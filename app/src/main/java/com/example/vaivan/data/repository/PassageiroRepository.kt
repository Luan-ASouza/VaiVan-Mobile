package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.PassageiroDao
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PassageiroRepository(
    private val passageiroDao: PassageiroDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private val collection =
        firestore.collection("passageiros")

    private var listenerRegistration:
            ListenerRegistration? = null

    private val scope =
        CoroutineScope(Dispatchers.IO)


    // =========================================================
    // OBSERVAÇÃO DO ROOM
    // =========================================================

    fun observarTodosPassageiros():
            Flow<List<PassageiroEntity>> {

        return passageiroDao.getAll()
    }

    fun observarPassageiroPorId(
        id: String
    ): Flow<PassageiroEntity?> {

        return passageiroDao.getById(id)
    }

    fun observarPassageirosPorResponsavel(
        responsavelId: String
    ): Flow<List<PassageiroEntity>> {

        return passageiroDao.getByResponsavel(
            responsavelId
        )
    }

    fun observarPassageirosPorRota(
        rotaId: String
    ): Flow<List<PassageiroEntity>> {

        return passageiroDao.getByRota(
            rotaId
        )
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

                    val passageiros =
                        snapshot.documents.mapNotNull { document ->

                            document
                                .toObject(
                                    PassageiroEntity::class.java
                                )
                                ?.copy(
                                    id = document.id,
                                    lastUpdated =
                                        System.currentTimeMillis()
                                )
                        }

                    passageiroDao.upsertAll(
                        passageiros
                    )
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
            collection
                .get()
                .await()

        val passageiros =
            snapshot.documents.mapNotNull { document ->

                document
                    .toObject(
                        PassageiroEntity::class.java
                    )
                    ?.copy(
                        id = document.id,
                        lastUpdated =
                            System.currentTimeMillis()
                    )
            }

        passageiroDao.upsertAll(
            passageiros
        )
    }


    // =========================================================
    // SALVAR FIREBASE + ROOM
    // =========================================================

    suspend fun salvarPassageiro(
        passageiro: PassageiroEntity
    ): String {

        val documentReference =
            if (passageiro.id.isBlank()) {
                collection.document()
            } else {
                collection.document(
                    passageiro.id
                )
            }

        val passageiroComId =
            passageiro.copy(
                id = documentReference.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        // Firebase
        documentReference
            .set(passageiroComId)
            .await()

        // Room
        passageiroDao.upsert(
            passageiroComId
        )

        return passageiroComId.id
    }


    // =========================================================
    // EXCLUIR FIREBASE + ROOM
    // =========================================================

    suspend fun excluirPassageiro(
        id: String
    ) {

        // Firebase
        collection
            .document(id)
            .delete()
            .await()

        // Room
        passageiroDao.deleteById(id)
    }
}