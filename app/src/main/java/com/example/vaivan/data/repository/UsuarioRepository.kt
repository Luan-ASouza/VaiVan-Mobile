package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.UsuarioDao
import com.example.vaivan.data.local.entities.UsuarioEntity
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
class UsuarioRepository(
    private val usuarioDao: UsuarioDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private val collection =
        firestore.collection("usuarios")

    private var listenerRegistration:
            ListenerRegistration? = null

    private val scope =
        CoroutineScope(Dispatchers.IO)


    // =========================================================
    // OBSERVAÇÃO DO ROOM
    // =========================================================

    fun observarUsuarioPorId(id: String): Flow<UsuarioEntity?> {
        return usuarioDao.getById(id)
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

                    val usuarios =
                        snapshot.documents.mapNotNull { document ->

                            document
                                .toObject(UsuarioEntity::class.java)
                                ?.copy(
                                    id = document.id,
                                    lastUpdated =
                                        System.currentTimeMillis()
                                )
                        }

                    usuarioDao.upsertAll(usuarios)
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

        val usuarios =
            snapshot.documents.mapNotNull { document ->

                document
                    .toObject(UsuarioEntity::class.java)
                    ?.copy(
                        id = document.id,
                        lastUpdated =
                            System.currentTimeMillis()
                    )
            }

        usuarioDao.upsertAll(usuarios)
    }


    // =========================================================
    // SINCRONIZAR USUÁRIO ESPECÍFICO
    // =========================================================

    suspend fun sincronizarUsuarioPorId(
        id: String
    ) {

        val document =
            collection
                .document(id)
                .get()
                .await()

        if (!document.exists()) {
            return
        }

        val usuario =
            document
                .toObject(UsuarioEntity::class.java)
                ?.copy(
                    id = document.id,
                    lastUpdated =
                        System.currentTimeMillis()
                )

        if (usuario != null) {
            usuarioDao.upsert(usuario)
        }
    }


    // =========================================================
    // SALVAR FIREBASE + ROOM
    // =========================================================

    suspend fun salvarUsuario(
        usuario: UsuarioEntity
    ): String {

        val documentReference =
            if (usuario.id.isBlank()) {
                collection.document()
            } else {
                collection.document(usuario.id)
            }

        val usuarioComId =
            usuario.copy(
                id = documentReference.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        // Firebase
        documentReference
            .set(usuarioComId)
            .await()

        // Room
        usuarioDao.upsert(usuarioComId)

        return usuarioComId.id
    }


    // =========================================================
    // EXCLUIR FIREBASE + ROOM
    // =========================================================

    suspend fun excluirUsuario(
        id: String
    ) {

        // Firebase
        collection
            .document(id)
            .delete()
            .await()

        // Room
        usuarioDao.deleteById(id)
    }
}