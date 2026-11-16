package com.example.vaivan.domain.usecase.solicitacao

import com.example.vaivan.data.local.dao.SolicitacaoInclusaoDao
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.models.StatusContrato
import com.example.vaivan.data.models.StatusMensalidade
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository.Companion.STATUS_ACEITA
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository.Companion.STATUS_AGUARDANDO_PAGAMENTO
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository.Companion.STATUS_CANCELADA
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository.Companion.STATUS_EXPIRADA
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository.Companion.STATUS_PENDENTE
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Desfaz o vínculo de um aluno com uma rota, em três situações:
 *
 *  - CANCELAR (responsável), em qualquer etapa:
 *     PENDENTE              -> só marca CANCELADA;
 *     AGUARDANDO_PAGAMENTO  -> libera a vaga reservada e cancela a 1ª mensalidade em aberto;
 *     ACEITA (rota ativa)   -> libera a vaga, tira as paradas do trajeto e cancela só as
 *                              mensalidades que ainda não venceram. Pagas não são devolvidas;
 *                              vencidas continuam devidas.
 *
 *  - EXPIRAR (motorista OU responsável, quem abrir o app primeiro): reserva cuja 1ª
 *    mensalidade não foi paga em 3 dias. Libera a vaga e cancela a mensalidade.
 *
 * Tudo vai num batch (vale inteiro ou nada). Não usa Cloud Functions: as regras do
 * Firestore conferem cada passo (ver firestore.rules, coleções rotas, contratos e
 * mensalidades).
 *
 * Trajeto: o responsável não pode gravar o desenho da rota, então aqui só fica o
 * aviso `ultimaSolicitacaoCancelada` na rota. O app do motorista vê esse aviso e
 * recalcula o trajeto com os alunos que sobraram (AtualizarTrajetoDaRotaUseCase,
 * ao abrir as rotas e antes de iniciar viagem).
 */
