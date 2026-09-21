package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.VeiculoDao
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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

    private val collection =
        firestore.collection("veiculos")

    private var listenerRegistration: ListenerRegistration? = null

    private val scope =
        CoroutineScope(Dispatchers.IO)


    // =========================================================
    // OBSERVAÇÃO DO ROOM
    // =========================================================

    fun observarVeiculos(): Flow<List<VeiculoEntity>> {
        return veiculoDao.getAll()
    }

    fun observarVeiculo(id: String): Flow<VeiculoEntity?> {
        return veiculoDao.getById(id)
    }

    fun observarPorMotorista(
        motoristaId: String
    ): Flow<List<VeiculoEntity>> {
        return veiculoDao.getByMotoristaId(motoristaId)
    }


    // =========================================================
    // SINCRONIZAÇÃO FIREBASE → ROOM
    // =========================================================

    fun iniciarSincronizacao() {

        listenerRegistration?.remove()

        listenerRegistration =
            collection.addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }

                scope.launch {

                    val veiculos =
                        snapshot.documents.mapNotNull { document ->

                            document
                                .toObject(VeiculoEntity::class.java)
                                ?.copy(
                                    id = document.id,
                                    lastUpdated =
                                        System.currentTimeMillis()
                                )
                        }

                    veiculoDao.upsertAll(veiculos)
                }
            }
    }

    fun pararSincronizacao() {

        listenerRegistration?.remove()

        listenerRegistration = null
    }


    // =========================================================
    // SINCRONIZAÇÃO MANUAL FIREBASE → ROOM
    // =========================================================

    suspend fun sincronizarUmaVez() {

        val snapshot =
            collection.get().await()

        val veiculos =
            snapshot.documents.mapNotNull { document ->

                document
                    .toObject(VeiculoEntity::class.java)
                    ?.copy(
                        id = document.id,
                        lastUpdated =
                            System.currentTimeMillis()
                    )
            }

        veiculoDao.upsertAll(veiculos)
    }


    // =========================================================
    // SALVAR FIREBASE + ROOM
    // =========================================================

    suspend fun salvarVeiculo(
        veiculo: VeiculoEntity
    ): String {

        val documentReference =
            if (veiculo.id.isBlank()) {
                collection.document()
            } else {
                collection.document(veiculo.id)
            }

        val veiculoComId =
            veiculo.copy(
                id = documentReference.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        // Firebase
        documentReference
            .set(veiculoComId)
            .await()

        // Room
        veiculoDao.upsert(veiculoComId)

        return veiculoComId.id
    }


    // =========================================================
    // WRAPPER PARA CÓDIGO JAVA
    // =========================================================

    fun salvarAsync(
        veiculo: VeiculoEntity,
        aoSucesso: (String) -> Unit,
        aoErro: (Exception) -> Unit
    ) {

        scope.launch {

            try {

                val idGerado =
                    salvarVeiculo(veiculo)

                withContext(Dispatchers.Main) {
                    aoSucesso(idGerado)
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    aoErro(e)
                }
            }
        }
    }


    // =========================================================
    // EXCLUIR FIREBASE + ROOM
    // =========================================================

    suspend fun excluirVeiculo(
        id: String
    ) {

        // Firebase
        collection
            .document(id)
            .delete()
            .await()

        // Room
        veiculoDao.deleteById(id)
    }
}