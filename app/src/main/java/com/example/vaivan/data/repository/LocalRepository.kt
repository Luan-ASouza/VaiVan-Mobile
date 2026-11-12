package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.LocalDao
import com.example.vaivan.data.local.entities.LocalEntity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Locais salvos do usuário.
 *
 * Firestore = fonte da verdade; Room = cache local observado pela UI.
 * observar  → Room → Flow
 * iniciarSincronizacao / pararSincronizacao → Firestore → Room (listener contínuo)
 * salvar    → Firestore + Room
 * excluir   → Firestore + Room
 */
class LocalRepository(
    private val localDao: LocalDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val collection = firestore.collection("locais")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var listener: ListenerRegistration? = null

    // =========================================================
    // OBSERVAÇÃO
    // =========================================================

    fun observarLocais(responsavelId: String): Flow<List<LocalEntity>> =
        localDao.getByResponsavel(responsavelId)

    // =========================================================
    // SINCRONIZAÇÃO
    // =========================================================

    fun iniciarSincronizacao(responsavelId: String) {
        pararSincronizacao()

        listener = collection
            .whereEqualTo("responsavelId", responsavelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val agora = System.currentTimeMillis()
                val locais = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(LocalEntity::class.java)?.copy(id = doc.id, lastUpdated = agora)
                }

                scope.launch {
                    val idsRemotos = locais.map { it.id }.toSet()
                    localDao.idsDoResponsavel(responsavelId)
                        .filterNot { it in idsRemotos }
                        .forEach { localDao.deleteById(it) }
                    localDao.upsertAll(locais)
                }
            }
    }

    fun pararSincronizacao() {
        listener?.remove()
        listener = null
    }

    // =========================================================
    // ESCRITA
    // =========================================================

    /** Cria (id vazio) ou atualiza um local. Retorna o id. */
    suspend fun salvarLocal(local: LocalEntity): String {
        val novo = local.id.isBlank()
        val documento = if (novo) collection.document() else collection.document(local.id)

        val salvo = local.copy(id = documento.id, lastUpdated = System.currentTimeMillis())

        val dados = hashMapOf<String, Any?>(
            "nome" to salvo.nome,
            "endereco" to salvo.endereco,
            "latitude" to salvo.latitude,
            "longitude" to salvo.longitude,
            "placeId" to salvo.placeId,
            "responsavelId" to salvo.responsavelId
        )
        if (novo) dados["criadoEm"] = FieldValue.serverTimestamp()

        documento.set(dados, SetOptions.merge()).await()
        localDao.upsert(salvo)

        return salvo.id
    }

    suspend fun excluirLocal(id: String) {
        collection.document(id).delete().await()
        localDao.deleteById(id)
    }
}
