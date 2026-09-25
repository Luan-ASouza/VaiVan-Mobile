package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.PontoEmbarqueDao
import com.example.vaivan.data.local.entities.PontoDeEmbarqueEntity
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
 * observar           → Room → Flow
 * consultar          → Firebase, consulta pontual
 * iniciarSincronizacao → Firebase → Room, mantendo os dados atualizados
 * salvar             → Firebase + Room
 * excluir            → Firebase + Room
 *
 * O Repository executa as operações de dados.
 * O SyncManager controla o ciclo de vida dos listeners.
 */
class PontoEmbarqueRepository(
    private val pontoEmbarqueDao: PontoEmbarqueDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private val collection =
        firestore.collection("ponto_embarque")

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )


    // =========================================================
    // OBSERVAÇÃO
    // =========================================================

    /**
     * Observa todos os pontos de embarque do usuário
     * armazenados no Room.
     */
    fun observarPontosDeEmbarqueDoUsuario(
        usuarioId: String
    ): Flow<List<PontoDeEmbarqueEntity>> {

        return pontoEmbarqueDao.getByResponsavel(
            usuarioId
        )
    }

    /**
     * Observa um ponto de embarque específico pelo ID
     * no Room.
     */
    fun observarPontoDeEmbarquePorId(
        id: String
    ): Flow<PontoDeEmbarqueEntity?> {

        return pontoEmbarqueDao.getById(id)
    }


    // =========================================================
    // CONSULTA
    // =========================================================

    /**
     * Consulta um ponto de embarque diretamente no Firebase.
     */
    suspend fun consultarPontoDeEmbarque(
        id: String
    ): PontoDeEmbarqueEntity? {

        val document =
            collection
                .document(id)
                .get()
                .await()

        if (!document.exists()) {
            return null
        }

        return document
            .toObject(
                PontoDeEmbarqueEntity::class.java
            )
            ?.copy(
                id = document.id
            )
    }


    // =========================================================
    // SINCRONIZAÇÃO
    // =========================================================

    /**
     * Inicia a sincronização dos pontos de embarque
     * do usuário com o Room.
     */
    fun iniciarSincronizacaoDosPontosDeEmbarque(
        usuarioId: String
    ): ListenerRegistration {

        return collection
            .whereEqualTo(
                "usuarioId",
                usuarioId
            )
            .addSnapshotListener { snapshot, error ->

                if (
                    error != null ||
                    snapshot == null
                ) {
                    return@addSnapshotListener
                }

                scope.launch {

                    val pontosDeEmbarque =
                        snapshot.documents.mapNotNull { document ->

                            document
                                .toObject(
                                    PontoDeEmbarqueEntity::class.java
                                )
                                ?.copy(
                                    id = document.id,
                                    lastUpdated =
                                        System.currentTimeMillis()
                                )
                        }

                    pontoEmbarqueDao.upsertAll(
                        pontosDeEmbarque
                    )
                }
            }
    }


    // =========================================================
    // SINCRONIZAÇÃO MANUAL
    // =========================================================

    /**
     * Sincroniza todos os pontos de embarque
     * uma única vez.
     */
    suspend fun sincronizarPontosDeEmbarqueUmaVez() {

        val snapshot =
            collection
                .get()
                .await()

        val pontosDeEmbarque =
            snapshot.documents.mapNotNull { document ->

                document
                    .toObject(
                        PontoDeEmbarqueEntity::class.java
                    )
                    ?.copy(
                        id = document.id,
                        lastUpdated =
                            System.currentTimeMillis()
                    )
            }

        pontoEmbarqueDao.upsertAll(
            pontosDeEmbarque
        )
    }


    // =========================================================
    // ESCRITA
    // =========================================================

    /**
     * Salva o ponto de embarque no Firebase
     * e atualiza o Room.
     */
    suspend fun salvarPontoDeEmbarque(
        pontoDeEmbarque: PontoDeEmbarqueEntity
    ): String {

        val documentReference =
            if (pontoDeEmbarque.id.isBlank()) {

                collection.document()

            } else {

                collection.document(
                    pontoDeEmbarque.id
                )
            }

        val pontoDeEmbarqueSalvo =
            pontoDeEmbarque.copy(
                id = documentReference.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        documentReference
            .set(pontoDeEmbarqueSalvo)
            .await()

        pontoEmbarqueDao.upsert(
            pontoDeEmbarqueSalvo
        )

        return pontoDeEmbarqueSalvo.id
    }


    // =========================================================
    // EXCLUSÃO
    // =========================================================

    /**
     * Exclui o ponto de embarque do Firebase
     * e do Room.
     */
    suspend fun excluirPontoDeEmbarque(
        id: String
    ) {

        collection
            .document(id)
            .delete()
            .await()

        pontoEmbarqueDao.deleteById(id)
    }
}