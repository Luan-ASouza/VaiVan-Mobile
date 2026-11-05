package com.example.vaivan.data.remote.localizacao

import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Porta pra publicar/observar a posição ao vivo do motorista numa viagem.
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
        atualizadoEm: Long
    )

    fun observar(viagemId: String): Flow<LocalizacaoViagemEntity?>
}
