package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.PassageiroDao
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/*
 * Nomenclatura:
 *
 * observar  → Room → Flow
 * consultar → Firebase, consulta pontual
 * sincronizar → Firebase → Room, mantendo os dados atualizados
 * salvar    → Firebase + Room
 * excluir   → Firebase + Room
 *
 * O Firebase é a fonte de verdade dos dados compartilhados.
 * O Room funciona como cache local observado pela UI.
 *
 * O Repository executa as operações de dados.
 * O SyncManager controla o ciclo de vida dos listeners.
 */

class PassageiroRepository(
    private val passageiroDao: PassageiroDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private val collection =
        firestore.collection("passageiros")

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )


    // =========================================================
    // OBSERVAÇÃO
    // =========================================================

    /** Observa todos os passageiros armazenados no Room. */
    fun observarPassageiros():
            Flow<List<PassageiroEntity>> {

        return passageiroDao.getAll()
    }

    /** Observa um passageiro específico pelo ID no Room. */
    fun observarPassageiro(
        id: String
    ): Flow<PassageiroEntity?> {

        return passageiroDao.getById(id)
    }

    /** Observa os passageiros de um responsável no Room. */
    fun observarPassageirosDoResponsavel(
        responsavelId: String
    ): Flow<List<PassageiroEntity>> {

        return passageiroDao.getByResponsavel(
            responsavelId
        )
    }

    /** Observa os passageiros de uma rota no Room. */
    fun observarPassageirosDaRota(
        rotaId: String
    ): Flow<List<PassageiroEntity>> {

        return passageiroDao.getByRota(
            rotaId
        )
    }


    // =========================================================
    // CONSULTAS
    // =========================================================

    /** Consulta um passageiro diretamente no Firebase. */
    suspend fun consultarPassageiro(
        id: String
    ): PassageiroEntity? {

        val document =
            collection
                .document(id)
                .get()
                .await()

        if (!document.exists()) {
            return null
        }

        return document
            .toObject(PassageiroEntity::class.java)
            ?.copy(
                id = document.id
            )
    }

    /** Consulta no Firebase os passageiros de um responsável. */
    suspend fun consultarPassageirosDoResponsavel(
        responsavelId: String
    ): List<PassageiroEntity> {

        val snapshot =
            collection
                .whereEqualTo(
                    "responsavelId",
                    responsavelId
                )
                .get()
                .await()

        return snapshot.documents.mapNotNull { document ->
            document
                .toObject(PassageiroEntity::class.java)
                ?.copy(
                    id = document.id
                )
        }
    }


    // =========================================================
    // SINCRONIZAÇÃO
    // =========================================================

    /** Inicia a sincronização dos passageiros de um responsável. */
    fun iniciarSincronizacaoDosPassageirosDoResponsavel(
        responsavelId: String
    ): ListenerRegistration {

        return collection
            .whereEqualTo(
                "responsavelId",
                responsavelId
            )
            .addSnapshotListener { snapshot, error ->

                if (
                    error != null ||
                    snapshot == null
                ) {
                    return@addSnapshotListener
                }

                scope.launch {
                    sincronizarSnapshot(snapshot)
                }
            }
    }

    /** Aplica no Room somente as alterações recebidas do Firebase. */
    private suspend fun sincronizarSnapshot(
        snapshot: QuerySnapshot
    ) {

        for (change in snapshot.documentChanges) {

            val document = change.document
            val passageiroId = document.id

            when (change.type) {

                DocumentChange.Type.ADDED,
                DocumentChange.Type.MODIFIED -> {

                    val passageiro =
                        document
                            .toObject(
                                PassageiroEntity::class.java
                            )
                            .copy(
                                id = passageiroId,
                                lastUpdated =
                                    System.currentTimeMillis()
                            )

                    passageiroDao.upsert(passageiro)
                }

                DocumentChange.Type.REMOVED -> {
                    passageiroDao.deleteById(
                        passageiroId
                    )
                }
            }
        }
    }


    // =========================================================
    // ESCRITA
    // =========================================================

    /** Salva o passageiro no Firebase e atualiza o Room. */
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

        val passageiroSalvo =
            passageiro.copy(
                id = documentReference.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        documentReference
            .set(passageiroSalvo)
            .await()

        passageiroDao.upsert(
            passageiroSalvo
        )

        return passageiroSalvo.id
    }


    // =========================================================
    // EXCLUSÃO
    // =========================================================

    /** Exclui o passageiro do Firebase e do Room. */
    suspend fun excluirPassageiro(
        id: String
    ) {

        collection
            .document(id)
            .delete()
            .await()

        passageiroDao.deleteById(id)
    }
}