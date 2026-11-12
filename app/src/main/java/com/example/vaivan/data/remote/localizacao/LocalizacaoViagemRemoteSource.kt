package com.example.vaivan.data.remote.localizacao

import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import com.example.vaivan.data.local.entities.RotaViagemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Porta pra publicar/observar a posição ao vivo do motorista numa viagem —
 * e a rota que ele está seguindo de fato (espelhada do Navigation SDK).
 * Hoje implementada com Firestore ([FirestoreLocalizacaoViagemRemoteSource]);
 * o resto do app nunca referencia Firestore pra essa feature, só esta
 * interface — trocar de banco no futuro é escrever uma nova implementação
 * e mudar a montagem do [com.example.vaivan.data.repository.LocalizacaoViagemRepository].
 */
interface LocalizacaoViagemRemoteSource {

    suspend fun publicar(
        viagemId: String,
        motoristaId: String,
        responsaveisAutorizados: List<String>,
        latitude: Double,
        longitude: Double,
        etasParadas: String,
        atualizadoEm: Long
    )

    fun observar(viagemId: String): Flow<LocalizacaoViagemEntity?>

    suspend fun publicarRota(
        viagemId: String,
        motoristaId: String,
        responsaveisAutorizados: List<String>,
        polylineEncoded: String,
        atualizadoEm: Long
    )

    fun observarRota(viagemId: String): Flow<RotaViagemEntity?>
}
