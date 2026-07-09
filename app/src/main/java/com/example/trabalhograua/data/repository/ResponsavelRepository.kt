package com.example.trabalhograua.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.trabalhograua.data.local.dao.ResponsavelDao
import com.example.trabalhograua.data.local.entities.ResponsavelEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Segue o mesmo padrão de MotoristaRepository: Firestore = fonte da
 * verdade, Room (ResponsavelDao) = cache local que a UI observa.
 */
class ResponsavelRepository(
    private val responsavelDao: ResponsavelDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val collection = firestore.collection("responsaveis")
    private var listenerRegistration: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun observarTodos(): Flow<List<ResponsavelEntity>> = responsavelDao.getAll()

    fun observarPorId(id: String): Flow<ResponsavelEntity?> = responsavelDao.getById(id)

    fun iniciarSincronizacao() {
        listenerRegistration?.remove()
        listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                val itens = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ResponsavelEntity::class.java)?.copy(
                        id = doc.id,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                responsavelDao.upsertAll(itens)
            }
        }
    }

    fun pararSincronizacao() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    suspend fun sincronizarUmaVez() {
        val snapshot = collection.get().await()
        val itens = snapshot.documents.mapNotNull { doc ->
            doc.toObject(ResponsavelEntity::class.java)?.copy(
                id = doc.id,
                lastUpdated = System.currentTimeMillis()
            )
        }
        responsavelDao.upsertAll(itens)
    }

    suspend fun salvar(item: ResponsavelEntity) {
        val docRef = if (item.id.isBlank()) {
            collection.document()
        } else {
            collection.document(item.id)
        }
        val comId = item.copy(id = docRef.id, lastUpdated = System.currentTimeMillis())
        docRef.set(comId).await()
        responsavelDao.upsert(comId)
    }

    suspend fun excluir(id: String) {
        collection.document(id).delete().await()
        responsavelDao.deleteById(id)
    }

    fun salvarAsync(
        item: ResponsavelEntity,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        scope.launch {
            try {
                salvar(item)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
}
