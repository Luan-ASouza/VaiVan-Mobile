package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.LocalizacaoDao
import com.example.vaivan.data.local.entities.LocalizacaoEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Firestore = fonte da verdade
 * Room = cache local observado pela UI
 */
class LocalizacaoRepository(
    private val localizacaoDao: LocalizacaoDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private val collection =
        firestore.collection("localizacoes")

    private var listenerRegistration:
            ListenerRegistration? = null

    private val scope =
        CoroutineScope(Dispatchers.IO)


    // =========================================================
    // OBSERVAÇÃO DO ROOM
    // =========================================================

    fun observarTodasLocalizacoes():
            Flow<List<LocalizacaoEntity>> {

        return localizacaoDao.getAll()
    }

    fun observarLocalizacaoPorId(
        id: String
    ): Flow<LocalizacaoEntity?> {

        return localizacaoDao.getById(id)
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

                    val localizacoes =
                        snapshot.documents.mapNotNull { document ->

                            document
                                .toObject(
                                    LocalizacaoEntity::class.java
                                )
                                ?.copy(
                                    id = document.id,
                                    lastUpdated =
                                        System.currentTimeMillis()
                                )
                        }

                    localizacaoDao.upsertAll(
                        localizacoes
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

        val localizacoes =
            snapshot.documents.mapNotNull { document ->

                document
                    .toObject(
                        LocalizacaoEntity::class.java
                    )
                    ?.copy(
                        id = document.id,
                        lastUpdated =
                            System.currentTimeMillis()
                    )
            }

        localizacaoDao.upsertAll(
            localizacoes
        )
    }


    // =========================================================
    // SALVAR FIREBASE + ROOM
    // =========================================================

    suspend fun salvarLocalizacao(
        localizacao: LocalizacaoEntity
    ): String {

        val documentReference =
            if (localizacao.id.isBlank()) {
                collection.document()
            } else {
                collection.document(
                    localizacao.id
                )
            }

        val localizacaoComId =
            localizacao.copy(
                id = documentReference.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        // Firebase
        documentReference
            .set(localizacaoComId)
            .await()

        // Room
        localizacaoDao.upsert(
            localizacaoComId
        )

        return localizacaoComId.id
    }


    // =========================================================
    // EXCLUIR FIREBASE + ROOM
    // =========================================================

    suspend fun excluirLocalizacao(
        id: String
    ) {

        // Firebase
        collection
            .document(id)
            .delete()
            .await()

        // Room
        localizacaoDao.deleteById(id)
    }
}