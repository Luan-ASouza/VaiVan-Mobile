package com.example.trabalhograua.data.repository

import com.example.trabalhograua.data.local.dao.ResponsavelDao
import com.example.trabalhograua.data.local.entities.ResponsavelEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firestore = fonte da verdade
 * Room = cache local que a UI observa
 */
class ResponsavelRepository(
    private val responsavelDao: ResponsavelDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    // ---------------------------------------------------------
    // FIRESTORE
    // ---------------------------------------------------------

    private val collection =
        firestore.collection("responsaveis")

    // ---------------------------------------------------------
    // SINCRONIZAÇÃO
    // ---------------------------------------------------------

    private var listenerRegistration:
            ListenerRegistration? = null

    private val scope =
        CoroutineScope(Dispatchers.IO)

    // ---------------------------------------------------------
    // ROOM
    // ---------------------------------------------------------

    /**
     * Observa todos os responsáveis salvos localmente.
     */
    fun observarTodos():
            Flow<List<ResponsavelEntity>> {

        return responsavelDao.getAll()
    }

    /**
     * Observa um responsável específico no Room.
     *
     * A UI continua observando o Room, enquanto o Firebase
     * atualiza o cache quando necessário.
     */
    fun observarPorId(
        id: String
    ): Flow<ResponsavelEntity?> {

        return responsavelDao.getById(id)
    }

    // ---------------------------------------------------------
    // FIREBASE → ROOM EM TEMPO REAL
    // ---------------------------------------------------------

    /**
     * Inicia uma escuta em tempo real no Firestore.
     *
     * Sempre que houver alteração na coleção "responsaveis",
     * os dados são atualizados no Room.
     */
    fun iniciarSincronizacao() {

        // Evita criar mais de um listener.
        listenerRegistration?.remove()

        listenerRegistration =
            collection.addSnapshotListener { snapshot, error ->

                if (
                    error != null ||
                    snapshot == null
                ) {
                    return@addSnapshotListener
                }

                scope.launch {

                    val itens =
                        snapshot.documents.mapNotNull { doc ->

                            doc.toObject(
                                ResponsavelEntity::class.java
                            )?.copy(
                                id = doc.id,
                                lastUpdated =
                                    System.currentTimeMillis()
                            )
                        }

                    if (itens.isNotEmpty()) {

                        responsavelDao.upsertAll(
                            itens
                        )
                    }
                }
            }
    }

    // ---------------------------------------------------------
    // PARAR SINCRONIZAÇÃO
    // ---------------------------------------------------------

    /**
     * Remove o listener do Firestore.
     */
    fun pararSincronizacao() {

        listenerRegistration?.remove()

        listenerRegistration = null
    }

    // ---------------------------------------------------------
    // SINCRONIZAÇÃO ÚNICA
    // ---------------------------------------------------------

    /**
     * Baixa todos os responsáveis do Firestore
     * e salva no Room.
     *
     * Normalmente não é necessário para o login.
     * Para login, prefira sincronizarPorId().
     */
    suspend fun sincronizarUmaVez() {

        val snapshot =
            collection
                .get()
                .await()

        val itens =
            snapshot.documents.mapNotNull { doc ->

                doc.toObject(
                    ResponsavelEntity::class.java
                )?.copy(
                    id = doc.id,
                    lastUpdated =
                        System.currentTimeMillis()
                )
            }

        if (itens.isNotEmpty()) {

            responsavelDao.upsertAll(
                itens
            )
        }
    }

    // ---------------------------------------------------------
    // SINCRONIZAR RESPONSÁVEL DO LOGIN
    // ---------------------------------------------------------

    /**
     * Busca somente o responsável informado pelo ID
     * no Firestore e salva no Room.
     *
     * Deve ser chamada após o login.
     */
    suspend fun sincronizarPorId(
        id: String
    ) {

        val document =
            collection
                .document(id)
                .get()
                .await()

        // Documento não encontrado.
        if (!document.exists()) {
            return
        }

        val responsavel =
            document.toObject(
                ResponsavelEntity::class.java
            )?.copy(
                id = document.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        if (responsavel != null) {

            responsavelDao.upsert(
                responsavel
            )
        }
    }

    // ---------------------------------------------------------
    // SALVAR
    // ---------------------------------------------------------

    /**
     * Salva no Firestore e depois atualiza o Room.
     */
    suspend fun salvar(
        item: ResponsavelEntity
    ) {

        val docRef =
            if (item.id.isBlank()) {

                collection.document()

            } else {

                collection.document(
                    item.id
                )
            }

        val comId =
            item.copy(
                id = docRef.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        // Primeiro salva no Firebase.
        docRef
            .set(comId)
            .await()

        // Depois atualiza o cache local.
        responsavelDao.upsert(
            comId
        )
    }

    // ---------------------------------------------------------
    // EXCLUIR
    // ---------------------------------------------------------

    /**
     * Remove do Firestore e também do Room.
     */
    suspend fun excluir(
        id: String
    ) {

        collection
            .document(id)
            .delete()
            .await()

        responsavelDao.deleteById(
            id
        )
    }

    // ---------------------------------------------------------
    // SALVAR ASYNC
    // ---------------------------------------------------------

    /**
     * Versão assíncrona do salvar().
     *
     * Mantida para as telas que já utilizam callbacks.
     */
    fun salvarAsync(
        item: ResponsavelEntity,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {

        scope.launch {

            try {

                salvar(item)

                withContext(
                    Dispatchers.Main
                ) {

                    onSuccess()
                }

            } catch (e: Exception) {

                withContext(
                    Dispatchers.Main
                ) {

                    onError(e)
                }
            }
        }
    }
}
