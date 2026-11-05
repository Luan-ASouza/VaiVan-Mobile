package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.LocalizacaoViagemDao
import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import com.example.vaivan.data.remote.localizacao.LocalizacaoViagemRemoteSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Porta de entrada para a posição ao vivo do motorista numa viagem. Segue o
 * mesmo padrão do [ViagemRepository]: fonte remota (hoje Firestore, escondida
 * atrás de [LocalizacaoViagemRemoteSource]) = fonte da verdade, Room = cache
 * local que a UI observa.
 */
class LocalizacaoViagemRepository(
    private val dao: LocalizacaoViagemDao,
    private val remoteSource: LocalizacaoViagemRemoteSource
) {
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observarLocalizacao(viagemId: String): Flow<LocalizacaoViagemEntity?> =
        dao.observarPorViagem(viagemId)

    fun iniciarSincronizacao(viagemId: String) {
        pararSincronizacao()
        job = scope.launch {
            remoteSource.observar(viagemId).collect { localizacao ->
                if (localizacao != null) dao.upsert(localizacao)
            }
        }
    }

    fun pararSincronizacao() {
        job?.cancel()
        job = null
    }

    suspend fun publicarLocalizacao(
        viagemId: String,
        motoristaId: String,
        responsaveisAutorizados: List<String>,
        latitude: Double,
        longitude: Double
    ) {
        remoteSource.publicar(
            viagemId = viagemId,
            motoristaId = motoristaId,
            responsaveisAutorizados = responsaveisAutorizados,
            latitude = latitude,
            longitude = longitude,
            atualizadoEm = System.currentTimeMillis()
        )
    }
}
