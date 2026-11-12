package com.example.vaivan.data.remote.localizacao

import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import com.example.vaivan.data.local.entities.RotaViagemEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implementação atual de [LocalizacaoViagemRemoteSource]: um documento por
 * viagem (sempre sobrescrito) em `localizacoes/{viagemId}` e outro em
 * `rotas_viagem/{viagemId}`, com os campos denormalizados que as regras do
 * Firestore usam pra liberar leitura (`motoristaId`, `responsaveisAutorizados`),
 * no mesmo espírito de
 * [com.example.vaivan.data.local.entities.ViagemEntity.responsaveisAutorizados].
 *
 * A rota fica num documento separado da posição de propósito: a posição é
 * reescrita a cada 5 s e a polyline (alguns KB) só muda de vez em quando —
 * juntas, cada responsável baixaria a polyline inteira a cada 5 s à toa.
 */
class FirestoreLocalizacaoViagemRemoteSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : LocalizacaoViagemRemoteSource {

    private val colecao = firestore.collection("localizacoes")
    private val colecaoRotas = firestore.collection("rotas_viagem")

    override suspend fun publicar(
        viagemId: String,
        motoristaId: String,
        responsaveisAutorizados: List<String>,
        latitude: Double,
        longitude: Double,
        etasParadas: String,
        atualizadoEm: Long
    ) {
        val documento = mapOf(
            "viagemId" to viagemId,
            "motoristaId" to motoristaId,
            "responsaveisAutorizados" to responsaveisAutorizados,
            "latitude" to latitude,
            "longitude" to longitude,
            "etasParadas" to etasParadas,
            "atualizadoEm" to atualizadoEm
        )
        colecao.document(viagemId).set(documento).await()
    }

    override fun observar(viagemId: String): Flow<LocalizacaoViagemEntity?> =
        observarDocumento(colecao.document(viagemId).path) { snapshot ->
            val latitude = snapshot.getDouble("latitude")
            val longitude = snapshot.getDouble("longitude")
            val atualizadoEm = snapshot.getLong("atualizadoEm")

            if (latitude == null || longitude == null || atualizadoEm == null) {
                null
            } else {
                LocalizacaoViagemEntity(
                    viagemId = viagemId,
                    latitude = latitude,
                    longitude = longitude,
                    atualizadoEm = atualizadoEm,
                    etasParadas = snapshot.getString("etasParadas").orEmpty()
                )
            }
        }

    override suspend fun publicarRota(
        viagemId: String,
        motoristaId: String,
        responsaveisAutorizados: List<String>,
        polylineEncoded: String,
        atualizadoEm: Long
    ) {
        val documento = mapOf(
            "viagemId" to viagemId,
            "motoristaId" to motoristaId,
            "responsaveisAutorizados" to responsaveisAutorizados,
            "polylineEncoded" to polylineEncoded,
            "atualizadoEm" to atualizadoEm
        )
        colecaoRotas.document(viagemId).set(documento).await()
    }

    override fun observarRota(viagemId: String): Flow<RotaViagemEntity?> =
        observarDocumento(colecaoRotas.document(viagemId).path) { snapshot ->
            val polyline = snapshot.getString("polylineEncoded")
            val atualizadoEm = snapshot.getLong("atualizadoEm")

            if (polyline.isNullOrBlank() || atualizadoEm == null) {
                null
            } else {
                RotaViagemEntity(viagemId, polyline, atualizadoEm)
            }
        }

    private fun <T> observarDocumento(
        caminho: String,
        converter: (DocumentSnapshot) -> T?
    ): Flow<T?> = callbackFlow {
        val registro = firestore.document(caminho).addSnapshotListener { snapshot, erro ->
            // Depois de um erro o Firestore já desligou este listener: fecha o
            // Flow com o erro pra quem coleta poder se reinscrever (ex.: o
            // documento ainda não existia porque o motorista não mandou a
            // primeira posição, e a regra recusou a leitura).
            if (erro != null) {
                close(erro)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(converter(snapshot))
        }

        awaitClose { registro.remove() }
    }
}
