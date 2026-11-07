package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.DocumentoDao
import com.example.vaivan.data.local.dao.VeiculoDao
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
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
/** Exclusão bloqueada: o veículo está vinculado a uma ou mais rotas. */
class VeiculoVinculadoARotaException : Exception(
    "Este veículo está vinculado a uma rota. Troque ou remova o veículo da rota antes de excluí-lo."
)

class VeiculoRepository(
    private val veiculoDao: VeiculoDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance(),
    // Opcional: só quem aciona excluirVeiculo precisa passar, pra limpeza em
    // cascata dos documentos também refletir no Room na hora.
    private val documentoDao: DocumentoDao? = null
) {

    private val collection =
        firestore.collection("veiculos")

    private val collectionRotas =
        firestore.collection("rotas")

    private val collectionDocumentos =
        firestore.collection("documentos")

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )


    // =========================================================
    // OBSERVAÇÃO
    // =========================================================

    /** Observa todos os veículos armazenados no Room. */
    fun observarVeiculos():
            Flow<List<VeiculoEntity>> {

        return veiculoDao.getAll()
    }

    /** Observa um veículo específico pelo ID no Room. */
    fun observarVeiculoPorId(
        id: String
    ): Flow<VeiculoEntity?> {

        return veiculoDao.getById(id)
    }

    /** Observa os veículos de um motorista específico no Room. */
    fun observarVeiculosPorMotorista(
        motoristaId: String
    ): Flow<List<VeiculoEntity>> {

        return veiculoDao.getByMotoristaId(
            motoristaId
        )
    }


    // =========================================================
    // CONSULTA
    // =========================================================

    /** Consulta diretamente no Firebase todos os veículos de um motorista
     * (usado pela exclusão de conta — não pode confiar no cache do Room). */
    suspend fun consultarVeiculosDoMotorista(motoristaId: String): List<VeiculoEntity> {
        val snapshot = collection.whereEqualTo("motoristaId", motoristaId).get().await()
        return snapshot.documents.mapNotNull { documento ->
            documento.toObject(VeiculoEntity::class.java)?.copy(id = documento.id)
        }
    }

    /** Consulta um veículo diretamente no Firebase. */
    suspend fun consultarVeiculo(
        id: String
    ): VeiculoEntity? {

        val document =
            collection
                .document(id)
                .get()
                .await()

        if (!document.exists()) {
            return null
        }

        return document
            .toObject(VeiculoEntity::class.java)
            ?.copy(
                id = document.id
            )
    }


    // =========================================================
    // SINCRONIZAÇÃO
    // =========================================================

    /** Inicia a sincronização de todos os veículos do motorista com o Room. */
    fun iniciarSincronizacao(
        motoristaId: String
    ): ListenerRegistration {

        return collection
            .whereEqualTo(
                "motoristaId",
                motoristaId
            )
            .addSnapshotListener { snapshot, error ->

                if (
                    error != null ||
                    snapshot == null
                ) {
                    return@addSnapshotListener
                }

                scope.launch {

                    // Veículos que atualmente existem no Firebase
                    val veiculos =
                        snapshot.documents.mapNotNull { document ->

                            document
                                .toObject(
                                    VeiculoEntity::class.java
                                )
                                ?.copy(
                                    id = document.id,
                                    lastUpdated =
                                        System.currentTimeMillis()
                                )
                        }

                    // IDs que existem no Firebase
                    val idsFirebase =
                        veiculos
                            .map { it.id }
                            .toSet()

                    // IDs que existem atualmente no Room
                    val idsRoom =
                        veiculoDao
                            .getIdsByMotoristaId(
                                motoristaId
                            )

                    // O que está no Room mas não está mais no Firebase
                    val idsRemovidos =
                        idsRoom - idsFirebase

                    // Remove do Room
                    idsRemovidos.forEach { id ->

                        veiculoDao.deleteById(
                            id
                        )
                    }

                    // Insere/atualiza os veículos existentes
                    veiculoDao.upsertAll(
                        veiculos
                    )
                }
            }
    }


    // =========================================================
    // SINCRONIZAÇÃO MANUAL
    // =========================================================

    /** Sincroniza todos os veículos uma única vez. */
    suspend fun sincronizarUmaVez() {

        val snapshot =
            collection
                .get()
                .await()

        val veiculos =
            snapshot.documents.mapNotNull { document ->

                document
                    .toObject(
                        VeiculoEntity::class.java
                    )
                    ?.copy(
                        id = document.id,
                        lastUpdated =
                            System.currentTimeMillis()
                    )
            }

        veiculoDao.upsertAll(
            veiculos
        )
    }

    // =========================================================
    // ESCRITA
    // =========================================================

    /** Salva o veículo no Firebase e atualiza o Room. */
    suspend fun salvarVeiculo(
        veiculo: VeiculoEntity
    ): String {

        val documentReference =
            if (veiculo.id.isBlank()) {
                collection.document()
            } else {
                collection.document(
                    veiculo.id
                )
            }

        val veiculoSalvo =
            veiculo.copy(
                id = documentReference.id,
                lastUpdated =
                    System.currentTimeMillis()
            )

        documentReference
            .set(veiculoSalvo)
            .await()

        veiculoDao.upsert(
            veiculoSalvo
        )

        return veiculoSalvo.id
    }


    // =========================================================
    // EXCLUSÃO
    // =========================================================

    /** Exclui o veículo do Firebase e do Room. Bloqueia (lança
     * [VeiculoVinculadoARotaException]) se alguma rota ainda estiver
     * vinculada a ele — precisa trocar/desvincular o veículo da rota
     * primeiro. Sem bloqueio, cascateia pros documentos (CRLV/autorização)
     * desse veículo, Firestore + Room + arquivos no Storage. */
    suspend fun excluirVeiculo(
        id: String
    ) {
        val rotasVinculadas = collectionRotas
            .whereEqualTo("veiculoId", id)
            .limit(1)
            .get()
            .await()

        if (!rotasVinculadas.isEmpty) {
            throw VeiculoVinculadoARotaException()
        }

        val documentos = collectionDocumentos
            .whereEqualTo("veiculoId", id)
            .get()
            .await()

        val batch = firestore.batch()
        documentos.documents.forEach { batch.delete(it.reference) }
        batch.delete(collection.document(id))
        batch.commit().await()

        documentoDao?.deleteByVeiculo(id)
        veiculoDao.deleteById(id)

        runCatching {
            val pasta = FirebaseStorage.getInstance().reference.child("documentos_veiculos/$id")
            pasta.listAll().await().items.forEach { it.delete().await() }
        }
    }
}