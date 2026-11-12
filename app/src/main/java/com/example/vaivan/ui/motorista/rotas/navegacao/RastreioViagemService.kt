package com.example.vaivan.ui.motorista.rotas.navegacao

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.vaivan.R
import com.example.vaivan.core.util.EtaParadasUtil
import com.example.vaivan.core.util.PolylineUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.remote.localizacao.FirestoreLocalizacaoViagemRemoteSource
import com.example.vaivan.data.repository.LocalizacaoViagemRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.RoadSnappedLocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Rastreio da viagem em segundo plano (foreground service, tipo "location").
 *
 * É quem publica pro responsável, a cada 5 s, a posição da van e o ETA ao
 * vivo de cada parada, e a rota que o Navigation SDK está seguindo (só
 * quando ela muda). Antes isso morava na [NavegacaoViagemActivity] e parava
 * assim que o motorista minimizava o app; aqui continua com a tela fechada,
 * com uma notificação fixa enquanto a viagem estiver ativa.
 *
 * Usa o mesmo Navigator da Activity (o SDK mantém uma instância só por
 * processo, e a guia continua rodando no serviço do próprio SDK quando a
 * Activity sai de cena). Para sozinho quando a viagem é finalizada/cancelada.
 */
class RastreioViagemService : Service() {

