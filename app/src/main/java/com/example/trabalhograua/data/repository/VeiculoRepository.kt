package com.example.trabalhograua.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.trabalhograua.data.local.dao.VeiculoDao
import com.example.trabalhograua.data.local.entities.VeiculoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Segue o mesmo padrão de MotoristaRepository/DocumentoRepository:
 * Firestore = fonte da verdade, Room (VeiculoDao) = cache local que a UI observa.
 */
class VeiculoRepository(
    private val veiculoDao: VeiculoDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val collection = firestore.collection("veiculos")
    private var listenerRegistration: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun observarVeiculos(): Flow<List<VeiculoEntity>> = veiculoDao.getAll()

    fun observarVeiculo(id: String): Flow<VeiculoEntity?> = veiculoDao.getById(id)

    fun observarPorMotorista(motoristaId: String): Flow<List<VeiculoEntity>> =
        veiculoDao.getByMotoristaId(motoristaId)

    fun iniciarSincronizacao() {
        listenerRegistration?.remove()
        listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                val itens = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(VeiculoEntity::class.java)?.copy(
                        id = doc.id,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                veiculoDao.upsertAll(itens)
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
            doc.toObject(VeiculoEntity::class.java)?.copy(
                id = doc.id,
                lastUpdated = System.currentTimeMillis()
            )
        }
        veiculoDao.upsertAll(itens)
    }

    /** Escrita: vai pro Firestore, e ao confirmar, atualiza o cache local também. */
    suspend fun salvarVeiculo(veiculo: VeiculoEntity) {
        val docRef = if (veiculo.id.isBlank()) {
            collection.document()
        } else {
            collection.document(veiculo.id)
        }
        val comId = veiculo.copy(id = docRef.id, lastUpdated = System.currentTimeMillis())
        docRef.set(comId).await()
        veiculoDao.upsert(comId)
    }

    suspend fun salvar(item: VeiculoEntity): String {
        val docRef = if (item.id.isBlank()) {
            collection.document()
        } else {
            collection.document(item.id)
        }

        val comId = item.copy(
            id = docRef.id,
            lastUpdated = System.currentTimeMillis()
        )

        docRef.set(comId).await()
        veiculoDao.upsert(comId)

        return docRef.id
    }

    fun salvarAsync(
        item: VeiculoEntity,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        scope.launch {
            try {
                salvar(item)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onSuccess(item.id)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    suspend fun excluirVeiculo(id: String) {
        collection.document(id).delete().await()
        veiculoDao.deleteById(id)
    }
}
