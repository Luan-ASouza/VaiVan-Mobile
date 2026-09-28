package com.example.vaivan.data.repository

import com.example.vaivan.core.util.TextoUtil
import com.example.vaivan.data.models.Escola
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class EscolaRepository {

    private val collection =
        FirebaseFirestore.getInstance().collection("escolas")

    suspend fun listarEscolas(): List<Escola> =
        withContext(Dispatchers.IO) {
            collection
                .orderBy("nome")
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.toObject(Escola::class.java)?.copy(id = doc.id)
                }
        }

    /**
     * Cadastra a escola. Se já existir uma com o mesmo nome,
     * devolve a existente em vez de duplicar.
     */
    suspend fun cadastrarEscola(
        nome: String,
        endereco: String,
        cidade: String,
        latitude: Double,
        longitude: Double,
        criadoPor: String
    ): Escola = withContext(Dispatchers.IO) {

        val normalizado = TextoUtil.normalizar(nome)

        val existente =
            collection
                .whereEqualTo("nomeNormalizado", normalizado)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()

        if (existente != null) {
            return@withContext existente
                .toObject(Escola::class.java)!!
                .copy(id = existente.id)
        }

        val documento = collection.document()

        val escola = Escola(
            id = documento.id,
            nome = nome.trim(),
            nomeNormalizado = normalizado,
            endereco = endereco,
            cidade = cidade,
            latitude = latitude,
            longitude = longitude,
            criadoPor = criadoPor,
            criadoEm = System.currentTimeMillis()
        )

        documento.set(escola).await()

        escola
    }
}