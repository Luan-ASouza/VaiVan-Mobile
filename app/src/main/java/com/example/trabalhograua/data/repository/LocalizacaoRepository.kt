package com.example.trabalhograua.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.trabalhograua.data.local.dao.LocalizacaoDao
import com.example.trabalhograua.data.local.entities.LocalizacaoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Segue o mesmo padrão de MotoristaRepository: Firestore = fonte da
 * verdade, Room (LocalizacaoDao) = cache local que a UI observa.
 */
class LocalizacaoRepository(
    private val localizacaoDao: LocalizacaoDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val collection = firestore.collection("localizacoes")
    private var listenerRegistration: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun observarTodos(): Flow<List<LocalizacaoEntity>> = localizacaoDao.getAll()

    fun observarPorId(id: String): Flow<LocalizacaoEntity?> = localizacaoDao.getById(id)

    fun iniciarSincronizacao() {
        listenerRegistration?.remove()
        listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                val itens = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(LocalizacaoEntity::class.java)?.copy(
                        id = doc.id,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                localizacaoDao.upsertAll(itens)
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
            doc.toObject(LocalizacaoEntity::class.java)?.copy(
                id = doc.id,
                lastUpdated = System.currentTimeMillis()
            )
        }
        localizacaoDao.upsertAll(itens)
    }

    suspend fun salvar(item: LocalizacaoEntity) {
        val docRef = if (item.id.isBlank()) {
            collection.document()
        } else {
            collection.document(item.id)
        }
        val comId = item.copy(id = docRef.id, lastUpdated = System.currentTimeMillis())
        docRef.set(comId).await()
        localizacaoDao.upsert(comId)
    }

    suspend fun excluir(id: String) {
        collection.document(id).delete().await()
        localizacaoDao.deleteById(id)
    }
}
