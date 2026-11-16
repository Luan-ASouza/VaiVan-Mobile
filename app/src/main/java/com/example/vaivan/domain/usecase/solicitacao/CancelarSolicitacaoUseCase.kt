package com.example.vaivan.domain.usecase.solicitacao

import com.example.vaivan.data.local.dao.SolicitacaoInclusaoDao
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.models.StatusContrato
import com.example.vaivan.data.models.StatusMensalidade
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * O RESPONSÁVEL cancela o pedido de vaga do filho, esteja ele pendente ou já aceito.
 *
 * Pendente: a solicitação só passa para CANCELADA.
 *
 * Aceito: tudo vai numa gravação só (batch), que vale inteira ou não vale nada:
 *  - a solicitação passa para CANCELADA;
 *  - a vaga volta a ficar livre na rota (vagasOcupadas - 1);
 *  - as paradas do filho saem da rota;
 *  - o contrato é encerrado;
 *  - as mensalidades que AINDA NÃO VENCERAM e não foram pagas são canceladas.
 *    As já vencidas continuam devidas e as pagas não são devolvidas.
 *
 * Não usa Cloud Functions: as regras do Firestore só permitem que o responsável
 * mexa na vaga, no contrato e nas mensalidades quando, no mesmo batch, ele está
 * cancelando uma solicitação ACEITA do próprio filho (ver firestore.rules).
 *
 * Observação: o desenho do trajeto da rota só é recalculado no próximo aceite
 * (ou quando o motorista editar a rota). Até lá, a linha no mapa ainda passa
 * pelo ponto antigo, mas a parada do aluno já não existe.
 */
class CancelarSolicitacaoUseCase(
    private val solicitacaoDao: SolicitacaoInclusaoDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend operator fun invoke(solicitacaoId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("Sessão expirada. Entre novamente.")

        val solicitacaoRef = firestore.collection(COL_SOLICITACOES).document(solicitacaoId)
        val documento = solicitacaoRef.get().await()

        if (!documento.exists() || documento.getString("responsavelId") != uid) {
            throw IllegalStateException("Solicitação não encontrada.")
        }

        val agora = System.currentTimeMillis()
        val cancelamento = mapOf(
            "status" to STATUS_CANCELADA,
            "canceladaEm" to agora,
            "lastUpdated" to agora
        )

        when (documento.getString("status")) {
            STATUS_PENDENTE -> solicitacaoRef.update(cancelamento).await()
            STATUS_ACEITA -> cancelarRotaAceita(uid, solicitacaoId, documento.getString("rotaId").orEmpty(),
                documento.getString("passageiroId").orEmpty(), cancelamento, agora)
            else -> throw IllegalStateException("Esta solicitação não está mais ativa.")
        }

        // Cópia local, para a tela mudar na hora (o listener também atualiza depois)
        documento.toObject(SolicitacaoInclusaoEntity::class.java)?.let {
            solicitacaoDao.upsert(it.copy(id = solicitacaoId, status = STATUS_CANCELADA, lastUpdated = agora))
        }
    }

    private suspend fun cancelarRotaAceita(
        uid: String,
        solicitacaoId: String,
        rotaId: String,
        passageiroId: String,
        cancelamento: Map<String, Any>,
        agora: Long
    ) {
        val rotaRef = firestore.collection(COL_ROTAS).document(rotaId)
        val contratoRef = firestore.collection(COL_CONTRATOS).document(solicitacaoId)

        val rota = if (rotaId.isBlank()) null else rotaRef.get().await()

        val paradas = if (rotaId.isBlank()) emptyList() else firestore.collection(COL_PARADAS)
            .whereEqualTo("rotaId", rotaId)
            .whereEqualTo("passageiroId", passageiroId)
            .get().await()
            .documents

        val contrato = contratoRef.get().await()
        val contratoAtivo = contrato.exists() && contrato.getString("status") == StatusContrato.ATIVO

        // Só as que ainda não venceram e não foram pagas
        val mensalidadesFuturas = if (!contrato.exists()) emptyList() else firestore.collection(COL_MENSALIDADES)
            .whereEqualTo("responsavelId", uid)
            .whereEqualTo("contratoId", solicitacaoId)
            .get().await()
            .documents
            .filter {
                it.getString("status") == StatusMensalidade.PENDENTE &&
                    (it.getLong("vencimento") ?: 0L) > agora
            }

        val batch = firestore.batch()

        batch.update(firestore.collection(COL_SOLICITACOES).document(solicitacaoId), cancelamento)

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

        if (contratoAtivo) {
            batch.update(
                contratoRef,
                mapOf(
                    "status" to StatusContrato.ENCERRADO,
                    "encerradoEm" to agora,
                    "atualizadoEm" to agora
                )
            )
        }

        mensalidadesFuturas.forEach {
            batch.update(
                it.reference,
                mapOf("status" to StatusMensalidade.CANCELADA, "atualizadoEm" to agora)
            )
        }

        batch.commit().await()
    }

    companion object {
        const val STATUS_PENDENTE = "PENDENTE"
        const val STATUS_ACEITA = "ACEITA"
        const val STATUS_CANCELADA = "CANCELADA"

        private const val COL_SOLICITACOES = "solicitacoes_inclusao"
        private const val COL_ROTAS = "rotas"
        private const val COL_PARADAS = "paradas_rota"
        private const val COL_CONTRATOS = "contratos"
        private const val COL_MENSALIDADES = "mensalidades"
    }
}
