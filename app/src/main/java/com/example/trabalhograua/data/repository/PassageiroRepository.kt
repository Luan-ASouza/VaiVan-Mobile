package com.example.trabalhograua.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.trabalhograua.data.local.dao.PassageiroDao
import com.example.trabalhograua.data.local.entities.PassageiroEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PassageiroRepository(
    private val passageiroDao: PassageiroDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val collection = firestore.collection("passageiros")
    private var listenerRegistration: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun observarTodos(): Flow<List<PassageiroEntity>> = passageiroDao.getAll()

    fun observarPorId(id: String): Flow<PassageiroEntity?> = passageiroDao.getById(id)

    /** Filhos vinculados a um responsável (ex: tela "meus passageiros"). */
    fun observarPorResponsavel(responsavelId: String): Flow<List<PassageiroEntity>> =
        passageiroDao.getByResponsavel(responsavelId)

    /** Passageiros de uma rota (ex: lista de embarque do motorista). */
    fun observarPorRota(rotaId: String): Flow<List<PassageiroEntity>> =
        passageiroDao.getByRota(rotaId)

    fun iniciarSincronizacao() {
        listenerRegistration?.remove()
        listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                val itens = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(PassageiroEntity::class.java)?.copy(
                        id = doc.id,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                passageiroDao.upsertAll(itens)
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
            doc.toObject(PassageiroEntity::class.java)?.copy(
                id = doc.id,
                lastUpdated = System.currentTimeMillis()
            )
        }
        passageiroDao.upsertAll(itens)
    }

    suspend fun salvar(item: PassageiroEntity) {
        val docRef = if (item.id.isBlank()) collection.document() else collection.document(item.id)
        val comId = item.copy(id = docRef.id, lastUpdated = System.currentTimeMillis())
        docRef.set(comId).await()
        passageiroDao.upsert(comId)
    }

    suspend fun excluir(id: String) {
        collection.document(id).delete().await()
        passageiroDao.deleteById(id)
    }
}
