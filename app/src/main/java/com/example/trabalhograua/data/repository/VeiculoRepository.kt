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
import kotlinx.coroutines.withContext

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

    suspend fun salvarVeiculo(veiculo: VeiculoEntity): String {
        val docRef = if (veiculo.id.isBlank()) {
            collection.document()
        } else {
            collection.document(veiculo.id)
        }
        val comId = veiculo.copy(id = docRef.id, lastUpdated = System.currentTimeMillis())
        docRef.set(comId).await()
        veiculoDao.upsert(comId)
        return comId.id
    }

    /** Wrapper com callback pra ser chamado a partir de código Java (Activities). */
    fun salvarAsync(
        veiculo: VeiculoEntity,
        aoSucesso: (String) -> Unit,
        aoErro: (Exception) -> Unit
    ) {
        scope.launch {
            try {
                val idGerado = salvarVeiculo(veiculo)
                withContext(Dispatchers.Main) { aoSucesso(idGerado) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { aoErro(e) }
            }
        }
    }

    suspend fun excluirVeiculo(id: String) {
        collection.document(id).delete().await()
        veiculoDao.deleteById(id)
    }
}