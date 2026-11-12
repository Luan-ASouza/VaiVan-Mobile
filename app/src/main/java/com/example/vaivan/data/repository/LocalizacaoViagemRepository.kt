package com.example.vaivan.data.repository

import com.example.vaivan.data.local.dao.LocalizacaoViagemDao
import com.example.vaivan.data.local.dao.RotaViagemDao
import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import com.example.vaivan.data.local.entities.RotaViagemEntity
import com.example.vaivan.data.remote.localizacao.LocalizacaoViagemRemoteSource
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

private const val TAG = "LocalizacaoViagemRepo"
private const val INTERVALO_NOVA_TENTATIVA_MS = 5_000L

/**
 * Porta de entrada para o rastreio ao vivo de uma viagem: a posição do
 * motorista e a rota que ele está seguindo de fato (espelhada do Navigation
 * SDK). Segue o mesmo padrão do [ViagemRepository]: fonte remota (hoje
 * Firestore, escondida atrás de [LocalizacaoViagemRemoteSource]) = fonte da
 * verdade, Room = cache local que a UI observa.
 */
class LocalizacaoViagemRepository(
    private val dao: LocalizacaoViagemDao,
    private val rotaViagemDao: RotaViagemDao,
    private val remoteSource: LocalizacaoViagemRemoteSource
) {
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observarLocalizacao(viagemId: String): Flow<LocalizacaoViagemEntity?> =
        dao.observarPorViagem(viagemId)

    fun observarRota(viagemId: String): Flow<RotaViagemEntity?> =
        rotaViagemDao.observarPorViagem(viagemId)

    /** Sincroniza posição e rota da viagem (Firestore -> Room), cada uma com
     * o seu listener, até [pararSincronizacao]. */
    fun iniciarSincronizacao(viagemId: String) {
        pararSincronizacao()
        job = scope.launch {
            launch {
                remoteSource.observar(viagemId)
                    .retryWhen { causa, _ ->
                        Log.w(TAG, "Falha ao observar localização da viagem $viagemId; tentando de novo", causa)
                        delay(INTERVALO_NOVA_TENTATIVA_MS)
                        true
                    }
                    .collect { localizacao ->
                        if (localizacao != null) dao.upsert(localizacao)
                    }
            }
            launch {
                remoteSource.observarRota(viagemId)
                    .retryWhen { causa, _ ->
                        Log.w(TAG, "Falha ao observar rota da viagem $viagemId; tentando de novo", causa)
                        delay(INTERVALO_NOVA_TENTATIVA_MS)
                        true
                    }
                    .collect { rota ->
                        if (rota != null) rotaViagemDao.upsert(rota)
                    }
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
        longitude: Double,
        etasParadas: String = ""
    ) {
        remoteSource.publicar(
            viagemId = viagemId,
            motoristaId = motoristaId,
            responsaveisAutorizados = responsaveisAutorizados,
            latitude = latitude,
            longitude = longitude,
            etasParadas = etasParadas,
            atualizadoEm = System.currentTimeMillis()
        )
    }

    /** Publica a rota que o motorista está seguindo agora. Chamada só quando
     * o Navigation SDK troca de rota — não a cada atualização de posição. */
    suspend fun salvarRota(
        viagemId: String,
        motoristaId: String,
        responsaveisAutorizados: List<String>,
        polylineEncoded: String
    ) {
        remoteSource.publicarRota(
            viagemId = viagemId,
            motoristaId = motoristaId,
            responsaveisAutorizados = responsaveisAutorizados,
            polylineEncoded = polylineEncoded,
            atualizadoEm = System.currentTimeMillis()
        )
    }
}
