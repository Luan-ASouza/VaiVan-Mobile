package com.example.vaivan.data.repository

import android.content.Context
import com.example.vaivan.data.local.dao.SolicitacaoInclusaoDao
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SolicitacaoInclusaoRepository(
    private val context: Context,
    private val dao: SolicitacaoInclusaoDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private val collection =
        firestore.collection("solicitacoes_inclusao")

    private val scope =
        CoroutineScope(Dispatchers.IO)


    fun observarPendentesPorMotorista(
        motoristaId: String
    ): Flow<List<SolicitacaoInclusaoEntity>> =
        dao.getPendentesPorMotorista(motoristaId)


    /**
     * Mantém o Room do MOTORISTA atualizado com as solicitações que chegam no Firestore.
     * (Antes, a solicitação só aparecia no mesmo celular em que foi criada.)
     */
    fun iniciarSincronizacaoDoMotorista(
        motoristaId: String
    ): ListenerRegistration =
        collection
            .whereEqualTo("motoristaId", motoristaId)
            .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }

                scope.launch {

                    for (mudanca in snapshot.documentChanges) {

                        val documento = mudanca.document

                        when (mudanca.type) {

                            DocumentChange.Type.ADDED,
                            DocumentChange.Type.MODIFIED -> {

                                val item =
                                    documento.toObject(
                                        SolicitacaoInclusaoEntity::class.java
                                    ).copy(
                                        id = documento.id,
                                        lastUpdated =
                                            System.currentTimeMillis()
                                    )

                                dao.upsert(item)
                            }

                            DocumentChange.Type.REMOVED ->
                                dao.deleteById(documento.id)
                        }
                    }
                }
            }


    /** Guarda no Room a solicitação aceita (a gravação no Firestore foi feita na transação). */
    suspend fun registrarAceitaLocalmente(
        solicitacao: SolicitacaoInclusaoEntity,
        respondidaEm: Long
    ) = withContext(Dispatchers.IO) {

        dao.upsert(
            solicitacao.copy(
                status = "ACEITA",
                respondidoEm = respondidaEm,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }


    fun observarPorResponsavel(
        responsavelId: String
    ): Flow<List<SolicitacaoInclusaoEntity>> =
        dao.getPorResponsavel(responsavelId)


    suspend fun enviarSolicitacao(
        item: SolicitacaoInclusaoEntity
    ) = withContext(Dispatchers.IO) {

        val docRef =
            collection.document()

        val comId =
            item.copy(
                id = docRef.id,
                criadoEm = System.currentTimeMillis(),
                status = "PENDENTE"
            )

        docRef
            .set(comId)
            .await()

        dao.upsert(comId)
    }


    suspend fun marcarComoAceita(
        solicitacao: SolicitacaoInclusaoEntity
    ) = withContext(Dispatchers.IO) {

        val atualizada =
            solicitacao.copy(
                status = "ACEITA",
                respondidoEm =
                    System.currentTimeMillis()
            )

        collection
            .document(solicitacao.id)
            .set(atualizada)
            .await()

        dao.upsert(atualizada)
    }


    suspend fun marcarComoRecusada(
        solicitacao: SolicitacaoInclusaoEntity
    ) = withContext(Dispatchers.IO) {

        val atualizada =
            solicitacao.copy(
                status = "RECUSADA",
                respondidoEm =
                    System.currentTimeMillis()
            )

        collection
            .document(solicitacao.id)
            .set(atualizada)
            .await()

        dao.upsert(atualizada)
    }


    fun aceitarAsync(
        solicitacao: SolicitacaoInclusaoEntity,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {

        scope.launch {

            try {

                marcarComoAceita(solicitacao)

                withContext(Dispatchers.Main) {
                    onSuccess()
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }


    fun recusarAsync(
        solicitacao: SolicitacaoInclusaoEntity,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {

        scope.launch {

            try {

                marcarComoRecusada(solicitacao)

                withContext(Dispatchers.Main) {
                    onSuccess()
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }


    fun enviarSolicitacaoAsync(
        item: SolicitacaoInclusaoEntity,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {

        scope.launch {

            try {

                enviarSolicitacao(item)

                withContext(Dispatchers.Main) {
                    onSuccess()
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
}