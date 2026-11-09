package com.example.vaivan.data.repository

import android.util.Log
import com.example.vaivan.data.models.Contrato
import com.example.vaivan.data.models.FormaPagamentoMensalidade
import com.example.vaivan.data.models.Mensalidade
import com.example.vaivan.data.models.RegistradoPor
import com.example.vaivan.data.models.StatusContrato
import com.example.vaivan.data.models.StatusMensalidade
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Contratos e mensalidades entre responsáveis e motoristas.
 *
 * Como o AssinaturaRepository, lê direto do Firestore (sem Room): é dado financeiro e
 * precisa ser o do servidor. O cache offline do próprio SDK continua valendo.
 *
 * Não há servidor (Cloud Functions) gerando as cobranças todo mês. Por isso quem abre a
 * tela (motorista ou responsável) cria as mensalidades que estiverem faltando. Isso é
 * seguro porque o id de cada mensalidade é fixo ("<contrato>_<AAAA-MM>") e a criação é
 * feita numa transação que nunca sobrescreve um documento que já existe.
 *
 * ATENÇÃO (ambiente acadêmico): o pagamento pelo app é SIMULADO, como na assinatura.
 */
class MensalidadeRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val colContratos = firestore.collection("contratos")
    private val colMensalidades = firestore.collection("mensalidades")
    private val colSolicitacoes = firestore.collection("solicitacoes_inclusao")
    private val colRotas = firestore.collection("rotas")
    private val colUsuarios = firestore.collection("usuarios")

    // =========================================================
    // OBSERVAÇÃO (tempo real)
    // =========================================================

    fun observarContratosDoMotorista(motoristaId: String) = observarContratos("motoristaId", motoristaId)

    fun observarContratosDoResponsavel(responsavelId: String) = observarContratos("responsavelId", responsavelId)

    fun observarMensalidadesDoMotorista(motoristaId: String) = observarMensalidades("motoristaId", motoristaId)

    fun observarMensalidadesDoResponsavel(responsavelId: String) = observarMensalidades("responsavelId", responsavelId)

    private fun observarContratos(campo: String, valor: String): Flow<List<Contrato>> =
        callbackFlow<List<Contrato>> {
            val registro = colContratos.whereEqualTo(campo, valor)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e(TAG, "Erro ao observar contratos", error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Contrato::class.java)?.copy(id = doc.id)
                    })
                }
            awaitClose { registro.remove() }
        }

    private fun observarMensalidades(campo: String, valor: String): Flow<List<Mensalidade>> =
        callbackFlow<List<Mensalidade>> {
            val registro = colMensalidades.whereEqualTo(campo, valor)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e(TAG, "Erro ao observar mensalidades", error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Mensalidade::class.java)?.copy(id = doc.id)
                    })
                }
            awaitClose { registro.remove() }
        }

    // =========================================================
    // CONTRATOS
    // =========================================================

    /**
     * Cria o contrato de uma solicitação recém-aceita (chamado pelo AceitarSolicitacaoUseCase).
     * Não faz nada se a solicitação não estiver aceita ou se o contrato já existir.
     */
    suspend fun garantirContrato(solicitacaoId: String, inicioEm: Long) {
        val solicitacao = colSolicitacoes.document(solicitacaoId).get().await()
        if (!solicitacao.exists() || solicitacao.getString("status") != "ACEITA") return

        val ref = colContratos.document(solicitacaoId)
        if (ref.get().await().exists()) return

        val rotaId = solicitacao.getString("rotaId").orEmpty()
        if (rotaId.isBlank()) return
        val rota = colRotas.document(rotaId).get().await()
        val nomeResponsavel = nomeDoUsuario(solicitacao.getString("responsavelId").orEmpty())

        ref.set(montarContrato(solicitacao, rota, nomeResponsavel, inicioEm)).await()
    }

    /**
     * Cria contratos para passageiros que já tinham sido aceitos ANTES desta funcionalidade
     * existir. Esses contratos começam hoje, para ninguém aparecer com meses de atraso
     * retroativos. Devolve quantos contratos foram criados.
     */
    suspend fun sincronizarContratosDoMotorista(motoristaId: String): Int {
        val aceitas = colSolicitacoes
            .whereEqualTo("motoristaId", motoristaId)
            .whereEqualTo("status", "ACEITA")
            .get().await()
            .documents

        val existentes = colContratos
            .whereEqualTo("motoristaId", motoristaId)
            .get().await()
            .documents.map { it.id }.toSet()

        val faltando = aceitas.filter {
            it.id !in existentes && !it.getString("rotaId").isNullOrBlank()
        }
        if (faltando.isEmpty()) return 0

        val agora = System.currentTimeMillis()
        val rotas = mutableMapOf<String, DocumentSnapshot>()
        val nomes = mutableMapOf<String, String>()
        val batch = firestore.batch()

        faltando.forEach { solicitacao ->
            val rotaId = solicitacao.getString("rotaId").orEmpty()
            val responsavelId = solicitacao.getString("responsavelId").orEmpty()

            val rota = rotas.getOrPut(rotaId) { colRotas.document(rotaId).get().await() }
            val nome = nomes.getOrPut(responsavelId) { nomeDoUsuario(responsavelId) }

            batch.set(colContratos.document(solicitacao.id), montarContrato(solicitacao, rota, nome, agora))
        }
        batch.commit().await()
        return faltando.size
    }

    /**
     * Muda valor e/ou dia de vencimento de um contrato. Vale para as mensalidades que
     * ainda não foram geradas; as já geradas mantêm o valor com que foram criadas.
     */
    suspend fun reajustarContrato(contratoId: String, valorMensalCentavos: Long, diaVencimento: Int) {
        require(valorMensalCentavos > 0) { "Informe um valor maior que zero." }
        colContratos.document(contratoId).update(
            mapOf(
                "valorMensalCentavos" to valorMensalCentavos,
                "diaVencimento" to MensalidadePolicy.diaValido(diaVencimento),
                "atualizadoEm" to System.currentTimeMillis()
            )
        ).await()
    }

    /**
     * Encerra o contrato (passageiro saiu da rota). Mensalidades em aberto que ainda não
     * venceram são canceladas; as já vencidas continuam devidas.
     */
    suspend fun encerrarContrato(contrato: Contrato) {
        val agora = System.currentTimeMillis()
        val hoje = MensalidadePolicy.inicioDoDia(MensalidadePolicy.dia(agora))

        val futuras = colMensalidades
            .whereEqualTo("motoristaId", contrato.motoristaId)
            .whereEqualTo("contratoId", contrato.id)
            .get().await()
            .documents
            .filter {
                it.getString("status") == StatusMensalidade.PENDENTE &&
                    (it.getLong("vencimento") ?: 0L) > hoje
            }

        val batch = firestore.batch()
        batch.update(
            colContratos.document(contrato.id),
            mapOf("status" to StatusContrato.ENCERRADO, "encerradoEm" to agora, "atualizadoEm" to agora)
        )
        futuras.forEach {
            batch.update(it.reference, mapOf("status" to StatusMensalidade.CANCELADA, "atualizadoEm" to agora))
        }
        batch.commit().await()
    }

    // =========================================================
    // GERAÇÃO DAS MENSALIDADES
    // =========================================================

    /**
     * Cria as mensalidades que estão faltando para os contratos informados.
     * [existentes] é o que a tela já tem em mãos (evita idas ao servidor à toa).
     * Devolve quantas foram criadas.
     */
    suspend fun gerarMensalidadesFaltando(
        contratos: List<Contrato>,
        existentes: List<Mensalidade>
    ): Int = coroutineScope {
        val agora = System.currentTimeMillis()
        val ids = existentes.map { it.id }.toSet()

        // Sem valor definido não há o que cobrar: o motorista define o valor primeiro.
        val novas = contratos.filter { it.valorMensalCentavos > 0 }.flatMap { contrato ->
            MensalidadePolicy.competenciasFaltando(contrato, ids, agora).map { mes ->
                Mensalidade(
                    id = MensalidadePolicy.idMensalidade(contrato.id, mes),
                    contratoId = contrato.id,
                    motoristaId = contrato.motoristaId,
                    responsavelId = contrato.responsavelId,
                    passageiroId = contrato.passageiroId,
                    nomePassageiro = contrato.nomePassageiro,
                    nomeRota = contrato.nomeRota,
                    competencia = mes.toString(),
                    valorCentavos = contrato.valorMensalCentavos,
                    vencimento = MensalidadePolicy.inicioDoDia(
                        MensalidadePolicy.vencimentoDa(mes, contrato.diaVencimento)
                    ),
                    status = StatusMensalidade.PENDENTE,
                    criadoEm = agora,
                    atualizadoEm = agora
                )
            }
        }

        novas.map { mensalidade ->
            async {
                try {
                    val ref = colMensalidades.document(mensalidade.id)
                    firestore.runTransaction { t ->
                        // Nunca sobrescreve: se a outra parte já criou (ou já pagou), deixa como está.
                        if (!t.get(ref).exists()) t.set(ref, mensalidade)
                        null
                    }.await()
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Não foi possível criar a mensalidade ${mensalidade.id}", e)
                    false
                }
            }
        }.awaitAll().count { it }
    }

    // =========================================================
    // PAGAMENTOS
    // =========================================================

    /** O motorista recebeu por fora (PIX direto, dinheiro...) e registra no app. */
    suspend fun registrarRecebimento(mensalidadeId: String, forma: String) {
        require(forma in FormaPagamentoMensalidade.manuais) { "Forma de pagamento inválida." }
        marcarComoPaga(mensalidadeId, forma, RegistradoPor.MOTORISTA)
    }

    /** O responsável paga pelo app (SIMULADO). */
    suspend fun pagarPeloApp(mensalidadeId: String) {
        marcarComoPaga(mensalidadeId, FormaPagamentoMensalidade.PIX_APP, RegistradoPor.RESPONSAVEL)
    }

    /** Desfaz um recebimento registrado por engano. Só vale para os registrados pelo motorista. */
    suspend fun desfazerRecebimento(mensalidadeId: String) {
        val ref = colMensalidades.document(mensalidadeId)
        firestore.runTransaction { t ->
            val atual = t.get(ref)
            if (atual.getString("status") != StatusMensalidade.PAGA ||
                atual.getString("registradoPor") != RegistradoPor.MOTORISTA
            ) {
                throw falha("Só é possível desfazer recebimentos registrados por você.")
            }
            t.update(
                ref,
                mapOf(
                    "status" to StatusMensalidade.PENDENTE,
                    "pagoEm" to FieldValue.delete(),
                    "forma" to "",
                    "registradoPor" to "",
                    "atualizadoEm" to System.currentTimeMillis()
                )
            )
            null
        }.await()
    }

    private suspend fun marcarComoPaga(mensalidadeId: String, forma: String, registradoPor: String) {
        val ref = colMensalidades.document(mensalidadeId)
        val agora = System.currentTimeMillis()
        firestore.runTransaction { t ->
            if (t.get(ref).getString("status") != StatusMensalidade.PENDENTE) {
                throw falha("Esta mensalidade não está mais em aberto.")
            }
            t.update(
                ref,
                mapOf(
                    "status" to StatusMensalidade.PAGA,
                    "pagoEm" to agora,
                    "forma" to forma,
                    "registradoPor" to registradoPor,
                    "atualizadoEm" to agora
                )
            )
            null
        }.await()
    }

    // =========================================================
    // AUXILIARES
    // =========================================================

    private fun montarContrato(
        solicitacao: DocumentSnapshot,
        rota: DocumentSnapshot,
        nomeResponsavel: String,
        inicioEm: Long
    ): Contrato {
        val agora = System.currentTimeMillis()
        return Contrato(
            id = solicitacao.id,
            motoristaId = solicitacao.getString("motoristaId").orEmpty(),
            responsavelId = solicitacao.getString("responsavelId").orEmpty(),
            nomeResponsavel = nomeResponsavel,
            passageiroId = solicitacao.getString("passageiroId").orEmpty(),
            nomePassageiro = solicitacao.getString("nomePassageiro").orEmpty(),
            rotaId = solicitacao.getString("rotaId").orEmpty(),
            nomeRota = rota.getString("nome").orEmpty(),
            valorMensalCentavos = rota.getLong("valorMensalCentavos") ?: 0L,
            diaVencimento = MensalidadePolicy.diaValido(
                (rota.getLong("diaVencimento") ?: MensalidadePolicy.DIA_VENCIMENTO_PADRAO.toLong()).toInt()
            ),
            status = StatusContrato.ATIVO,
            inicioEm = inicioEm,
            criadoEm = agora,
            atualizadoEm = agora
        )
    }

    private suspend fun nomeDoUsuario(uid: String): String =
        if (uid.isBlank()) "" else try {
            colUsuarios.document(uid).get().await().getString("nome").orEmpty()
        } catch (e: Exception) {
            ""
        }

    private fun falha(mensagem: String) =
        FirebaseFirestoreException(mensagem, FirebaseFirestoreException.Code.FAILED_PRECONDITION)

    companion object {
        private const val TAG = "MensalidadeRepository"
    }
}
