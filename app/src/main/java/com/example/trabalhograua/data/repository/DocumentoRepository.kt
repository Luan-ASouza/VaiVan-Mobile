package com.example.trabalhograua.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.trabalhograua.data.local.dao.DocumentoDao
import com.example.trabalhograua.data.local.entities.DocumentoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Segue o mesmo padrão de MotoristaRepository: Firestore = fonte da
 * verdade, Room (DocumentoDao) = cache local que a UI observa.
 */
class DocumentoRepository(
    private val documentoDao: DocumentoDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val collection = firestore.collection("documentos")
    private var listenerRegistration: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun observarTodos(): Flow<List<DocumentoEntity>> = documentoDao.getAll()

    fun observarPorId(id: String): Flow<DocumentoEntity?> = documentoDao.getById(id)

    fun iniciarSincronizacao() {
        listenerRegistration?.remove()
        listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                val itens = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DocumentoEntity::class.java)?.copy(
                        id = doc.id,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                documentoDao.upsertAll(itens)
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
            doc.toObject(DocumentoEntity::class.java)?.copy(
                id = doc.id,
                lastUpdated = System.currentTimeMillis()
            )
        }
        documentoDao.upsertAll(itens)
    }

    suspend fun salvar(item: DocumentoEntity) {
        val docRef = if (item.id.isBlank()) {
            collection.document()
        } else {
            collection.document(item.id)
        }
        val comId = item.copy(id = docRef.id, lastUpdated = System.currentTimeMillis())
        docRef.set(comId).await()
        documentoDao.upsert(comId)
    }

    suspend fun excluir(id: String) {
        collection.document(id).delete().await()
        documentoDao.deleteById(id)
    }
}
