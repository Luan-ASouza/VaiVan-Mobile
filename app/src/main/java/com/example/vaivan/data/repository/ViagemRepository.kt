package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.EventoViagemDao
import com.example.vaivan.data.local.dao.ParadaViagemDao
import com.example.vaivan.data.local.dao.ViagemDao
import com.example.vaivan.data.local.entities.EventoViagemEntity
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

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

    private val listenersPorViagem =
        mutableMapOf<String, Triple<ListenerRegistration, ListenerRegistration, ListenerRegistration>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "ViagemRepository"

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

        // Motivos gravados quando a parada foi resolvida por aviso do
        // responsável (não pelo motorista no ponto) — ver [ehFaltaAvisada].
        const val MOTIVO_AVISO_PREVIO = "Aviso prévio do responsável"
        const val MOTIVO_AVISO_EM_ANDAMENTO = "Aviso do responsável (viagem em andamento)"

        /** Parada que já nasceu (ou virou) resolvida por falta avisada: o
         * motorista não tem nada a confirmar nela, então não deve aparecer
         * no card de chegada (ex.: embarque na escola, na volta). */
        fun ehFaltaAvisada(parada: ParadaViagemEntity): Boolean =
            parada.motivoAusencia == MOTIVO_AVISO_PREVIO ||
                parada.motivoAusencia == MOTIVO_AVISO_EM_ANDAMENTO
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

    /** Mesma ideia de [consultarViagemAtivaDoMotorista], só que pelo lado do
     * responsável: ele não tem o motoristaId em mãos, mas sabe o rotaId da
     * viagem que quer acompanhar.
     *
     * Toda consulta de viagens feita pelo responsável PRECISA filtrar por
     * `responsaveisAutorizados array-contains responsavelId`: a regra de
     * leitura de `viagens` só libera o responsável por esse campo, e o
     * Firestore recusa (PERMISSION_DENIED) qualquer consulta que não prove,
     * pelos próprios filtros, que só devolve documentos permitidos. */
    suspend fun consultarViagemAtivaParaRota(rotaId: String, responsavelId: String): ViagemEntity? {
        val snapshot = collectionViagens
            .whereArrayContains("responsaveisAutorizados", responsavelId)
            .whereEqualTo("rotaId", rotaId)
            .whereIn("status", listOf(STATUS_EM_ANDAMENTO, STATUS_PAUSADA))
            .limit(1)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.let { documento ->
            documento.toObject(ViagemEntity::class.java)?.copy(id = documento.id)
        }
    }

    /** Mesma ideia de [consultarViagemAtivaParaRota], mais específica por
     * direção — usada quando um aviso de ausência precisa ser aplicado numa
     * viagem que já está em andamento (ver RegistrarAvisoAusenciaUseCase). */
    suspend fun consultarViagemAtivaParaRotaEDirecao(
        rotaId: String,
        direcao: String,
        responsavelId: String
    ): ViagemEntity? {
        val snapshot = collectionViagens
            .whereArrayContains("responsaveisAutorizados", responsavelId)
            .whereEqualTo("rotaId", rotaId)
            .whereEqualTo("direcao", direcao)
            .whereIn("status", listOf(STATUS_EM_ANDAMENTO, STATUS_PAUSADA))
            .limit(1)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.let { documento ->
            documento.toObject(ViagemEntity::class.java)?.copy(id = documento.id)
        }
    }

    /** Aplica retroativamente a falta avisada numa viagem já em andamento —
     * só muda as paradas desse passageiro que ainda estiverem pendentes
     * (o motorista ainda não passou por elas); se já tiver passado, não há
     * nada pra mudar e quem chamou consome o aviso do mesmo jeito. Nunca
     * toca na navegação/waypoints ao vivo — só o status, visível na próxima
     * atualização do painel do motorista e da tela do responsável.
     *
     * Importante: se o EMBARQUE desse passageiro já não estiver mais
     * AGUARDANDO (ele já subiu na van, ou já foi resolvido por outro
     * caminho), a função não altera NADA — nem a parada de desembarque
     * (ex.: a da escola). Sem essa checagem, marcar falta com o aluno já a
     * bordo resolvia a parada de desembarque dele prematuramente, o que
     * fazia NavegacaoViagemActivity.avancarSeAlvoAtualResolvido liberar o
     * SDK pro próximo destino antes da chegada física — o ponto final
     * "desaparecia" da navegação em andamento. */
    suspend fun aplicarAusenciaNaParadaAtiva(
        viagemId: String,
        passageiroId: String,
        responsavelId: String,
        motivo: String
    ) {
        val agora = System.currentTimeMillis()

        // Filtro por responsavelId: a regra de leitura de paradas_viagem só
        // libera o responsável pelas paradas dos próprios filhos.
        val paradasDoPassageiro = collectionParadas
            .whereEqualTo("responsavelId", responsavelId)
            .whereEqualTo("viagemId", viagemId)
            .whereEqualTo("passageiroId", passageiroId)
            .get()
            .await()
            .documents
            .mapNotNull { documento -> documento.toObject(ParadaViagemEntity::class.java)?.copy(id = documento.id) }

        val embarque = paradasDoPassageiro.firstOrNull { it.tipo == TIPO_EMBARQUE } ?: return
        if (embarque.status != PARADA_AGUARDANDO) return

        val paradas = paradasDoPassageiro
            .filter { it.status == PARADA_AGUARDANDO || it.status == PARADA_AGUARDANDO_DESEMBARQUE }

        paradas.forEach { parada ->
            val novoStatus = if (parada.tipo == TIPO_EMBARQUE) PARADA_NAO_EMBARCOU else PARADA_DESEMBARCADO
            val atualizada = parada.copy(status = novoStatus, motivoAusencia = motivo, lastUpdated = agora)

            collectionParadas.document(parada.id).set(atualizada).await()
            paradaViagemDao.upsert(atualizada)
        }
    }

    /** Diz se ainda faz sentido oferecer a opção "marcar falta" pra essa
     * rota+direção AGORA — false só quando existe uma viagem de hoje
     * ATIVA (em andamento/pausada) e o passageiro já embarcou nela (a falta
     * não teria efeito nenhum nessa viagem, ver aplicarAusenciaNaParadaAtiva
     * acima). Quando a viagem de hoje já terminou (ou nem começou, ou foi
     * cancelada), marcar falta agora é válido — não tem nada pra aplicar
     * hoje, então o aviso só fica pendente pra próxima viagem dessa direção
     * (pode ser já amanhã). Usado pela UI pra desabilitar a opção
     * correspondente antes mesmo de o responsável tentar marcar. */
    suspend fun aindaDaParaAvisarFaltaHoje(
        rotaId: String,
        passageiroId: String,
        direcao: String,
        responsavelId: String
    ): Boolean {
        val viagemHoje = consultarUltimaViagemDeHoje(rotaId, direcao, responsavelId) ?: return true
        if (viagemHoje.status != STATUS_EM_ANDAMENTO && viagemHoje.status != STATUS_PAUSADA) return true

        val embarque = collectionParadas
            .whereEqualTo("responsavelId", responsavelId)
            .whereEqualTo("viagemId", viagemHoje.id)
            .whereEqualTo("passageiroId", passageiroId)
            .whereEqualTo("tipo", TIPO_EMBARQUE)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toObject(ParadaViagemEntity::class.java)

        return embarque?.status == PARADA_AGUARDANDO
    }

    /** Última viagem de hoje pra essa rota+direção (qualquer status) — usada
     * pra decidir se ainda dá pra avisar falta, e como base pro sincronizador
     * ao vivo [iniciarSincronizacaoDoDia]/[observarUltimaViagemDeHoje]. */
    suspend fun consultarUltimaViagemDeHoje(rotaId: String, direcao: String, responsavelId: String): ViagemEntity? {
        // Usa o índice composto (responsaveisAutorizados, direcao, rotaId,
        // iniciadaEm DESC) declarado em admin-web/firestore.indexes.json.
        val snapshot = collectionViagens
            .whereArrayContains("responsaveisAutorizados", responsavelId)
            .whereEqualTo("rotaId", rotaId)
            .whereEqualTo("direcao", direcao)
            .whereGreaterThanOrEqualTo("iniciadaEm", inicioDoDiaEmMillis())
            .orderBy("iniciadaEm", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.let { documento ->
            documento.toObject(ViagemEntity::class.java)?.copy(id = documento.id)
        }
    }

    private fun inicioDoDiaEmMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun observarParadas(viagemId: String): Flow<List<ParadaViagemEntity>> =
        paradaViagemDao.getByViagem(viagemId)

    fun observarEventos(viagemId: String): Flow<List<EventoViagemEntity>> =
        eventoViagemDao.getByViagem(viagemId)

    // =========================================================
    // SINCRONIZAÇÃO (várias viagens em paralelo, uma entrada por viagemId —
    // ex.: a tela de navegação do motorista, a de acompanhamento do
    // responsável, e agora também cada card ativo da lista de rotas)
    // =========================================================

    /** [responsavelId] != null quando quem sincroniza é o responsável: aí as
     * paradas vêm filtradas pelas dos próprios filhos (é o que a regra de
     * `paradas_viagem` libera pra ele — sem o filtro, a consulta inteira é
     * recusada). O motorista passa null e recebe todas. */
    fun iniciarSincronizacao(viagemId: String, responsavelId: String? = null) {
        if (listenersPorViagem.containsKey(viagemId)) return

        val listenerViagem = collectionViagens.document(viagemId)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                snapshot.toObject(ViagemEntity::class.java)?.copy(id = snapshot.id)?.let {
                    scope.launch { viagemDao.upsert(it) }
                }
            }

        val consultaParadas = if (responsavelId != null) {
            collectionParadas
                .whereEqualTo("responsavelId", responsavelId)
                .whereEqualTo("viagemId", viagemId)
        } else {
            collectionParadas.whereEqualTo("viagemId", viagemId)
        }

        val listenerParadas = consultaParadas
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) Log.w(TAG, "Falha ao sincronizar paradas da viagem $viagemId", erro)
                if (erro != null || snapshot == null) return@addSnapshotListener

                val paradas = snapshot.documents.mapNotNull { documento ->
                    documento.toObject(ParadaViagemEntity::class.java)?.copy(id = documento.id)
                }

                scope.launch { paradaViagemDao.upsertAll(paradas) }
            }

        // Gancho pra notificação push (Etapa 5): mantém o histórico de eventos
        // sincronizado no Room do responsável também — uma Cloud Function vai
        // poder observar esses mesmos documentos no Firestore pra disparar o
        // push, sem precisar de nenhuma mudança aqui quando isso acontecer.
        val listenerEventos = collectionEventos
            .whereEqualTo("viagemId", viagemId)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null || snapshot == null) return@addSnapshotListener

                val eventos = snapshot.documents.mapNotNull { documento ->
                    documento.toObject(EventoViagemEntity::class.java)?.copy(id = documento.id)
                }

                scope.launch { eventoViagemDao.upsertAll(eventos) }
            }

        listenersPorViagem[viagemId] = Triple(listenerViagem, listenerParadas, listenerEventos)
    }

    fun pararSincronizacao(viagemId: String) {
        listenersPorViagem.remove(viagemId)?.let { (listenerViagem, listenerParadas, listenerEventos) ->
            listenerViagem.remove()
            listenerParadas.remove()
            listenerEventos.remove()
        }
    }

    /** Sincronizador leve (só o documento da viagem, sem paradas/eventos) pra
     * descobrir QUAL é a viagem de hoje de uma rota+direção sem precisar
     * saber o id de antemão — base pra [observarUltimaViagemDeHoje], usado
     * pela lista de rotas do responsável. Mesmo padrão de
     * AvisoAusenciaRepository.mirrorarMudancas. */
    fun iniciarSincronizacaoDoDia(rotaId: String, direcao: String, responsavelId: String): ListenerRegistration =
        collectionViagens
            .whereArrayContains("responsaveisAutorizados", responsavelId)
            .whereEqualTo("rotaId", rotaId)
            .whereEqualTo("direcao", direcao)
            .whereGreaterThanOrEqualTo("iniciadaEm", inicioDoDiaEmMillis())
            // DESC só pra reaproveitar o mesmo índice de consultarUltimaViagemDeHoje
            // (a ordem não importa aqui: tudo vai pro Room).
            .orderBy("iniciadaEm", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) Log.w(TAG, "Falha ao sincronizar viagens do dia da rota $rotaId ($direcao)", erro)
                if (erro != null || snapshot == null) return@addSnapshotListener

                scope.launch {
                    for (mudanca in snapshot.documentChanges) {
                        val documento = mudanca.document
                        when (mudanca.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED ->
                                documento.toObject(ViagemEntity::class.java)
                                    ?.copy(id = documento.id)
                                    ?.let { viagemDao.upsert(it) }

                            DocumentChange.Type.REMOVED -> viagemDao.deleteById(documento.id)
                        }
                    }
                }
            }

    fun observarUltimaViagemDeHoje(rotaId: String, direcao: String): Flow<ViagemEntity?> =
        viagemDao.getUltimaViagemDoDia(rotaId, direcao, inicioDoDiaEmMillis())

    // =========================================================
    // INICIAR VIAGEM
    // =========================================================

    /**
     * Cria a viagem e o snapshot das paradas a partir da rota cadastrada.
     * Ordem dos waypoints:
     *  - IDA: paradas dos alunos (embarque, na ordem da rota) + escola (desembarque, todos).
     *  - VOLTA: escola (embarque, todos) + paradas dos alunos (desembarque, na ordem da rota).
     */
    /** Passageiro com falta avisada nasce direto no status terminal
     * correspondente ao tipo da própria linha (embarque->NAO_EMBARCOU,
     * desembarque->DESEMBARCADO) — nunca entra em AGUARDANDO/
     * AGUARDANDO_DESEMBARQUE, então nunca aparece como pendente pro
     * motorista nem entra nos waypoints da navegação (ver
     * NavegacaoViagemActivity.montarWaypointsEIniciarGuiaSePossivel). */
    private fun statusAusenteOu(
        passageiroId: String,
        passageirosAusentes: Set<String>,
        tipo: String,
        statusPadrao: String
    ): String {
        if (passageiroId !in passageirosAusentes) return statusPadrao
        return if (tipo == TIPO_EMBARQUE) PARADA_NAO_EMBARCOU else PARADA_DESEMBARCADO
    }

    private fun motivoAusenciaOu(passageiroId: String, passageirosAusentes: Set<String>): String? =
        if (passageiroId in passageirosAusentes) MOTIVO_AVISO_PREVIO else null

    suspend fun iniciarViagem(
        rota: RotaEntity,
        paradasRota: List<ParadaRotaEntity>,
        motoristaId: String,
        veiculoId: String?,
        direcao: String,
        responsavelPorPassageiro: Map<String, String>,
        fotoPorPassageiro: Map<String, String?> = emptyMap(),
        passageirosAusentes: Set<String> = emptySet()
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
                fotoPassageiroUrl = fotoPorPassageiro[paradaRota.passageiroId],
                responsavelId = responsavelPorPassageiro[paradaRota.passageiroId].orEmpty(),
                localId = paradaRota.localId,
                nomeLocal = paradaRota.nomeLocal,
                endereco = paradaRota.endereco,
                latitude = paradaRota.latitude,
                longitude = paradaRota.longitude,
                tipo = tipoParadasAluno,
                ordem = index,
                status = statusAusenteOu(paradaRota.passageiroId, passageirosAusentes, tipoParadasAluno, statusInicialAluno),
                motivoAusencia = motivoAusenciaOu(paradaRota.passageiroId, passageirosAusentes),
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
                fotoPassageiroUrl = fotoPorPassageiro[paradaRota.passageiroId],
                responsavelId = responsavelPorPassageiro[paradaRota.passageiroId].orEmpty(),
                localId = "escola",
                nomeLocal = rota.escolaNome.ifBlank { rota.destinoNome },
                endereco = rota.destinoEndereco,
                latitude = rota.destinoLatitude,
                longitude = rota.destinoLongitude,
                tipo = tipoParadaEscola,
                ordem = ordemEscola,
                status = statusAusenteOu(paradaRota.passageiroId, passageirosAusentes, tipoParadaEscola, statusInicialEscola),
                motivoAusencia = motivoAusenciaOu(paradaRota.passageiroId, passageirosAusentes),
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

    /** Chamado pelo responsável (client-side, via proximidade GPS) quando a
     * van entra no raio do ponto do filho — ver AcompanharViagemActivity. */
    suspend fun registrarChegada(viagemId: String, paradaViagemId: String) {
        registrarEvento(viagemId, paradaViagemId, "CHEGADA")
    }

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
