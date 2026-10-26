package com.example.vaivan.data.repository

import android.util.Log
import com.example.vaivan.core.util.GeoUtil
import com.example.vaivan.core.util.PolylineUtil
import com.example.vaivan.data.local.dao.ParadaRotaDao
import com.example.vaivan.data.local.dao.RotaDao
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.models.RotaCompativel
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.remote.routes.PontoRota
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firestore = fonte da verdade
 * Room = cache local observado pela UI
 */
class RotaRepository(
    private val rotaDao: RotaDao,
    private val paradaRotaDao: ParadaRotaDao,
    private val routesClient: GoogleRoutesClient,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val assinaturaRepository: AssinaturaRepository = AssinaturaRepository(firestore)
) {

    private val collectionRotas = firestore.collection("rotas")
    private val collectionParadas = firestore.collection("paradas_rota")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenerRotas: ListenerRegistration? = null

    // =========================================================
    // OBSERVAÇÃO
    // =========================================================

    /** Observa as rotas de um motorista no Room. */
    fun observarRotasPorMotorista(motoristaId: String): Flow<List<RotaEntity>> {
        return rotaDao.getByMotorista(motoristaId)
    }

    /** Observa uma rota específica no Room. */
    fun observarRotaPorId(rotaId: String): Flow<RotaEntity?> {
        return rotaDao.getById(rotaId)
    }

    /** Observa as paradas de uma rota no Room. */
    fun observarParadas(rotaId: String): Flow<List<ParadaRotaEntity>> {
        return paradaRotaDao.getByRota(rotaId)
    }

    // =========================================================
    // CONSULTAS
    // =========================================================

    /** Consulta uma rota diretamente no Firebase. */
    suspend fun consultarRota(rotaId: String): RotaEntity? {
        val document = collectionRotas.document(rotaId).get().await()
        if (!document.exists()) return null

        return document.toObject(RotaEntity::class.java)?.copy(id = document.id)
    }

    /** Consulta as paradas de uma rota diretamente no Firebase. */
    suspend fun consultarParadas(rotaId: String): List<ParadaRotaEntity> {
        val snapshot = collectionParadas
            .whereEqualTo("rotaId", rotaId)
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            document.toObject(ParadaRotaEntity::class.java)?.copy(id = document.id)
        }
    }

    /** Consulta os passageiros de uma rota diretamente no Firebase. */
    suspend fun consultarParadasPorPassageiro(passageiroId: String): List<ParadaRotaEntity> {
        return collectionParadas
            .whereEqualTo("passageiroId", passageiroId)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(ParadaRotaEntity::class.java)?.copy(id = document.id)
            }
    }

    // =========================================================
    // SINCRONIZAÇÃO
    // =========================================================

    /** Inicia a sincronização das rotas de um motorista. */
    fun iniciarSincronizacaoDasRotasDoMotorista(motoristaId: String): ListenerRegistration {
        listenerRotas?.remove()

        listenerRotas = collectionRotas
            .whereEqualTo("motoristaId", motoristaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                scope.launch {
                    sincronizarSnapshot(snapshot)
                }
            }

        return listenerRotas!!
    }

    /** Aplica no Room somente as alterações recebidas do Firebase. */
    private suspend fun sincronizarSnapshot(snapshot: QuerySnapshot) {
        for (change in snapshot.documentChanges) {
            val document = change.document
            val rotaId = document.id

            when (change.type) {
                DocumentChange.Type.ADDED,
                DocumentChange.Type.MODIFIED -> {
                    val rota = document.toObject(RotaEntity::class.java).copy(
                        id = rotaId,
                        lastUpdated = System.currentTimeMillis()
                    )
                    rotaDao.upsert(rota)
                }
                DocumentChange.Type.REMOVED -> {
                    rotaDao.deleteById(rotaId)
                }
            }
        }
    }

    fun pararSincronizacao() {
        listenerRotas?.remove()
        listenerRotas = null
    }

    /** Faz uma sincronização pontual das rotas do motorista. */
    suspend fun sincronizarRotasDoMotoristaUmaVez(motoristaId: String) {
        val snapshot = collectionRotas
            .whereEqualTo("motoristaId", motoristaId)
            .get()
            .await()

        val rotas = snapshot.documents.mapNotNull { document ->
            document.toObject(RotaEntity::class.java)?.copy(
                id = document.id,
                lastUpdated = System.currentTimeMillis()
            )
        }

        rotaDao.upsertAll(rotas)
    }

    // =========================================================
    // CRIAR ROTA
    // =========================================================

    suspend fun criarRota(
        motoristaId: String,
        veiculoId: String?,
        nome: String,
        tipoTrajeto: String,
        origemNome: String,
        origemEndereco: String,
        origemLatitude: Double,
        origemLongitude: Double,
        destinoNome: String,
        destinoEndereco: String,
        destinoLatitude: Double,
        destinoLongitude: Double,
        turno: String,
        diasSemana: List<String>,
        capacidadeTotal: Int,
        escolaId: String = "",
        escolaNome: String = "",
        bairrosAtendidos: List<String> = emptyList()
    ): RotaEntity = withContext(Dispatchers.IO) {

        val documentReference = collectionRotas.document()
        val agora = System.currentTimeMillis()

        val origem = PontoRota(
            id = "origem",
            nome = origemNome,
            endereco = origemEndereco,
            latitude = origemLatitude,
            longitude = origemLongitude
        )

        val destino = PontoRota(
            id = "destino",
            nome = destinoNome,
            endereco = destinoEndereco,
            latitude = destinoLatitude,
            longitude = destinoLongitude
        )

        // Trajeto real: ponto A -> escola
        val resultado = try {
            routesClient.calcularMelhorRota(
                origem = origem,
                destino = destino,
                paradas = emptyList()
            )
        } catch (e: Exception) {
            Log.e("RotaRepository", "Erro ao calcular rota", e)
            throw IllegalStateException(
                "Não foi possível traçar a rota. Verifique a internet e os endereços.", e
            )
        }

        // Rota sem trajeto ficaria invisível para o radar, então não salvamos
        if (resultado.polylineEncoded.isNullOrBlank()) {
            throw IllegalStateException("O Google não retornou um trajeto para esses endereços.")
        }

        val rota = RotaEntity(
            id = documentReference.id,
            motoristaId = motoristaId,
            veiculoId = veiculoId,
            nome = nome,
            tipoTrajeto = tipoTrajeto,
            origemNome = origemNome,
            origemEndereco = origemEndereco,
            origemLatitude = origemLatitude,
            origemLongitude = origemLongitude,
            destinoNome = destinoNome,
            destinoEndereco = destinoEndereco,
            destinoLatitude = destinoLatitude,
            destinoLongitude = destinoLongitude,
            turno = turno,
            diasSemana = diasSemana.joinToString(","),
            escolaId = escolaId,
            escolaNome = escolaNome,
            bairrosAtendidos = bairrosAtendidos,
            capacidadeTotal = capacidadeTotal,
            vagasOcupadas = 0,
            distanciaMetros = resultado.distanciaMetros,
            duracaoSegundos = resultado.duracaoSegundos,
            duracaoSemTrafegoSegundos = resultado.duracaoSemTrafegoSegundos,
            polylineEncoded = resultado.polylineEncoded,
            status = "ATIVA",
            calculadoEm = agora,
            lastUpdated = agora
        )

        documentReference.set(rota).await()   // Firestore = fonte da verdade
        rotaDao.upsert(rota)                  // Room = cache local

        rota
    }
    // =========================================================
    // BUSCA TEMPORÁRIA DE ROTAS
    // =========================================================

    suspend fun buscarRotasDisponiveis(
        textoBusca: String,
        turno: String?
    ): List<RotaEntity> = withContext(Dispatchers.IO) {
        var query: Query = collectionRotas.whereEqualTo("status", "ATIVA")

        if (!turno.isNullOrBlank()) {
            query = query.whereEqualTo("turno", turno)
        }

        val snapshot = query.get().await()

        val comVaga = snapshot.documents
            .mapNotNull { document ->
                document.toObject(RotaEntity::class.java)?.copy(id = document.id)
            }
            .filter { rota ->
                rota.vagasOcupadas < rota.capacidadeTotal
            }

        // Só aparecem rotas de motoristas com assinatura em dia (ativa ou em carência).
        val motoristasEmDia = assinaturaRepository
            .motoristasOperacionais(comVaga.map { it.motoristaId })

        val rotas = comVaga.filter { it.motoristaId in motoristasEmDia }

        if (textoBusca.isBlank()) {
            rotas
        } else {
            rotas.filter { rota ->
                rota.destinoNome.contains(textoBusca, ignoreCase = true) ||
                        rota.escolaNome.contains(textoBusca, ignoreCase = true)
            }
        }
    }

    // =========================================================
    // BUSCA POR PROXIMIDADE (embarque + destino)
    // =========================================================

    private fun trajetoDaRota(rota: RotaEntity): List<Pair<Double, Double>> {
        val decodificado: List<Pair<Double, Double>> =
            if (rota.polylineEncoded.isBlank()) {
                emptyList()
            } else {
                runCatching {
                    PolylineUtil.decode(rota.polylineEncoded)
                        .map { it.latitude to it.longitude }
                }.getOrDefault(emptyList())
            }

        if (decodificado.size >= 2) return decodificado

        // Plano B: apenas o ponto de destino/escola
        return listOf(
            rota.destinoLatitude to rota.destinoLongitude
        )
    }

    suspend fun buscarRotasCompativeis(
        embarqueLatitude: Double,
        embarqueLongitude: Double,
        destinoLatitude: Double,
        destinoLongitude: Double,
        turno: String?,
        dias: Set<String>,
        raioEmbarqueMetros: Double = 1_000.0,
        raioDestinoMetros: Double = 1_000.0
    ): List<RotaCompativel> = withContext(Dispatchers.IO) {

        Log.d(
            "RADAR",
            "VERSAO NOVA: embarque=($embarqueLatitude,$embarqueLongitude) " +
                    "desembarque=($destinoLatitude,$destinoLongitude) turno=$turno dias=$dias"
        )

        var query: Query = collectionRotas.whereEqualTo("status", "ATIVA")
        if (!turno.isNullOrBlank()) {
            query = query.whereEqualTo("turno", turno)
        }

        val rotas = query.get().await().documents.mapNotNull { doc ->
            doc.toObject(RotaEntity::class.java)?.copy(id = doc.id)
        }

        Log.d("RADAR", "Rotas ATIVAS encontradas: ${rotas.size}")

        val rotasComVaga = rotas.filter { it.vagasOcupadas < it.capacidadeTotal }

        // Só aparecem rotas de motoristas com assinatura em dia (ativa ou em carência).
        val motoristasEmDia = assinaturaRepository
            .motoristasOperacionais(rotasComVaga.map { it.motoristaId })

        Log.d("RADAR", "Motoristas com assinatura em dia: ${motoristasEmDia.size}")

        rotasComVaga
            .filter { it.motoristaId in motoristasEmDia }
            .filter { rota ->
                val diasDaRota = rota.diasSemana.split(",").map { it.trim() }
                dias.isEmpty() || diasDaRota.containsAll(dias)
            }
            .mapNotNull { rota ->
                // Sem trajeto real (polyline) a rota não entra no radar
                val trajeto = trajetoDaRota(rota)
                if (trajeto.size < 2) {
                    Log.d("RADAR", "[${rota.nome}] FORA: sem polyline")
                    return@mapNotNull null
                }

                // Distância de cada ponto da busca até a escola da rota
                val embarqueAteEscola = GeoUtil.distanciaMetros(
                    embarqueLatitude, embarqueLongitude,
                    rota.destinoLatitude, rota.destinoLongitude
                )
                val desembarqueAteEscola = GeoUtil.distanciaMetros(
                    destinoLatitude, destinoLongitude,
                    rota.destinoLatitude, rota.destinoLongitude
                )

                // O ponto mais perto da escola é "a escola". O outro é "a casa".
                val buscaEhIda = desembarqueAteEscola <= embarqueAteEscola
                val sentidoBusca = if (buscaEhIda) "IDA" else "VOLTA"

                // A rota atende esse sentido?
                val tipoDaRota = rota.tipoTrajeto.ifBlank { "IDA_E_VOLTA" }
                val atendeSentido = tipoDaRota == "IDA_E_VOLTA" || tipoDaRota == sentidoBusca
                if (!atendeSentido) {
                    Log.d("RADAR", "[${rota.nome}] FORA: busca é de $sentidoBusca, mas a rota é $tipoDaRota")
                    return@mapNotNull null
                }

                // A escola da busca precisa estar perto da escola da rota
                val distEscola = if (buscaEhIda) desembarqueAteEscola else embarqueAteEscola
                if (distEscola > raioDestinoMetros) {
                    Log.d("RADAR", "[${rota.nome}] FORA: escola a ${distEscola.toInt()} m da escola da rota")
                    return@mapNotNull null
                }

                // A casa precisa estar perto do trajeto (o "radar" de 1 km)
                val casaLat = if (buscaEhIda) embarqueLatitude else destinoLatitude
                val casaLng = if (buscaEhIda) embarqueLongitude else destinoLongitude

                val distCasa = GeoUtil.distanciaAoTrajetoMetros(casaLat, casaLng, trajeto)
                if (distCasa > raioEmbarqueMetros) {
                    Log.d("RADAR", "[${rota.nome}] FORA: casa a ${distCasa.toInt()} m do trajeto")
                    return@mapNotNull null
                }

                Log.d(
                    "RADAR",
                    "[${rota.nome}] COMPATIVEL ($sentidoBusca, rota $tipoDaRota): " +
                            "casa a ${distCasa.toInt()} m, escola a ${distEscola.toInt()} m"
                )

                RotaCompativel(rota, distCasa.toInt(), distEscola.toInt())
            }
            .sortedBy { it.distanciaEmbarqueMetros + it.distanciaDestinoMetros }
    }

    // =========================================================
    // EXCLUIR ROTA
    // =========================================================

    /** Apaga a rota e as paradas dela no Firestore e no cache local (Room). */
    suspend fun excluirRota(rotaId: String) = withContext(Dispatchers.IO) {
        val paradas = collectionParadas
            .whereEqualTo("rotaId", rotaId)
            .get()
            .await()

        val batch = firestore.batch()
        paradas.documents.forEach { batch.delete(it.reference) }
        batch.delete(collectionRotas.document(rotaId))
        batch.commit().await()

        paradaRotaDao.deleteByRota(rotaId)
        rotaDao.deleteById(rotaId)
    }

    // =========================================================
    // OPERAÇÕES PARA USE CASE
    // =========================================================

    suspend fun buscarRotaNoFirestore(rotaId: String): RotaEntity {
        return consultarRota(rotaId) ?: throw IllegalStateException("Rota não encontrada.")
    }

    suspend fun buscarParadasNoFirestore(rotaId: String): List<ParadaRotaEntity> {
        return consultarParadas(rotaId)
    }

    suspend fun salvarRotaRecalculada(rota: RotaEntity) {
        collectionRotas.document(rota.id).set(rota).await()
        rotaDao.upsert(rota)
    }

    suspend fun salvarParadasRecalculadas(
        rotaId: String,
        paradas: List<ParadaRotaEntity>
    ) {
        paradas.forEach { parada ->
            collectionParadas.document(parada.id).set(parada).await()
        }

        paradaRotaDao.deleteByRota(rotaId)
        paradaRotaDao.upsertAll(paradas)
    }

    fun novoIdParada(): String {
        return collectionParadas.document().id
    }

    /**
     * Grava, numa única transação, o resultado de aceitar uma solicitação:
     * a rota recalculada, as paradas e a solicitação marcada como ACEITA.
     *
     * Nada é gravado se: a solicitação já foi respondida, a rota ficou sem vagas ou a rota
     * foi alterada depois que o cálculo começou (nesse caso o motorista tenta de novo).
     */
    suspend fun confirmarInclusaoNaRota(
        solicitacaoId: String,
        rotaAtualizada: RotaEntity,
        paradas: List<ParadaRotaEntity>,
        baseCalculadoEm: Long,
        respondidaEm: Long
    ) = withContext(Dispatchers.IO) {

        val rotaRef = collectionRotas.document(rotaAtualizada.id)
        val solicitacaoRef = firestore.collection("solicitacoes_inclusao").document(solicitacaoId)

        fun falha(mensagem: String) = FirebaseFirestoreException(
            mensagem,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION
        )

        firestore.runTransaction { transaction ->

            // Primeiro lemos tudo, depois escrevemos.
            val rotaServidor = transaction.get(rotaRef)
            val solicitacaoServidor = transaction.get(solicitacaoRef)

            if (!rotaServidor.exists()) {
                throw falha("Rota não encontrada.")
            }
            if (solicitacaoServidor.getString("status") != "PENDENTE") {
                throw falha("Esta solicitação já foi respondida.")
            }

            val vagasOcupadas = rotaServidor.getLong("vagasOcupadas") ?: 0L
            val capacidadeTotal = rotaServidor.getLong("capacidadeTotal") ?: 0L
            if (vagasOcupadas >= capacidadeTotal) {
                throw falha("Esta rota já está com todas as vagas ocupadas.")
            }

            val calculadoEmServidor = rotaServidor.getLong("calculadoEm") ?: 0L
            if (calculadoEmServidor != baseCalculadoEm) {
                throw falha("A rota foi alterada durante o cálculo. Toque em aceitar novamente.")
            }

            transaction.set(rotaRef, rotaAtualizada)
            paradas.forEach { parada ->
                transaction.set(collectionParadas.document(parada.id), parada)
            }
            transaction.update(
                solicitacaoRef,
                mapOf(
                    "status" to "ACEITA",
                    "respondidoEm" to respondidaEm,
                    "lastUpdated" to respondidaEm
                )
            )

            null
        }.await()

        // Cache local
        paradaRotaDao.deleteByRota(rotaAtualizada.id)
        paradaRotaDao.upsertAll(paradas)
        rotaDao.upsert(rotaAtualizada)
    }

    // =========================================================
    // MODELO AUXILIAR
    // =========================================================

    data class ParadaCandidata(
        val passageiroId: String,
        val nomePassageiro: String,
        val localId: String,
        val nomeLocal: String,
        val endereco: String,
        val latitude: Double,
        val longitude: Double
    )
}