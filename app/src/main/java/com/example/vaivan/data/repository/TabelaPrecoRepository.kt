package com.example.vaivan.data.repository

import com.example.vaivan.data.models.TabelaPrecoMotorista
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/** Tabelas de preços dos motoristas ("tabelas_preco/{motoristaId}"). */
class TabelaPrecoRepository(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val colecao = firestore.collection("tabelas_preco")

    /** A tabela do motorista, ou null se ele ainda não criou. */
    suspend fun buscar(motoristaId: String): TabelaPrecoMotorista? {
        val doc = colecao.document(motoristaId).get().await()
        return if (doc.exists()) doc.toObject(TabelaPrecoMotorista::class.java) else null
    }

    /**
     * Tabelas de vários motoristas de uma vez (uma leitura por motorista, em paralelo).
     * Quem não tem tabela, ou deu erro na leitura, simplesmente fica de fora.
     */
    suspend fun buscarVarias(motoristaIds: Set<String>): Map<String, TabelaPrecoMotorista> =
        coroutineScope {
            motoristaIds
                .filter { it.isNotBlank() }
                .map { id -> async { id to runCatching { buscar(id) }.getOrNull() } }
                .awaitAll()
                .mapNotNull { (id, tabela) -> tabela?.let { id to it } }
                .toMap()
        }

    suspend fun salvar(tabela: TabelaPrecoMotorista) {
        colecao.document(tabela.motoristaId)
            .set(tabela.copy(atualizadoEm = System.currentTimeMillis()))
            .await()
    }
}