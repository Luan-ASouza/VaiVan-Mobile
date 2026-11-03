package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.EventoViagemDao
import com.example.vaivan.data.local.dao.ParadaViagemDao
import com.example.vaivan.data.local.dao.ViagemDao
import com.example.vaivan.data.local.entities.EventoViagemEntity
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Porta de entrada para viagens (execuções de uma rota). Segue o mesmo padrão
 * do RotaRepository: Firestore = fonte da verdade, Room = cache local.
 *
 * Importante: isto NÃO recebe ticks de GPS. A posição ao vivo é distribuída
 * via SignalR (Etapa 3); aqui só entram eventos de negócio (início, chegada,
 * embarque, desembarque, fim) e, ocasionalmente, a última posição conhecida
 * (fallback de reconexão).
 */
class ViagemRepository(
    private val viagemDao: ViagemDao,
    private val paradaViagemDao: ParadaViagemDao,
    private val eventoViagemDao: EventoViagemDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val collectionViagens = firestore.collection("viagens")
    private val collectionParadas = firestore.collection("paradas_viagem")
    private val collectionEventos = firestore.collection("eventos_viagem")

    private var listenerViagem: ListenerRegistration? = null
    private var listenerParadas: ListenerRegistration? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val DIRECAO_IDA = "IDA"
        const val DIRECAO_VOLTA = "VOLTA"

        const val TIPO_EMBARQUE = "EMBARQUE"
        const val TIPO_DESEMBARQUE = "DESEMBARQUE"

        const val STATUS_EM_ANDAMENTO = "EM_ANDAMENTO"
        const val STATUS_PAUSADA = "PAUSADA"
        const val STATUS_FINALIZADA = "FINALIZADA"
        const val STATUS_CANCELADA = "CANCELADA"

        const val PARADA_AGUARDANDO = "AGUARDANDO"
        const val PARADA_EMBARCADO = "EMBARCADO"
        const val PARADA_NAO_EMBARCOU = "NAO_EMBARCOU"
        const val PARADA_AGUARDANDO_DESEMBARQUE = "AGUARDANDO_DESEMBARQUE"
        const val PARADA_DESEMBARCADO = "DESEMBARCADO"
    }

    // =========================================================
    // OBSERVAÇÃO (Room)
    // =========================================================

    fun observarViagem(viagemId: String): Flow<ViagemEntity?> = viagemDao.getById(viagemId)

    fun observarViagemAtivaDoMotorista(motoristaId: String): Flow<ViagemEntity?> =
        viagemDao.getViagemAtivaDoMotorista(motoristaId)

    /** Consulta direto no Firestore (não confia no cache) se o motorista já
     * tem uma viagem em andamento/pausada — usado antes de criar uma viagem
     * nova, pra não reiniciar o progresso se a tela de navegação for fechada
     * e reaberta (ex.: Activity recriada, app foi pra background e voltou). */
    suspend fun consultarViagemAtivaDoMotorista(motoristaId: String): ViagemEntity? {
        val snapshot = collectionViagens
            .whereEqualTo("motoristaId", motoristaId)
            .whereIn("status", listOf(STATUS_EM_ANDAMENTO, STATUS_PAUSADA))
            .limit(1)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.let { documento ->
            documento.toObject(ViagemEntity::class.java)?.copy(id = documento.id)
        }
    }

    fun observarParadas(viagemId: String): Flow<List<ParadaViagemEntity>> =
        paradaViagemDao.getByViagem(viagemId)

    fun observarEventos(viagemId: String): Flow<List<EventoViagemEntity>> =
        eventoViagemDao.getByViagem(viagemId)

    // =========================================================
    // SINCRONIZAÇÃO (uma viagem por vez — a que está ativa)
    // =========================================================

    fun iniciarSincronizacao(viagemId: String) {
        pararSincronizacao()

        listenerViagem = collectionViagens.document(viagemId)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                snapshot.toObject(ViagemEntity::class.java)?.copy(id = snapshot.id)?.let {
                    scope.launch { viagemDao.upsert(it) }
                }
            }

        listenerParadas = collectionParadas
            .whereEqualTo("viagemId", viagemId)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null || snapshot == null) return@addSnapshotListener

                val paradas = snapshot.documents.mapNotNull { documento ->
                    documento.toObject(ParadaViagemEntity::class.java)?.copy(id = documento.id)
                }

                scope.launch { paradaViagemDao.upsertAll(paradas) }
            }
    }

    fun pararSincronizacao() {
        listenerViagem?.remove()
        listenerViagem = null
        listenerParadas?.remove()
        listenerParadas = null
    }

    // =========================================================
    // INICIAR VIAGEM
    // =========================================================

    /**
     * Cria a viagem e o snapshot das paradas a partir da rota cadastrada.
     * Ordem dos waypoints:
     *  - IDA: paradas dos alunos (embarque, na ordem da rota) + escola (desembarque, todos).
     *  - VOLTA: escola (embarque, todos) + paradas dos alunos (desembarque, na ordem da rota).
     */
    suspend fun iniciarViagem(
        rota: RotaEntity,
        paradasRota: List<ParadaRotaEntity>,
        motoristaId: String,
        veiculoId: String?,
        direcao: String,
        responsavelPorPassageiro: Map<String, String>
    ): ViagemEntity {

        val agora = System.currentTimeMillis()
        val viagemRef = collectionViagens.document()

        val tipoParadasAluno = if (direcao == DIRECAO_IDA) TIPO_EMBARQUE else TIPO_DESEMBARQUE
        val tipoParadaEscola = if (direcao == DIRECAO_IDA) TIPO_DESEMBARQUE else TIPO_EMBARQUE
        val statusInicialAluno =
            if (tipoParadasAluno == TIPO_EMBARQUE) PARADA_AGUARDANDO else PARADA_AGUARDANDO_DESEMBARQUE
        val statusInicialEscola =
            if (tipoParadaEscola == TIPO_EMBARQUE) PARADA_AGUARDANDO else PARADA_AGUARDANDO_DESEMBARQUE

        val paradasOrdenadas = paradasRota.sortedBy { it.ordem }

        val paradasAluno = paradasOrdenadas.mapIndexed { index, paradaRota ->
            ParadaViagemEntity(
                id = collectionParadas.document().id,
                viagemId = viagemRef.id,
                passageiroId = paradaRota.passageiroId,
                nomePassageiro = paradaRota.nomePassageiro,
                responsavelId = responsavelPorPassageiro[paradaRota.passageiroId].orEmpty(),
                localId = paradaRota.localId,
                nomeLocal = paradaRota.nomeLocal,
                endereco = paradaRota.endereco,
                latitude = paradaRota.latitude,
                longitude = paradaRota.longitude,
                tipo = tipoParadasAluno,
                ordem = index,
                status = statusInicialAluno,
                horarioPrevistoMinutos = paradaRota.horarioEstimadoMinutos,
                horarioEstimadoMinutos = paradaRota.horarioEstimadoMinutos,
                lastUpdated = agora
            )
        }

        val ordemEscola = paradasAluno.size
        val duracaoTotalMinutos = (rota.duracaoSegundos / 60)

        val paradasEscola = paradasOrdenadas.map { paradaRota ->
            ParadaViagemEntity(
                id = collectionParadas.document().id,
                viagemId = viagemRef.id,
                passageiroId = paradaRota.passageiroId,
                nomePassageiro = paradaRota.nomePassageiro,
                responsavelId = responsavelPorPassageiro[paradaRota.passageiroId].orEmpty(),
                localId = "escola",
                nomeLocal = rota.escolaNome.ifBlank { rota.destinoNome },
                endereco = rota.destinoEndereco,
                latitude = rota.destinoLatitude,
                longitude = rota.destinoLongitude,
                tipo = tipoParadaEscola,
                ordem = ordemEscola,
                status = statusInicialEscola,
                horarioPrevistoMinutos = duracaoTotalMinutos,
                horarioEstimadoMinutos = duracaoTotalMinutos,
                lastUpdated = agora
            )
        }

        // A sequência de waypoints da navegação segue a direção escolhida.
        val todasParadas = if (direcao == DIRECAO_IDA) {
            paradasAluno + paradasEscola
        } else {
            paradasEscola.map { it.copy(ordem = 0) } + paradasAluno.mapIndexed { index, parada ->
                parada.copy(ordem = index + 1)
            }
        }

        val viagem = ViagemEntity(
            id = viagemRef.id,
            rotaId = rota.id,
            motoristaId = motoristaId,
            veiculoId = veiculoId,
            direcao = direcao,
            responsaveisAutorizados = responsavelPorPassageiro.values.distinct().filter { it.isNotBlank() },
            status = STATUS_EM_ANDAMENTO,
            paradaAtualId = todasParadas.minByOrNull { it.ordem }?.id,
            iniciadaEm = agora,
            lastUpdated = agora
        )

        val batch = firestore.batch()
        batch.set(viagemRef, viagem)
        todasParadas.forEach { parada ->
            batch.set(collectionParadas.document(parada.id), parada)
        }
        batch.commit().await()

        viagemDao.upsert(viagem)
        paradaViagemDao.upsertAll(todasParadas)

        registrarEvento(viagem.id, null, "INICIO")

        return viagem
    }

    // =========================================================
    // EMBARQUE / DESEMBARQUE
    // =========================================================

    suspend fun confirmarEmbarque(parada: ParadaViagemEntity) {
        atualizarStatusParada(parada, PARADA_EMBARCADO, "EMBARQUE")
    }

    suspend fun marcarNaoEmbarcou(parada: ParadaViagemEntity, motivo: String?) {
        val agora = System.currentTimeMillis()
        val atualizada = parada.copy(
            status = PARADA_NAO_EMBARCOU,
            motivoAusencia = motivo,
            horarioRealMillis = agora,
            lastUpdated = agora
        )

        collectionParadas.document(atualizada.id).set(atualizada).await()
        paradaViagemDao.upsert(atualizada)

        registrarEvento(atualizada.viagemId, atualizada.id, "NAO_EMBARCOU")
    }

    suspend fun confirmarDesembarque(parada: ParadaViagemEntity) {
        atualizarStatusParada(parada, PARADA_DESEMBARCADO, "DESEMBARQUE")
    }

    /** Confirma de uma vez o desembarque de todo um grupo (ex.: chegada ao
     * ponto final, onde não faz sentido confirmar aluno por aluno). */
    suspend fun confirmarDesembarqueColetivo(paradas: List<ParadaViagemEntity>) {
        if (paradas.isEmpty()) return

        val agora = System.currentTimeMillis()
        val atualizadas = paradas.map {
            it.copy(status = PARADA_DESEMBARCADO, horarioRealMillis = agora, lastUpdated = agora)
        }

        val batch = firestore.batch()
        atualizadas.forEach { batch.set(collectionParadas.document(it.id), it) }
        batch.commit().await()

        paradaViagemDao.upsertAll(atualizadas)
        atualizadas.forEach { registrarEvento(it.viagemId, it.id, "DESEMBARQUE") }
    }

    private suspend fun atualizarStatusParada(
        parada: ParadaViagemEntity,
        novoStatus: String,
        tipoEvento: String
    ) {
        val agora = System.currentTimeMillis()
        val atualizada = parada.copy(
            status = novoStatus,
            horarioRealMillis = agora,
            lastUpdated = agora
        )

        collectionParadas.document(atualizada.id).set(atualizada).await()
        paradaViagemDao.upsert(atualizada)

        registrarEvento(atualizada.viagemId, atualizada.id, tipoEvento)
    }

    suspend fun avancarParaProximaParada(viagemId: String, proximaParadaId: String?) {
        val agora = System.currentTimeMillis()
        collectionViagens.document(viagemId)
            .update(mapOf("paradaAtualId" to proximaParadaId, "lastUpdated" to agora))
            .await()
    }

    suspend fun pausarViagem(viagemId: String) {
        val agora = System.currentTimeMillis()
        collectionViagens.document(viagemId)
            .update(mapOf("status" to STATUS_PAUSADA, "pausadaEm" to agora, "lastUpdated" to agora))
            .await()
    }

    suspend fun retomarViagem(viagemId: String) {
        val agora = System.currentTimeMillis()
        collectionViagens.document(viagemId)
            .update(mapOf("status" to STATUS_EM_ANDAMENTO, "pausadaEm" to null, "lastUpdated" to agora))
            .await()
    }

    suspend fun finalizarViagem(viagemId: String) {
        val agora = System.currentTimeMillis()
        collectionViagens.document(viagemId)
            .update(
                mapOf(
                    "status" to STATUS_FINALIZADA,
                    "finalizadaEm" to agora,
                    "lastUpdated" to agora
                )
            )
            .await()

        registrarEvento(viagemId, null, "FINALIZADA")
    }

    // =========================================================
    // EVENTOS
    // =========================================================

    private suspend fun registrarEvento(viagemId: String, paradaViagemId: String?, tipo: String) {
        val agora = System.currentTimeMillis()
        val evento = EventoViagemEntity(
            id = collectionEventos.document().id,
            viagemId = viagemId,
            paradaViagemId = paradaViagemId,
            tipo = tipo,
            ocorridoEm = agora,
            lastUpdated = agora
        )

        collectionEventos.document(evento.id).set(evento).await()
        eventoViagemDao.upsert(evento)
    }
}
