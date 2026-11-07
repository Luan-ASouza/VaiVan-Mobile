package com.example.vaivan.data.remote.localizacao

import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implementação atual de [LocalizacaoViagemRemoteSource]: um documento por
 * viagem (sempre sobrescrito) em `localizacoes/{viagemId}`, com os campos
 * denormalizados que as regras do Firestore usam pra liberar leitura
 * (`motoristaId`, `responsaveisAutorizados`), no mesmo espírito de
 * [com.example.vaivan.data.local.entities.ViagemEntity.responsaveisAutorizados].
 */
class FirestoreLocalizacaoViagemRemoteSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : LocalizacaoViagemRemoteSource {

    private val colecao = firestore.collection("localizacoes")

    override suspend fun publicar(
        viagemId: String,
        motoristaId: String,
        responsaveisAutorizados: List<String>,
        latitude: Double,
        longitude: Double,
        atualizadoEm: Long
    ) {
        val documento = mapOf(
            "viagemId" to viagemId,
            "motoristaId" to motoristaId,
            "responsaveisAutorizados" to responsaveisAutorizados,
            "latitude" to latitude,
            "longitude" to longitude,
            "atualizadoEm" to atualizadoEm
        )
        colecao.document(viagemId).set(documento).await()
    }

    override fun observar(viagemId: String): Flow<LocalizacaoViagemEntity?> = callbackFlow {
        val registro = colecao.document(viagemId).addSnapshotListener { snapshot, erro ->
            if (erro != null || snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }

            val latitude = snapshot.getDouble("latitude")
            val longitude = snapshot.getDouble("longitude")
            val atualizadoEm = snapshot.getLong("atualizadoEm")

            trySend(
                if (latitude == null || longitude == null || atualizadoEm == null) {
                    null
                } else {
                    LocalizacaoViagemEntity(viagemId, latitude, longitude, atualizadoEm)
                }
            )
        }

        awaitClose { registro.remove() }
    }
}
