package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.UsuarioDao
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Firestore = fonte da verdade
 * Room = cache local observado pela UI
 *
 * Nomenclatura:
 * observar  → Room → Flow
 * consultar → Firebase, consulta pontual
 * sincronizar → Firebase → Room, mantendo os dados atualizados
 * salvar    → Firebase + Room
 * excluir   → Firebase + Room
 *
 * O Repository executa as operações de dados.
 * O SyncManager controla o ciclo de vida dos listeners.
 */
class UsuarioRepository(
    private val usuarioDao: UsuarioDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private val collection =
        firestore.collection("usuarios")

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )


    // =========================================================
    // OBSERVAÇÃO
    // =========================================================

    /** Observa um usuário específico pelo ID no Room. */
    fun observarUsuario(
        id: String
    ): Flow<UsuarioEntity?> {

        return usuarioDao.getById(id)
    }


    // =========================================================
    // CONSULTA
    // =========================================================

    /** Consulta um usuário diretamente no Firebase. */
    suspend fun consultarUsuario(
        id: String
    ): UsuarioEntity? {

        val document =
            collection
                .document(id)
                .get()
                .await()

        if (!document.exists()) {
            return null
        }

        return document
            .toObject(UsuarioEntity::class.java)
            ?.copy(
                id = document.id
            )
    }


    // =========================================================
    // SINCRONIZAÇÃO
    // =========================================================

    /** Inicia a sincronização de um usuário específico com o Room. */
    fun iniciarSincronizacaoPorId(
        id: String
    ): ListenerRegistration {

        return collection
            .document(id)
            .addSnapshotListener { snapshot, error ->

                if (
                    error != null ||
                    snapshot == null
                ) {
                    return@addSnapshotListener
                }

                scope.launch {

                    if (!snapshot.exists()) {
                        usuarioDao.deleteById(id)
                        return@launch
                    }

                    val usuario =
                        snapshot
                            .toObject(
                                UsuarioEntity::class.java
                            )
                            ?.copy(
                                id = snapshot.id,
                                lastUpdated =
                                    System.currentTimeMillis()
                            )

                    if (usuario != null) {
                        usuarioDao.upsert(usuario)
                    }
                }
            }
    }


    // =========================================================
    // ESCRITA
    // =========================================================

    /** Salva o usuário no Firebase e atualiza o Room. */
    suspend fun salvarUsuario(
        usuario: UsuarioEntity
    ): String {

        val documentReference =
            if (usuario.id.isBlank()) {
                collection.document()
            } else {
                collection.document(usuario.id)
            }

        val usuarioSalvo =
            usuario.copy(
                id = documentReference.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        documentReference
            .set(usuarioSalvo)
            .await()

        usuarioDao.upsert(
            usuarioSalvo
        )

        return usuarioSalvo.id
    }


    // =========================================================
    // EXCLUSÃO
    // =========================================================

    /** Exclui o usuário do Firebase e do Room. */
    suspend fun excluirUsuario(
        id: String
    ) {

        collection
            .document(id)
            .delete()
            .await()

        usuarioDao.deleteById(id)
    }
}