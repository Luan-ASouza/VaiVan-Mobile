package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.DocumentoDao
import com.example.vaivan.data.local.dao.VeiculoDao
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.models.StatusDocumento
import com.example.vaivan.domain.usecase.veiculo.VeiculoPolicy
import com.google.firebase.auth.FirebaseAuth
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

/** Cadastro bloqueado: outro veículo já usa esta placa. */
class PlacaDuplicadaException(placa: String) : Exception(
    "A placa $placa já está cadastrada em outro veículo."
)

/** Associação bloqueada: só veículo aprovado pode ir para uma rota. */
class VeiculoNaoAprovadoException : Exception(
    "Só veículos com os documentos aprovados podem ser associados a uma rota."
)

class VeiculoRepository(
    private val veiculoDao: VeiculoDao,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance(),
    // Opcional: só quem aciona excluirVeiculo precisa passar, pra limpeza em
    // cascata dos documentos também refletir no Room na hora.
    private val documentoDao: DocumentoDao? = null
) {

    companion object {
        // Valores de VeiculoEntity.status (regras em VeiculoPolicy)
        const val STATUS_PENDENTE = "PENDENTE"
        const val STATUS_EM_ANALISE = "EM_ANALISE"
        const val STATUS_APROVADO = "APROVADO"   // só o painel grava
        const val STATUS_REJEITADO = "REJEITADO" // só o painel grava
    }

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


    /** Consulta no Firebase se a placa já está em outro veículo (de qualquer motorista).
     * Compara também a forma com hífen, de cadastros antigos. */
    suspend fun consultarPlacaEmUso(placa: String, ignorarVeiculoId: String?): Boolean {
        val normalizada = VeiculoPolicy.normalizarPlaca(placa)
        val variantes = listOf(normalizada, normalizada.take(3) + "-" + normalizada.drop(3))
        val snapshot = collection.whereIn("placa", variantes).get().await()
        return snapshot.documents.any { it.id != ignorarVeiculoId }
    }

    /** Veículo do motorista apto a ir para uma rota (consulta o Firebase, não o cache). */
    suspend fun exigirVeiculoAprovado(veiculoId: String, motoristaId: String) {
        val veiculo = consultarVeiculo(veiculoId)
        if (veiculo == null || veiculo.motoristaId != motoristaId ||
            !VeiculoPolicy.podeSerAssociadoARota(veiculo)
        ) {
            throw VeiculoNaoAprovadoException()
        }
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

    /** Sincronização pontual (pull-to-refresh) só dos veículos de um
     * motorista — mesma filtragem/limpeza de [iniciarSincronizacao], mas sem
     * depender de um listener contínuo estar ativo nesse momento. */
    suspend fun sincronizarVeiculosDoMotoristaUmaVez(motoristaId: String) {
        val snapshot = collection
            .whereEqualTo("motoristaId", motoristaId)
            .get()
            .await()

        val veiculos = snapshot.documents.mapNotNull { document ->
            document.toObject(VeiculoEntity::class.java)
                ?.copy(id = document.id, lastUpdated = System.currentTimeMillis())
        }

        val idsFirebase = veiculos.map { it.id }.toSet()
        val idsRoom = veiculoDao.getIdsByMotoristaId(motoristaId)
        (idsRoom - idsFirebase).forEach { id -> veiculoDao.deleteById(id) }

        veiculoDao.upsertAll(veiculos)
    }

    // =========================================================
    // ESCRITA
    // =========================================================

    /**
     * Salva o veículo no Firebase e atualiza o Room.
     *  - Bloqueia placa repetida ([PlacaDuplicadaException]);
     *  - Veículo novo nasce PENDENTE;
     *  - Edição de dado crítico (ver [VeiculoPolicy.alterouDadosCriticos]) devolve o
     *    veículo a PENDENTE e manda os documentos já aprovados de volta à análise.
     */
    suspend fun salvarVeiculo(
        veiculo: VeiculoEntity
    ): String {

        val placa = VeiculoPolicy.normalizarPlaca(veiculo.placa)
        val idExistente = veiculo.id.takeIf { it.isNotBlank() }

        if (consultarPlacaEmUso(placa, idExistente)) {
            throw PlacaDuplicadaException(placa)
        }

        val documentReference =
            if (idExistente == null) {
                collection.document()
            } else {
                collection.document(idExistente)
            }

        val anterior = idExistente?.let { consultarVeiculo(it) }
        val alterouCritico = anterior != null &&
            VeiculoPolicy.alterouDadosCriticos(anterior, veiculo)

        val status = when {
            anterior == null -> STATUS_PENDENTE
            alterouCritico -> STATUS_PENDENTE
            else -> anterior.status
        }

        val veiculoSalvo =
            veiculo.copy(
                id = documentReference.id,
                placa = placa,
                status = status,
                lastUpdated =
                    System.currentTimeMillis()
            )

        val batch = firestore.batch()
        batch.set(documentReference, veiculoSalvo)

        if (alterouCritico) {
            documentosAprovadosDoVeiculo(documentReference.id, veiculo.motoristaId).forEach { doc ->
                batch.update(
                    doc,
                    mapOf(
                        "statusValidacao" to StatusDocumento.EM_ANALISE.name,
                        "motivoRejeicao" to null,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                )
            }
        }

        batch.commit().await()

        veiculoDao.upsert(
            veiculoSalvo
        )

        return veiculoSalvo.id
    }

    /**
     * Depois do envio de CRLV/autorização: recalcula o status do veículo a partir dos
     * documentos (ver [VeiculoPolicy.statusAposEnvio]). Aprovar é sempre do painel.
     */
    suspend fun atualizarStatusPelosDocumentos(veiculoId: String, motoristaId: String) {
        val documentos = collectionDocumentos
            .whereEqualTo("motoristaId", motoristaId)
            .whereEqualTo("veiculoId", veiculoId)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val tipo = doc.getString("tipo") ?: return@mapNotNull null
                tipo to StatusDocumento.fromString(doc.getString("statusValidacao"))
            }
            .toMap()

        val novo = VeiculoPolicy.statusAposEnvio(documentos) ?: return
        val atual = consultarVeiculo(veiculoId) ?: return
        if (atual.status == novo) return

        val agora = System.currentTimeMillis()
        collection.document(veiculoId)
            .update(mapOf("status" to novo, "lastUpdated" to agora))
            .await()
        veiculoDao.upsert(atual.copy(status = novo, lastUpdated = agora))
    }

    private suspend fun documentosAprovadosDoVeiculo(veiculoId: String, motoristaId: String) =
        collectionDocumentos
            .whereEqualTo("motoristaId", motoristaId)
            .whereEqualTo("veiculoId", veiculoId)
            .get()
            .await()
            .documents
            .filter {
                StatusDocumento.fromString(it.getString("statusValidacao")) == StatusDocumento.APROVADO
            }
            .map { it.reference }


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

        // O filtro por motoristaId é obrigatório: a regra de leitura de
        // "documentos" só libera o dono, e o Firestore recusa
        // (PERMISSION_DENIED) uma consulta só por veiculoId — era o que
        // impedia a exclusão do veículo.
        val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("Usuário não autenticado.")

        val documentos = collectionDocumentos
            .whereEqualTo("motoristaId", motoristaId)
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