class CancelarSolicitacaoUseCase(
    private val solicitacaoDao: SolicitacaoInclusaoDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /** Cancelamento pelo responsável. */
    suspend operator fun invoke(solicitacaoId: String) {
        val uid = usuarioAtual()
        val documento = carregar(solicitacaoId)

        if (documento.getString("responsavelId") != uid) {
            throw IllegalStateException("Solicitação não encontrada.")
        }

        when (documento.getString("status")) {
            STATUS_PENDENTE -> {
                val agora = System.currentTimeMillis()
                documento.reference.update(
                    mapOf("status" to STATUS_CANCELADA, "canceladaEm" to agora, "lastUpdated" to agora)
                ).await()
                salvarLocal(documento, STATUS_CANCELADA, agora)
            }
            STATUS_AGUARDANDO_PAGAMENTO -> liberar(uid, documento, STATUS_CANCELADA, eraRotaAtiva = false)
            STATUS_ACEITA -> liberar(uid, documento, STATUS_CANCELADA, eraRotaAtiva = true)
            else -> throw IllegalStateException("Esta solicitação não está mais ativa.")
        }
    }

    /**
     * Expira uma reserva cujo prazo do 1º pagamento acabou. Pode ser chamado pelo app
     * do motorista ou do responsável. Devolve true se expirou.
     */
    suspend fun expirarReserva(solicitacaoId: String): Boolean {
        val uid = usuarioAtual()
        val documento = carregar(solicitacaoId)

        if (uid != documento.getString("motoristaId") && uid != documento.getString("responsavelId")) return false
        if (documento.getString("status") != STATUS_AGUARDANDO_PAGAMENTO) return false

        val aceiteEm = documento.getLong("respondidoEm") ?: 0L
        if (!MensalidadePolicy.reservaExpirada(aceiteEm, System.currentTimeMillis())) return false

        liberar(uid, documento, STATUS_EXPIRADA, eraRotaAtiva = false)
        return true
    }

    // ---------------------------------------------------------

    private suspend fun liberar(
        uid: String,
        solicitacao: DocumentSnapshot,
        novoStatus: String,
        eraRotaAtiva: Boolean
    ) {
        val solicitacaoId = solicitacao.id
        val rotaId = solicitacao.getString("rotaId").orEmpty()
        val passageiroId = solicitacao.getString("passageiroId").orEmpty()
        val souMotorista = uid == solicitacao.getString("motoristaId")
        val agora = System.currentTimeMillis()

        val rotaRef = firestore.collection(COL_ROTAS).document(rotaId)
        val contratoRef = firestore.collection(COL_CONTRATOS).document(solicitacaoId)

        val rota = if (rotaId.isBlank()) null else rotaRef.get().await()

        // Só a rota ativa tem o aluno no trajeto
        val paradas = if (!eraRotaAtiva || rotaId.isBlank()) emptyList() else firestore.collection(COL_PARADAS)
            .whereEqualTo("rotaId", rotaId)
            .whereEqualTo("passageiroId", passageiroId)
            .get().await()
            .documents

        val contrato = contratoRef.get().await()
        val contratoAberto = contrato.exists() &&
            contrato.getString("status") in setOf(StatusContrato.ATIVO, StatusContrato.AGUARDANDO_PAGAMENTO)

        // Rota ativa: só as que ainda não venceram. Reserva: a 1ª, mesmo vencida (nunca foi usada).
        val mensalidadesParaCancelar = if (!contrato.exists()) emptyList() else firestore.collection(COL_MENSALIDADES)
            .whereEqualTo(if (souMotorista) "motoristaId" else "responsavelId", uid)
            .whereEqualTo("contratoId", solicitacaoId)
            .get().await()
            .documents
            .filter { it.getString("status") == StatusMensalidade.PENDENTE }
            .filter { !eraRotaAtiva || (it.getLong("vencimento") ?: 0L) > agora }

        val batch = firestore.batch()

        batch.update(
            solicitacao.reference,
            mapOf(
                "status" to novoStatus,
                (if (novoStatus == STATUS_EXPIRADA) "expiradaEm" else "canceladaEm") to agora,
                "lastUpdated" to agora
            )
        )

        // Libera a vaga (a regra confere que é exatamente 1 e que é desta solicitação)
        if (rota != null && rota.exists() && (rota.getLong("vagasOcupadas") ?: 0L) > 0L) {
            batch.update(
                rotaRef,
                mapOf(
                    "vagasOcupadas" to FieldValue.increment(-1),
                    "ultimaSolicitacaoCancelada" to solicitacaoId
                )
            )
        }

        paradas.forEach { batch.delete(it.reference) }

        if (contratoAberto) {
            batch.update(
                contratoRef,
                mapOf("status" to StatusContrato.ENCERRADO, "encerradoEm" to agora, "atualizadoEm" to agora)
            )
        }

        mensalidadesParaCancelar.forEach {
            batch.update(it.reference, mapOf("status" to StatusMensalidade.CANCELADA, "atualizadoEm" to agora))
        }

        batch.commit().await()

        salvarLocal(solicitacao, novoStatus, agora)
    }

    private fun usuarioAtual(): String =
        FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("Sessão expirada. Entre novamente.")

    private suspend fun carregar(solicitacaoId: String): DocumentSnapshot {
        val documento = firestore.collection(COL_SOLICITACOES).document(solicitacaoId).get().await()
        if (!documento.exists()) throw IllegalStateException("Solicitação não encontrada.")
        return documento
    }

    /** Cópia local, para a tela mudar na hora (o listener também atualiza depois). */
    private suspend fun salvarLocal(documento: DocumentSnapshot, status: String, agora: Long) {
        documento.toObject(SolicitacaoInclusaoEntity::class.java)?.let {
            solicitacaoDao.upsert(it.copy(id = documento.id, status = status, lastUpdated = agora))
        }
    }

    private companion object {
        const val COL_SOLICITACOES = "solicitacoes_inclusao"
        const val COL_ROTAS = "rotas"
        const val COL_PARADAS = "paradas_rota"
        const val COL_CONTRATOS = "contratos"
        const val COL_MENSALIDADES = "mensalidades"
    }
}
