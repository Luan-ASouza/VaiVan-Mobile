package com.example.vaivan.data.repository

import com.example.vaivan.core.util.DataUtil
import android.util.Log
import com.example.vaivan.core.util.GeoUtil
import com.example.vaivan.core.util.PolylineUtil
import com.example.vaivan.data.local.dao.ParadaRotaDao
import com.example.vaivan.data.local.dao.RotaDao
import com.example.vaivan.data.local.dao.SolicitacaoInclusaoDao
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.comTabela
import com.example.vaivan.domain.usecase.preco.TabelaPreco
import com.example.vaivan.data.models.RotaCompativel
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.remote.routes.PontoRota
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
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
    private val assinaturaRepository: AssinaturaRepository = AssinaturaRepository(firestore),
    // Opcional: só quem aciona excluirRota precisa passar, pra limpeza em
    // cascata (apagar solicitação pendente) também refletir no Room na hora.
    private val solicitacaoInclusaoDao: SolicitacaoInclusaoDao? = null
) {

    companion object {
        /** Gravado pelo cancelamento do responsável (CancelarSolicitacaoUseCase,
         * firestore.rules): "um aluno saiu, o trajeto precisa ser recalculado".
         * Só existe no Firestore (não está na RotaEntity) — por isso as
         * gravações da rota inteira usam merge, pra não apagá-lo sem querer. */
        const val CAMPO_SAIDA_PENDENTE = "ultimaSolicitacaoCancelada"
    }

    private val collectionRotas = firestore.collection("rotas")
    private val collectionParadas = firestore.collection("paradas_rota")
    private val collectionSolicitacoes = firestore.collection("solicitacoes_inclusao")

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

    /** Consulta diretamente no Firebase todas as rotas de um motorista
     * (usado pela exclusão de conta — não pode confiar no cache do Room). */
    suspend fun consultarRotasDoMotorista(motoristaId: String): List<RotaEntity> {
        val snapshot = collectionRotas.whereEqualTo("motoristaId", motoristaId).get().await()
        return snapshot.documents.mapNotNull { documento ->
            documento.toObject(RotaEntity::class.java)?.copy(id = documento.id)
        }
    }

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

    /** Mesma ideia de [sincronizarRotasDoMotoristaUmaVez], pras paradas de uma
     * rota específica — usado pelo pull-to-refresh da tela de detalhe, que
     * não tem (e não precisa manter) um listener próprio o tempo todo.
     * Espelha o servidor: paradas removidas lá (aluno saiu) somem do Room. */
    suspend fun sincronizarParadasUmaVez(rotaId: String) {
        val paradas = consultarParadas(rotaId)
        paradaRotaDao.deleteByRota(rotaId)
        paradaRotaDao.upsertAll(paradas)
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
        bairrosAtendidos: List<String> = emptyList(),
        tabelaPreco: TabelaPreco? = null,
        diaVencimento: Int = 10
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
            diasSemana = DataUtil.ordenarDiasSemana(diasSemana).joinToString(","),
            escolaId = escolaId,
            escolaNome = escolaNome,
            bairrosAtendidos = bairrosAtendidos,
            capacidadeTotal = capacidadeTotal,
            vagasOcupadas = 0,
            diaVencimento = diaVencimento,
            distanciaMetros = resultado.distanciaMetros,
            duracaoSegundos = resultado.duracaoSegundos,
            duracaoSemTrafegoSegundos = resultado.duracaoSemTrafegoSegundos,
            polylineEncoded = resultado.polylineEncoded,
            status = "ATIVA",
            calculadoEm = agora,
            lastUpdated = agora
        ).comTabela(tabelaPreco)

        documentReference.set(rota).await()   // Firestore = fonte da verdade
        rotaDao.upsert(rota)                  // Room = cache local

        rota
    }

    /** Atualiza uma rota existente. Só recalcula o trajeto via
     * GoogleRoutesClient se origem/destino realmente mudaram em relação ao
     * que já estava salvo — senão é só atualização de metadados (nome,
     * turno, dias, capacidade etc.), sem gastar chamada de API nem arriscar
     * falhar por um problema de rede numa edição que não mudou o trajeto. */
    suspend fun atualizarRota(
        rotaId: String,
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
        bairrosAtendidos: List<String> = emptyList(),
        // null = mantém o que a rota já tem
        tabelaPreco: TabelaPreco? = null,
        diaVencimento: Int? = null
    ): RotaEntity = withContext(Dispatchers.IO) {

        val atual = consultarRota(rotaId)
            ?: throw IllegalStateException("Rota não encontrada.")

        val agora = System.currentTimeMillis()

        val trajetoMudou =
            atual.origemLatitude != origemLatitude ||
                    atual.origemLongitude != origemLongitude ||
                    atual.destinoLatitude != destinoLatitude ||
                    atual.destinoLongitude != destinoLongitude

        val rotaAtualizada = if (trajetoMudou) {

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

            val resultado = try {
                routesClient.calcularMelhorRota(
                    origem = origem,
                    destino = destino,
                    paradas = emptyList()
                )
            } catch (e: Exception) {
                Log.e("RotaRepository", "Erro ao recalcular rota", e)
                throw IllegalStateException(
                    "Não foi possível traçar a rota. Verifique a internet e os endereços.", e
                )
            }

            if (resultado.polylineEncoded.isNullOrBlank()) {
                throw IllegalStateException("O Google não retornou um trajeto para esses endereços.")
            }

            atual.copy(
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
                diasSemana = DataUtil.ordenarDiasSemana(diasSemana).joinToString(","),
                escolaId = escolaId,
                escolaNome = escolaNome,
                bairrosAtendidos = bairrosAtendidos,
                capacidadeTotal = capacidadeTotal,
                diaVencimento = diaVencimento ?: atual.diaVencimento,
                distanciaMetros = resultado.distanciaMetros,
                duracaoSegundos = resultado.duracaoSegundos,
                duracaoSemTrafegoSegundos = resultado.duracaoSemTrafegoSegundos,
                polylineEncoded = resultado.polylineEncoded,
                calculadoEm = agora,
                lastUpdated = agora
            )

        } else {

            atual.copy(
                nome = nome,
                tipoTrajeto = tipoTrajeto,
                turno = turno,
                diasSemana = DataUtil.ordenarDiasSemana(diasSemana).joinToString(","),
                escolaId = escolaId,
                escolaNome = escolaNome,
                bairrosAtendidos = bairrosAtendidos,
                capacidadeTotal = capacidadeTotal,
                diaVencimento = diaVencimento ?: atual.diaVencimento,
                lastUpdated = agora
            )
        }

        val rotaFinal = rotaAtualizada.comTabela(tabelaPreco)

        // merge: mantém o aviso de trajeto desatualizado (CAMPO_SAIDA_PENDENTE),
        // que não faz parte da entity — uma edição só de nome/turno não recalcula.
        collectionRotas.document(rotaId).set(rotaFinal, SetOptions.merge()).await()
        rotaDao.upsert(rotaFinal)

        rotaFinal
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
    /** Exclui a rota e cascateia: apaga as paradas_rota dela e as solicitações
     * de inclusão ainda pendentes. Nunca toca em ViagemEntity/histórico. */
    suspend fun excluirRota(rotaId: String) = withContext(Dispatchers.IO) {
        val paradas = collectionParadas
            .whereEqualTo("rotaId", rotaId)
            .get()
            .await()

        val solicitacoesPendentes = collectionSolicitacoes
            .whereEqualTo("rotaId", rotaId)
            .whereEqualTo("status", "PENDENTE")
            .get()
            .await()

        val batch = firestore.batch()
        paradas.documents.forEach { batch.delete(it.reference) }
        solicitacoesPendentes.documents.forEach { batch.delete(it.reference) }
        batch.delete(collectionRotas.document(rotaId))
        batch.commit().await()

        paradaRotaDao.deleteByRota(rotaId)
        solicitacaoInclusaoDao?.deletePendentesPorRota(rotaId)
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

    /**
     * Grava a ordem da volta calculada fora do aceite (rota antiga, ou que
     * mudou desde o último cálculo). Só grava se a rota não tiver sido
     * recalculada nesse meio-tempo (calculadoEm igual ao lido) — senão um
     * aceite concorrente já gravou uma ordem mais nova.
     */
    suspend fun salvarOrdemVolta(
        rota: RotaEntity,
        paradas: List<ParadaRotaEntity>
    ) = withContext(Dispatchers.IO) {
        val rotaRef = collectionRotas.document(rota.id)

        val gravou = firestore.runTransaction { transaction ->
            val calculadoEmServidor = transaction.get(rotaRef).getLong("calculadoEm") ?: 0L
            if (calculadoEmServidor != rota.calculadoEm) return@runTransaction false

            transaction.update(rotaRef, "voltaCalculadaEm", rota.calculadoEm)
            paradas.forEach { parada ->
                transaction.update(
                    collectionParadas.document(parada.id),
                    mapOf(
                        "ordemVolta" to parada.ordemVolta,
                        "horarioEstimadoVoltaMinutos" to parada.horarioEstimadoVoltaMinutos
                    )
                )
            }
            true
        }.await()

        if (gravou) {
            paradaRotaDao.upsertAll(paradas)
            rotaDao.upsert(rota.copy(voltaCalculadaEm = rota.calculadoEm))
        }
    }

    // =========================================================
    // TRAJETO DESATUALIZADO (aluno saiu da rota)
    // =========================================================

    /**
     * Rotas do motorista em que um aluno saiu depois do último cálculo do
     * trajeto. O cancelamento é feito pelo responsável, que pelas regras só
     * pode gravar `vagasOcupadas` e [CAMPO_SAIDA_PENDENTE] na rota — então é
     * o app do motorista que recalcula, e esse campo é o aviso de que precisa.
     */
    suspend fun consultarRotasComTrajetoDesatualizado(motoristaId: String): List<String> =
        withContext(Dispatchers.IO) {
            collectionRotas
                .whereEqualTo("motoristaId", motoristaId)
                .get()
                .await()
                .documents
                .filter { !it.getString(CAMPO_SAIDA_PENDENTE).isNullOrBlank() }
                .map { it.id }
        }

    /**
     * Grava o trajeto recalculado só com os alunos atuais e apaga o aviso
     * [CAMPO_SAIDA_PENDENTE]. Não grava (devolve false) se, desde a leitura,
     * a rota foi recalculada por outro caminho (`calculadoEm` mudou) ou mais
     * um aluno saiu (uma das paradas sumiu) — nesse caso o aviso continua lá
     * e o cálculo é refeito na próxima vez.
     *
     * Só os campos do trajeto são tocados: `vagasOcupadas` pode ter mudado
     * nesse meio-tempo e não é responsabilidade deste cálculo.
     */
    suspend fun salvarTrajetoAtualizado(
        rota: RotaEntity,
        paradas: List<ParadaRotaEntity>,
        baseCalculadoEm: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val rotaRef = collectionRotas.document(rota.id)

        val vagasNoServidor = firestore.runTransaction { transaction ->
            val rotaServidor = transaction.get(rotaRef)
            if ((rotaServidor.getLong("calculadoEm") ?: 0L) != baseCalculadoEm) return@runTransaction null

            val paradasRefs = paradas.map { collectionParadas.document(it.id) }
            if (paradasRefs.any { !transaction.get(it).exists() }) return@runTransaction null

            transaction.update(
                rotaRef,
                mapOf(
                    "distanciaMetros" to rota.distanciaMetros,
                    "duracaoSegundos" to rota.duracaoSegundos,
                    "duracaoSemTrafegoSegundos" to rota.duracaoSemTrafegoSegundos,
                    "polylineEncoded" to rota.polylineEncoded,
                    "calculadoEm" to rota.calculadoEm,
                    "voltaCalculadaEm" to rota.voltaCalculadaEm,
                    "lastUpdated" to rota.lastUpdated,
                    CAMPO_SAIDA_PENDENTE to FieldValue.delete()
                )
            )
            paradas.zip(paradasRefs).forEach { (parada, ref) -> transaction.set(ref, parada) }

            rotaServidor.getLong("vagasOcupadas") ?: rota.vagasOcupadas.toLong()
        }.await() ?: return@withContext false

        rotaDao.upsert(rota.copy(vagasOcupadas = vagasNoServidor.toInt()))
        paradaRotaDao.deleteByRota(rota.id)
        paradaRotaDao.upsertAll(paradas)
        true
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
        respondidaEm: Long,
        // true = a vaga já foi reservada no aceite (ver reservarVaga): não conta de novo
        vagaJaReservada: Boolean = false
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
            val statusEsperado =
                if (vagaJaReservada) SolicitacaoInclusaoRepository.STATUS_AGUARDANDO_PAGAMENTO
                else SolicitacaoInclusaoRepository.STATUS_PENDENTE
            when (solicitacaoServidor.getString("status")) {
                statusEsperado -> Unit
                SolicitacaoInclusaoRepository.STATUS_CANCELADA -> throw falha("O responsável cancelou este pedido.")
                else -> throw falha("Esta solicitação já foi respondida.")
            }

            val vagasOcupadas = rotaServidor.getLong("vagasOcupadas") ?: 0L
            val capacidadeTotal = rotaServidor.getLong("capacidadeTotal") ?: 0L
            if (!vagaJaReservada && vagasOcupadas >= capacidadeTotal) {
                throw falha("Esta rota já está com todas as vagas ocupadas.")
            }

            val calculadoEmServidor = rotaServidor.getLong("calculadoEm") ?: 0L
            if (calculadoEmServidor != baseCalculadoEm) {
                throw falha("A rota foi alterada durante o cálculo. Toque em aceitar novamente.")
            }

            // Vagas a partir do número do SERVIDOR: se um responsável cancelou e
            // liberou uma vaga há pouco, a cópia local pode estar desatualizada.
            val vagasFinal = vagasOcupadas.toInt() + if (vagaJaReservada) 0 else 1
            transaction.set(rotaRef, rotaAtualizada.copy(vagasOcupadas = vagasFinal))
            paradas.forEach { parada ->
                transaction.set(collectionParadas.document(parada.id), parada)
            }
            transaction.update(
                solicitacaoRef,
                if (vagaJaReservada) {
                    // Ativação depois do 1º pagamento: mantém a data do aceite
                    mapOf(
                        "status" to SolicitacaoInclusaoRepository.STATUS_ACEITA,
                        "ativadaEm" to respondidaEm,
                        "lastUpdated" to respondidaEm
                    )
                } else {
                    mapOf(
                        "status" to SolicitacaoInclusaoRepository.STATUS_ACEITA,
                        "respondidoEm" to respondidaEm,
                        "lastUpdated" to respondidaEm
                    )
                }
            )

            null
        }.await()

        // Cache local
        paradaRotaDao.deleteByRota(rotaAtualizada.id)
        paradaRotaDao.upsertAll(paradas)
        rotaDao.upsert(rotaAtualizada)
    }

    /**
     * ACEITE DO MOTORISTA (1ª etapa): reserva a vaga, sem colocar o aluno no trajeto.
     * Numa transação: confere que o pedido ainda está PENDENTE e que há vaga, soma 1
     * em vagasOcupadas e marca o pedido como AGUARDANDO_PAGAMENTO. O aluno entra no
     * trajeto só depois que o responsável paga a 1ª mensalidade (ConfirmarReservasPagasUseCase).
     */
    suspend fun reservarVaga(
        solicitacaoId: String,
        rotaId: String,
        respondidaEm: Long
    ) = withContext(Dispatchers.IO) {

        val rotaRef = collectionRotas.document(rotaId)
        val solicitacaoRef = firestore.collection("solicitacoes_inclusao").document(solicitacaoId)

        fun falha(mensagem: String) = FirebaseFirestoreException(
            mensagem,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION
        )

        firestore.runTransaction { transaction ->
            val rotaServidor = transaction.get(rotaRef)
            val solicitacaoServidor = transaction.get(solicitacaoRef)

            if (!rotaServidor.exists()) throw falha("Rota não encontrada.")

            when (solicitacaoServidor.getString("status")) {
                SolicitacaoInclusaoRepository.STATUS_PENDENTE -> Unit
                SolicitacaoInclusaoRepository.STATUS_CANCELADA -> throw falha("O responsável cancelou este pedido.")
                else -> throw falha("Esta solicitação já foi respondida.")
            }

            val vagasOcupadas = rotaServidor.getLong("vagasOcupadas") ?: 0L
            val capacidadeTotal = rotaServidor.getLong("capacidadeTotal") ?: 0L
            if (vagasOcupadas >= capacidadeTotal) {
                throw falha("Esta rota já está com todas as vagas ocupadas.")
            }

            transaction.update(rotaRef, "vagasOcupadas", vagasOcupadas + 1)
            transaction.update(
                solicitacaoRef,
                mapOf(
                    "status" to SolicitacaoInclusaoRepository.STATUS_AGUARDANDO_PAGAMENTO,
                    "respondidoEm" to respondidaEm,
                    "lastUpdated" to respondidaEm
                )
            )
            null
        }.await()
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