    companion object {
        private const val TAG = "RastreioViagemService"
        private const val EXTRA_VIAGEM_ID = "viagemId"
        private const val CANAL_ID = "rastreio_viagem"
        private const val NOTIFICACAO_ID = 4201
        private const val INTERVALO_PUBLICACAO_MS = 5_000L
        private const val ANTECEDENCIA_APROXIMACAO_MS = 5 * 60_000L

        /** Debounce: o SDK pode disparar várias trocas de rota em sequência
         * (ex.: setDestinations + recálculo logo em seguida). */
        private const val ATRASO_PUBLICAR_ROTA_MS = 2_000L

        /** Chamar com a tela de navegação em primeiro plano — o Android não
         * deixa começar um serviço de localização a partir do background. */
        fun iniciar(context: Context, viagemId: String) {
            val intent = Intent(context, RastreioViagemService::class.java)
                .putExtra(EXTRA_VIAGEM_ID, viagemId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun parar(context: Context) {
            context.stopService(Intent(context, RastreioViagemService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database by lazy { VaivanDatabase.getInstance(this) }

    private val viagemRepository by lazy {
        ViagemRepository(database.viagemDao(), database.paradaViagemDao(), database.eventoViagemDao())
    }

    private val localizacaoRepository by lazy {
        LocalizacaoViagemRepository(
            database.localizacaoViagemDao(),
            database.rotaViagemDao(),
            FirestoreLocalizacaoViagemRemoteSource()
        )
    }

    private var viagemIdAtual: String? = null
    private var viagem: ViagemEntity? = null
    private var jobViagem: Job? = null
    private var jobPublicarRota: Job? = null

    /** Título do Waypoint -> ordem da parada (mesma regra de título que a
     * NavegacaoViagemActivity usa ao montar os waypoints), pra saber de qual
     * parada é cada ETA que o SDK devolve. */
    private var ordemPorTitulo: Map<String, Int> = emptyMap()

    private var paradasAtuais: List<ParadaViagemEntity> = emptyList()

    /** Paradas que já geraram o evento APROXIMACAO nesta viagem (a Cloud
     * Function também ignora repetidos, caso o serviço reinicie). */
    private val aproximacoesAvisadas = mutableSetOf<String>()

    private var navigator: Navigator? = null
    private var routeChangedListener: Navigator.RouteChangedListener? = null
    private var provedorLocalizacao: RoadSnappedLocationProvider? = null
    private var listenerLocalizacao: RoadSnappedLocationProvider.LocationListener? = null
    private var ultimaPosicao: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val viagemId = intent?.getStringExtra(EXTRA_VIAGEM_ID)
        if (viagemId == null || !entrarEmPrimeiroPlano()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (viagemId != viagemIdAtual) acompanharViagem(viagemId)

        // A Activity chama de novo depois de cada setDestinations (ex.: ao
        // retomar a viagem): garante que a rota nova sai na hora.
        agendarPublicacaoRota()

        // Sem reinício automático: se o sistema matar o processo, recomeçar
        // um serviço de localização a partir do background é bloqueado no
        // Android 14+. O motorista retoma pela lista de rotas / notificação.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pararAcompanhamento()
        scope.cancel()
        super.onDestroy()
    }

    // =========================================================
    // NOTIFICAÇÃO / PRIMEIRO PLANO
    // =========================================================

    private fun entrarEmPrimeiroPlano(): Boolean = try {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Viagem em andamento")
                .setDescription("Mantém a localização da van chegando aos responsáveis")
                .build()
        )

        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICACAO_ID, criarNotificacao(), tipo)
        true
    } catch (e: Exception) {
        // Ex.: ForegroundServiceStartNotAllowedException / SecurityException
        // se for chamado com o app em background ou sem permissão.
        Log.e(TAG, "Não foi possível iniciar o rastreio em primeiro plano", e)
        false
    }

    private fun criarNotificacao(): Notification {
        val abrirNavegacao = Intent(this, NavegacaoViagemActivity::class.java).apply {
            putExtra(NavegacaoViagemActivity.EXTRA_VIAGEM_ID, viagemIdAtual)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            abrirNavegacao,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_van)
            .setContentTitle("Viagem em andamento")
            .setContentText("Os responsáveis estão acompanhando a van em tempo real.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // =========================================================
    // VIAGEM
    // =========================================================

    private fun acompanharViagem(viagemId: String) {
        pararAcompanhamento()
        viagemIdAtual = viagemId

        // Atualiza a notificação com o viagemId certo no "tocar pra abrir".
        NotificationManagerCompat.from(this).let { gerenciador ->
            if (gerenciador.areNotificationsEnabled()) {
                try {
                    gerenciador.notify(NOTIFICACAO_ID, criarNotificacao())
                } catch (e: SecurityException) {
                    Log.w(TAG, "Sem permissão de notificação", e)
                }
            }
        }

        // Sincroniza a viagem por conta própria: com a Activity fechada,
        // ninguém mais traria pro Room a finalização feita em outro lugar.
        viagemRepository.iniciarSincronizacao(viagemId)

        jobViagem = scope.launch {
            launch {
                viagemRepository.observarViagem(viagemId).collect { atual ->
                    viagem = atual
                    if (atual != null && !estaAtiva(atual)) {
                        Log.d(TAG, "Viagem $viagemId encerrada (${atual.status}); parando rastreio.")
                        stopSelf()
                    }
                }
            }
            launch {
                viagemRepository.observarParadas(viagemId).collect { paradas ->
                    paradasAtuais = paradas
                    ordemPorTitulo = paradas
                        .groupBy { it.ordem }
                        .map { (ordem, grupo) ->
                            val referencia = grupo.first()
                            referencia.nomeLocal.ifBlank { referencia.endereco } to ordem
                        }
                        .toMap()
                }
            }
            launch {
                while (true) {
                    delay(INTERVALO_PUBLICACAO_MS)
                    publicarLocalizacao()
                }
            }
        }

        NavigationApi.getNavigator(application, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(nav: Navigator) = conectarNavigator(nav)

            override fun onError(errorCode: Int) {
                Log.e(TAG, "Navigator indisponível no serviço (código $errorCode)")
            }
        })
    }

    private fun estaAtiva(viagem: ViagemEntity) =
        viagem.status == ViagemRepository.STATUS_EM_ANDAMENTO ||
            viagem.status == ViagemRepository.STATUS_PAUSADA

    private fun conectarNavigator(nav: Navigator) {
        desconectarNavigator()
        navigator = nav

        // Dispara no setDestinations, em cada recálculo por desvio e a cada
        // continueToNextDestination.
        val listenerRota = Navigator.RouteChangedListener { agendarPublicacaoRota() }
        routeChangedListener = listenerRota
        nav.addRouteChangedListener(listenerRota)

        // Posição "grudada" na rua, a mesma que a guia usa — inclusive na
        // simulação de debug, onde o GPS do sistema fica parado.
        val listener = object : RoadSnappedLocationProvider.LocationListener {
            override fun onLocationChanged(location: Location) {
                ultimaPosicao = location
            }

            override fun onRawLocationUpdate(location: Location) = Unit
        }
        provedorLocalizacao = NavigationApi.getRoadSnappedLocationProvider(application)?.also {
            it.addLocationListener(listener)
            listenerLocalizacao = listener
        }

        agendarPublicacaoRota()
    }

    private fun desconectarNavigator() {
        routeChangedListener?.let { navigator?.removeRouteChangedListener(it) }
        routeChangedListener = null
        listenerLocalizacao?.let { provedorLocalizacao?.removeLocationListener(it) }
        listenerLocalizacao = null
        provedorLocalizacao = null
        navigator = null
    }

    private fun pararAcompanhamento() {
        jobViagem?.cancel()
        jobViagem = null
        jobPublicarRota?.cancel()
        jobPublicarRota = null
        desconectarNavigator()
        viagemIdAtual?.let { viagemRepository.pararSincronizacao(it) }
        viagemIdAtual = null
        viagem = null
        ultimaPosicao = null
        paradasAtuais = emptyList()
        aproximacoesAvisadas.clear()
    }

    // =========================================================
    // PUBLICAÇÃO (Firestore)
    // =========================================================

    private suspend fun publicarLocalizacao() {
        val viagemId = viagemIdAtual ?: return
        val viagemAtual = viagem ?: return
        if (!estaAtiva(viagemAtual)) return

        val nav = navigator
        val posicao = ultimaPosicao
        val latitude: Double
        val longitude: Double
        if (posicao != null) {
            latitude = posicao.latitude
            longitude = posicao.longitude
        } else {
            val ultimoPonto = nav?.traveledRoute?.lastOrNull() ?: return
            latitude = ultimoPonto.latitude
            longitude = ultimoPonto.longitude
        }

        val etas = calcularEtasPorOrdem(nav)

        try {
            localizacaoRepository.publicarLocalizacao(
                viagemId = viagemId,
                motoristaId = viagemAtual.motoristaId,
                responsaveisAutorizados = viagemAtual.responsaveisAutorizados,
                latitude = latitude,
                longitude = longitude,
                etasParadas = EtaParadasUtil.codificar(etas)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Uma escrita perdida não derruba nada; a próxima sai em 5 s.
            Log.w(TAG, "Falha ao publicar localização", e)
        }

        registrarAproximacoes(etas)
    }

    /** Grava o evento APROXIMACAO (vira o push "a van está chegando" pro
     * responsável) quando faltam [ANTECEDENCIA_APROXIMACAO_MS] para uma
     * parada pendente na casa de um aluno — uma vez por parada. */
    private suspend fun registrarAproximacoes(etas: Map<Int, Long>) {
        val agora = System.currentTimeMillis()
        val proximas = paradasAtuais.filter { parada ->
            val eta = etas[parada.ordem] ?: return@filter false
            parada.localId != ViagemRepository.LOCAL_ESCOLA &&
                parada.id !in aproximacoesAvisadas &&
                (parada.status == ViagemRepository.PARADA_AGUARDANDO ||
                    parada.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE) &&
                eta - agora <= ANTECEDENCIA_APROXIMACAO_MS
        }

        proximas.forEach { parada ->
            try {
                viagemRepository.registrarAproximacao(parada.viagemId, parada.id)
                aproximacoesAvisadas.add(parada.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao registrar aproximação da parada ${parada.id}", e)
            }
        }
    }

    /** ETA ao vivo (com trânsito) de cada parada que falta, direto do SDK:
     * getRouteSegments e getTimeAndDistanceList vêm um item por destino
     * restante, na mesma ordem — o título do waypoint de cada segmento diz
     * qual parada é. Leitura local, sem chamada cobrada. */
    private fun calcularEtasPorOrdem(nav: Navigator?): Map<Int, Long> {
        nav ?: return emptyMap()
        val agora = System.currentTimeMillis()
        return nav.routeSegments.zip(nav.timeAndDistanceList)
            .mapNotNull { (segmento, tempo) ->
                val ordem = segmento.destinationWaypoint?.title?.let { ordemPorTitulo[it] }
                    ?: return@mapNotNull null
                ordem to agora + tempo.seconds * 1_000L
            }
            .toMap()
    }

    private fun agendarPublicacaoRota() {
        jobPublicarRota?.cancel()
        jobPublicarRota = scope.launch {
            delay(ATRASO_PUBLICAR_ROTA_MS)
            publicarRotaAtual()
        }
    }

    /** Manda pro Firestore só o trecho que falta da rota atual do SDK. Roda
     * apenas quando a rota muda (não a cada 5 s): umas poucas escritas por
     * viagem, e nenhuma chamada nova à API do Google. */
    private suspend fun publicarRotaAtual() {
        val viagemId = viagemIdAtual ?: return
        val nav = navigator ?: return
        // A primeira rota pode sair antes de a viagem chegar do Room.
        val viagemAtual = viagem ?: viagemRepository.observarViagem(viagemId).filterNotNull().first()
        if (!estaAtiva(viagemAtual)) return

        val pontos = nav.routeSegments.flatMap { it.latLngs }
        if (pontos.size < 2) return

        try {
            localizacaoRepository.salvarRota(
                viagemId = viagemId,
                motoristaId = viagemAtual.motoristaId,
                responsaveisAutorizados = viagemAtual.responsaveisAutorizados,
                polylineEncoded = PolylineUtil.encode(pontos)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao publicar rota atual", e)
        }
    }
}
