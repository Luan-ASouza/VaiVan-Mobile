package com.example.vaivan.data.repository

import android.content.Context
import com.example.vaivan.data.local.dao.ParadaRotaDao
import com.example.vaivan.data.local.dao.RotaDao
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.remote.routes.PontoRota
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Uma parada (aluno + local de embarque) já confirmada numa rota. */
data class ParadaCandidata(
    val passageiroId: String,
    val nomePassageiro: String,
    val localId: String,
    val nomeLocal: String,
    val endereco: String,
    val latitude: Double,
    val longitude: Double
)

class RotaRepository(
    private val context: Context,
    private val rotaDao: RotaDao,
    private val paradaRotaDao: ParadaRotaDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val routesClient = GoogleRoutesClient(context)
    private val collectionRotas = firestore.collection("rotas")
    private val collectionParadas = firestore.collection("paradas_rota")

    fun observarPorMotorista(motoristaId: String): Flow<List<RotaEntity>> =
        rotaDao.getByMotorista(motoristaId)

    fun observarRota(rotaId: String): Flow<RotaEntity?> =
        rotaDao.getById(rotaId)

    fun observarParadas(rotaId: String): Flow<List<ParadaRotaEntity>> =
        paradaRotaDao.getByRota(rotaId)

    /**
     * Cria uma nova rota oferecida pelo motorista (ex: "La Salle Carmo - Manhã").
     * Já calcula uma estimativa de trajeto direto (origem -> destino, sem
     * paradas) para a rota aparecer com dados na busca antes do primeiro
     * aluno ser aceito.
     */
    suspend fun criarRota(
        motoristaId: String,
        veiculoId: String?,
        nome: String,
        origemNome: String,
        origemLatitude: Double,
        origemLongitude: Double,
        destinoNome: String,
        destinoEndereco: String,
        destinoLatitude: Double,
        destinoLongitude: Double,
        turno: String,
        diasSemana: List<String>,
        capacidadeTotal: Int
    ): RotaEntity = withContext(Dispatchers.IO) {

        val docRef = collectionRotas.document()
        val agora = System.currentTimeMillis()

        val origem = PontoRota("origem", origemNome, "", origemLatitude, origemLongitude)
        val destino = PontoRota("destino", destinoNome, destinoEndereco, destinoLatitude, destinoLongitude)

        val resultadoInicial = try {
            routesClient.calcularMelhorRota(origem, destino, emptyList())
        } catch (e: Exception) {
            null // Se a estimativa inicial falhar, a rota ainda é criada; o cálculo se refaz no 1º aluno aceito.
        }

        val rota = RotaEntity(
            id = docRef.id,
            motoristaId = motoristaId,
            veiculoId = veiculoId,
            nome = nome,
            origemNome = origemNome,
            origemLatitude = origemLatitude,
            origemLongitude = origemLongitude,
            destinoNome = destinoNome,
            destinoEndereco = destinoEndereco,
            destinoLatitude = destinoLatitude,
            destinoLongitude = destinoLongitude,
            turno = turno,
            diasSemana = diasSemana.joinToString(","),
            capacidadeTotal = capacidadeTotal,
            vagasOcupadas = 0,
            distanciaMetros = resultadoInicial?.distanciaMetros ?: 0,
            duracaoSegundos = resultadoInicial?.duracaoSegundos ?: 0,
            duracaoSemTrafegoSegundos = resultadoInicial?.duracaoSemTrafegoSegundos ?: 0,
            polylineEncoded = resultadoInicial?.polylineEncoded ?: "",
            status = "ATIVA",
            calculadoEm = agora,
            lastUpdated = agora
        )

        docRef.set(rota).await()
        rotaDao.upsert(rota)
        rota
    }

    /**
     * Busca rotas ativas e com vaga, filtrando pelo nome do destino (escola)
     * e, opcionalmente, pelo turno. Usada na tela de pesquisa do responsável.
     */
    suspend fun buscarRotasDisponiveis(textoBusca: String, turno: String?): List<RotaEntity> =
        withContext(Dispatchers.IO) {

            var query: Query = collectionRotas.whereEqualTo("status", "ATIVA")
            if (!turno.isNullOrBlank()) {
                query = query.whereEqualTo("turno", turno)
            }

            val snapshot = query.get().await()
            val todas = snapshot.documents.mapNotNull { it.toObject(RotaEntity::class.java) }
            val comVaga = todas.filter { it.vagasOcupadas < it.capacidadeTotal }

            if (textoBusca.isBlank()) {
                comVaga
            } else {
                comVaga.filter { it.destinoNome.contains(textoBusca, ignoreCase = true) }
            }
        }

    /**
     * Chamado quando o motorista ACEITA uma solicitação: adiciona a nova
     * parada às já existentes, recalcula a rota inteira com otimização de
     * ordem e trânsito em tempo real, e atualiza as vagas ocupadas.
     */
    suspend fun recalcularComNovaParada(
        rotaId: String,
        novaParada: ParadaCandidata
    ): RotaEntity = withContext(Dispatchers.IO) {

        val rotaAtualSnapshot = collectionRotas.document(rotaId).get().await()
        if (!rotaAtualSnapshot.exists()) {
            throw IllegalStateException("Rota não encontrada.")
        }

        val rotaAtual = rotaAtualSnapshot.toObject(RotaEntity::class.java)
            ?: throw IllegalStateException("Não foi possível ler a rota.")

        if (rotaAtual.vagasOcupadas >= rotaAtual.capacidadeTotal) {
            throw IllegalStateException("Esta rota já está com todas as vagas ocupadas.")
        }

        val paradasExistentesSnapshot = collectionParadas
            .whereEqualTo("rotaId", rotaId)
            .get()
            .await()

        val paradasExistentes = paradasExistentesSnapshot.documents.mapNotNull { doc ->
            doc.toObject(ParadaRotaEntity::class.java)
        }

        val todasParadas = paradasExistentes.map { existente ->
            ParadaCandidata(
                passageiroId = existente.passageiroId,
                nomePassageiro = existente.nomePassageiro,
                localId = existente.localId,
                nomeLocal = existente.nomeLocal,
                endereco = existente.endereco,
                latitude = existente.latitude,
                longitude = existente.longitude
            )
        } + novaParada

        val origem = PontoRota("origem", rotaAtual.origemNome, "", rotaAtual.origemLatitude, rotaAtual.origemLongitude)
        val destino = PontoRota(
            "destino", rotaAtual.destinoNome, rotaAtual.destinoEndereco,
            rotaAtual.destinoLatitude, rotaAtual.destinoLongitude
        )

        val pontosIntermediarios = todasParadas.map { parada ->
            PontoRota(parada.passageiroId, parada.nomePassageiro, parada.endereco, parada.latitude, parada.longitude)
        }

        val resultado = routesClient.calcularMelhorRota(origem, destino, pontosIntermediarios)

        val paradasOrdenadas = if (resultado.ordemOtimizada.isNotEmpty()) {
            resultado.ordemOtimizada.map { indiceOriginal -> todasParadas[indiceOriginal] }
        } else {
            todasParadas
        }

        val agora = System.currentTimeMillis()

        val rotaAtualizada = rotaAtual.copy(
            vagasOcupadas = rotaAtual.vagasOcupadas + 1,
            distanciaMetros = resultado.distanciaMetros,
            duracaoSegundos = resultado.duracaoSegundos,
            duracaoSemTrafegoSegundos = resultado.duracaoSemTrafegoSegundos,
            polylineEncoded = resultado.polylineEncoded,
            calculadoEm = agora,
            lastUpdated = agora
        )

        var acumuladoSegundos = 0
        val paradasEntities = paradasOrdenadas.mapIndexed { index, parada ->
            val perna = resultado.pernas.getOrNull(index)
            val duracaoTrecho = perna?.duracaoSegundos ?: 0
            val distanciaTrecho = perna?.distanciaMetros ?: 0
            acumuladoSegundos += duracaoTrecho

            val idExistente = paradasExistentes
                .firstOrNull { it.passageiroId == parada.passageiroId }
                ?.id

            ParadaRotaEntity(
                id = idExistente ?: collectionParadas.document().id,
                rotaId = rotaId,
                passageiroId = parada.passageiroId,
                localId = parada.localId,
                nomePassageiro = parada.nomePassageiro,
                nomeLocal = parada.nomeLocal,
                endereco = parada.endereco,
                latitude = parada.latitude,
                longitude = parada.longitude,
                ordem = index,
                distanciaTrechoMetros = distanciaTrecho,
                duracaoTrechoSegundos = duracaoTrecho,
                horarioEstimadoMinutos = acumuladoSegundos / 60,
                lastUpdated = agora
            )
        }

        collectionRotas.document(rotaId).set(rotaAtualizada).await()
        paradasEntities.forEach { parada ->
            collectionParadas.document(parada.id).set(parada).await()
        }

        rotaDao.upsert(rotaAtualizada)
        paradaRotaDao.deleteByRota(rotaId)
        paradaRotaDao.upsertAll(paradasEntities)

        rotaAtualizada
    }
}