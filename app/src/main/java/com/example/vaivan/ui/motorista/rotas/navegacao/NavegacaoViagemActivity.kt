package com.example.vaivan.ui.motorista.rotas.navegacao

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vaivan.BuildConfig
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.remote.localizacao.FirestoreLocalizacaoViagemRemoteSource
import com.example.vaivan.data.repository.LocalizacaoViagemRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.google.android.gms.maps.GoogleMap
import com.google.android.libraries.navigation.ArrivalEvent
import com.google.android.libraries.navigation.AudioGuidanceSettings
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationView
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.StylingOptions
import com.google.android.libraries.navigation.Waypoint
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Tela de navegação turno-a-turno do motorista (Google Navigation SDK).
 *
 * A Activity só cuida de interface e do ciclo de vida do SDK de navegação
 * (que precisa de uma View real, não pode morar no ViewModel). Todo o estado
 * da viagem/paradas e o acesso ao Firebase ficam no [NavegacaoViagemViewModel]
 * e no [ViagemRepository] — não escreve a posição do GPS em lugar nenhum,
 * isso é tarefa do serviço de rastreamento (Etapa 3).
 */
class NavegacaoViagemActivity : BaseActivity() {

    companion object {
        const val EXTRA_VIAGEM_ID = "viagemId"
        private const val DURACAO_TIMER_CHEGADA_MS = 120_000L

        // TODO (debug temporário): remover depois de descobrir por que o card
        // de chegada não aparece. Filtre o Logcat por essa tag.
        private const val TAG_DEBUG = "NavegacaoViagemDebug"
    }

    private lateinit var viagemId: String

    private val localizacaoViagemRepository: LocalizacaoViagemRepository by lazy {
        val database = VaivanDatabase.getInstance(this)
        LocalizacaoViagemRepository(
            database.localizacaoViagemDao(),
            FirestoreLocalizacaoViagemRemoteSource()
        )
    }

    private val viewModel: NavegacaoViagemViewModel by viewModels {
        viewModelFactory {
            initializer {
                val database = VaivanDatabase.getInstance(this@NavegacaoViagemActivity)
                val viagemRepository = ViagemRepository(
                    database.viagemDao(),
                    database.paradaViagemDao(),
                    database.eventoViagemDao()
                )
                NavegacaoViagemViewModel(viagemId, viagemRepository)
            }
        }
    }

    private lateinit var navigationView: NavigationView
    private lateinit var fotoProximaParadaNavegacao: ShapeableImageView
    private lateinit var txtProximaParadaNavegacao: TextView
    private lateinit var txtEnderecoProximaParadaNavegacao: TextView
    private lateinit var btnPausarRetomarNavegacao: MaterialButton
    private lateinit var btnFinalizarNavegacao: MaterialButton
    private lateinit var btnReiniciarSimulacaoNavegacao: MaterialButton
    private lateinit var btnRecentralizarNavegacao: FloatingActionButton
    private lateinit var btnAlternarVozNavegacao: FloatingActionButton

    /** Substitui o botão nativo de voz (o SDK não tem um pronto): true = com
     * áudio de alertas e instruções de condução; false = silenciado. */
    private var vozAtiva = true

    // Card de chegada (estilo "atender ligação") — some/aparece sozinho
    private lateinit var cardChegadaNavegacao: View
    private lateinit var txtTituloPainelParada: TextView
    private lateinit var txtEnderecoPainelParada: TextView
    private lateinit var txtTimerCardChegada: TextView
    private lateinit var recyclerPassageirosPainelParada: RecyclerView
    private lateinit var btnFinalizarParadaFinal: MaterialButton

    // Bottom Sheet arrastável com o progresso de todos os passageiros da viagem
    private lateinit var painelProgressoViagem: View
    private lateinit var recyclerProgressoViagem: RecyclerView

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var passageiroAdapter: PassageiroParadaAdapter
    private lateinit var progressoAdapter: ProgressoViagemAdapter

    private var navigator: Navigator? = null
    private var timerChegada: CountDownTimer? = null

    /** Último valor de ordemPainelAtivo, só pra detectar a transição
     * "card aberto -> fechado" (resolvido) e então chamar continueToNextDestination. */
    private var ordemCardAnterior: Int? = null

    /** Guardado só pra poder remover no onDestroy — confirmado em teste real
     * que o Navigator do SDK não é recriado do zero por Activity: sem isso, o
     * listener de uma instância antiga (com dados de outra viagem) continua
     * disparando junto com o da instância atual a cada chegada. */
    private var arrivalListener: Navigator.ArrivalListener? = null

    /** Mapeia o TÍTULO do Waypoint -> a ORDEM da parada (não o objeto Waypoint:
     * confirmado em teste real que o Waypoint devolvido no ArrivalEvent não é
     * `equals()` ao que foi passado em setDestinations — provavelmente porque
     * o SDK reprocessa a posição ao calcular a rota, então a comparação por
     * LatLng falha mesmo com coordenadas visualmente idênticas no log).
     * Guarda só a ordem (imutável), nunca a lista de passageiros: o conteúdo
     * exibido vem sempre do estado atual do ViewModel, nunca de uma cópia
     * congelada no momento em que a navegação começou. */
    private val ordemPorTituloWaypoint = mutableMapOf<String, Int>()

    /** Índice do próximo waypoint ainda não "consumido" (ver [avancarWaypointSdk])
     * dentro de `ordemPorTituloWaypoint.values` — que preserva a ordem de
     * inserção, ou seja, a ordem crescente em que os waypoints foram
     * mandados pro SDK em setDestinations. */
    private var indiceProximoWaypoint = 0

    private val solicitarPermissaoLocalizacao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
            if (concedida) {
                inicializarNavegador()
            } else {
                Toast.makeText(
                    this,
                    "A navegação precisa da permissão de localização para funcionar.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viagemId = intent.getStringExtra(EXTRA_VIAGEM_ID) ?: run {
            finish()
            return
        }

        setContentView(R.layout.activity_navegacao_viagem)

        inicializarViews(savedInstanceState)
        observarEstado()

        if (temPermissaoLocalizacao()) {
            inicializarNavegador()
        } else {
            solicitarPermissaoLocalizacao.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        aplicarGapsDeSistema()
    }

    private fun inicializarViews(savedInstanceState: Bundle?) {
        navigationView = findViewById(R.id.navigationView)
        navigationView.onCreate(savedInstanceState)

        // Tira o botão nativo de reportar incidente/alerta e o de
        // recentralizar (este último é substituído pelo FAB próprio, porque
        // o SDK não permite trocar o ícone do nativo).
        navigationView.setReportIncidentButtonEnabled(false)
        navigationView.setRecenterButtonEnabled(false)

        // Cor do percurso/cabeçalho e cor do texto do banner de distância
        // ("700 metros"): a API só expõe cor, não o formato/layout do texto.
        navigationView.setStylingOptions(
            StylingOptions()
                .primaryDayModeThemeColor(ContextCompat.getColor(this, R.color.color_primary))
                .primaryNightModeThemeColor(ContextCompat.getColor(this, R.color.color_primary))
                .headerDistanceValueTextColor(ContextCompat.getColor(this, R.color.white))
                .headerDistanceUnitsTextColor(ContextCompat.getColor(this, R.color.white))
                .headerLargeManeuverIconColor(ContextCompat.getColor(this, R.color.white))
                .headerSmallManeuverIconColor(ContextCompat.getColor(this, R.color.white))
        )

        fotoProximaParadaNavegacao = findViewById(R.id.fotoProximaParadaNavegacao)
        txtProximaParadaNavegacao = findViewById(R.id.txtProximaParadaNavegacao)
        txtEnderecoProximaParadaNavegacao = findViewById(R.id.txtEnderecoProximaParadaNavegacao)
        btnPausarRetomarNavegacao = findViewById(R.id.btnPausarRetomarNavegacao)
        btnFinalizarNavegacao = findViewById(R.id.btnFinalizarNavegacao)
        btnReiniciarSimulacaoNavegacao = findViewById(R.id.btnReiniciarSimulacaoNavegacao)
        btnRecentralizarNavegacao = findViewById(R.id.btnRecentralizarNavegacao)
        btnAlternarVozNavegacao = findViewById(R.id.btnAlternarVozNavegacao)

        cardChegadaNavegacao = findViewById(R.id.cardChegadaNavegacao)
        txtTituloPainelParada = findViewById(R.id.txtTituloPainelParada)
        txtEnderecoPainelParada = findViewById(R.id.txtEnderecoPainelParada)
        txtTimerCardChegada = findViewById(R.id.txtTimerCardChegada)
        recyclerPassageirosPainelParada = findViewById(R.id.recyclerPassageirosPainelParada)
        btnFinalizarParadaFinal = findViewById(R.id.btnFinalizarParadaFinal)

        painelProgressoViagem = findViewById(R.id.painelProgressoViagem)
        recyclerProgressoViagem = findViewById(R.id.recyclerProgressoViagem)

        passageiroAdapter = PassageiroParadaAdapter(
            onConfirmar = { parada ->
                if (parada.tipo == ViagemRepository.TIPO_EMBARQUE) {
                    viewModel.confirmarEmbarque(parada)
                } else {
                    viewModel.confirmarDesembarque(parada)
                }
            },
            onNaoEmbarcou = { parada -> viewModel.marcarNaoEmbarcou(parada) }
        )

        recyclerPassageirosPainelParada.layoutManager = LinearLayoutManager(this)
        recyclerPassageirosPainelParada.adapter = passageiroAdapter

        progressoAdapter = ProgressoViagemAdapter()
        recyclerProgressoViagem.layoutManager = LinearLayoutManager(this)
        recyclerProgressoViagem.adapter = progressoAdapter

        // O Bottom Sheet é o progresso geral da viagem — sempre disponível pra
        // arrastar, independente do card de chegada (que tem vida própria).
        bottomSheetBehavior = BottomSheetBehavior.from(painelProgressoViagem)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        btnPausarRetomarNavegacao.setOnClickListener { alternarPausa() }
        btnFinalizarNavegacao.setOnClickListener { confirmarFinalizarViagem() }
        btnFinalizarParadaFinal.setOnClickListener { viewModel.finalizarParadaFinal() }

        btnRecentralizarNavegacao.setOnClickListener {
            navigationView.getMapAsync { googleMap ->
                googleMap.followMyLocation(GoogleMap.CameraPerspective.TOP_DOWN_HEADING_UP)
            }
        }
        btnAlternarVozNavegacao.setOnClickListener { alternarVoz() }

        if (BuildConfig.DEBUG) {
            btnReiniciarSimulacaoNavegacao.visibility = View.VISIBLE
            btnReiniciarSimulacaoNavegacao.setOnClickListener { reiniciarSimulacao() }
        }
    }

    private fun temPermissaoLocalizacao(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // =========================================================
    // NAVIGATION SDK
    // =========================================================

    private fun inicializarNavegador() {
        NavigationApi.getNavigator(this, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(nav: Navigator) {
                Log.d(TAG_DEBUG, "onNavigatorReady: Navigator pronto.")
                navigator = nav

                // Se já havia um listener de uma instância anterior desta tela
                // ainda pendurado no Navigator (ele parece ser compartilhado,
                // não recriado por Activity), remove antes de adicionar o novo.
                arrivalListener?.let { nav.removeArrivalListener(it) }

                val listener = object : Navigator.ArrivalListener {
                    override fun onArrival(arrivalEvent: ArrivalEvent) {
                        val waypoint = arrivalEvent.waypoint
                        Log.d(
                            TAG_DEBUG,
                            "onArrival: evento recebido! waypoint=${waypoint.title} " +
                                "lat=${waypoint.position?.latitude} lng=${waypoint.position?.longitude}"
                        )

                        val ordem = waypoint.title?.let { ordemPorTituloWaypoint[it] }
                        if (ordem == null) {
                            Log.w(
                                TAG_DEBUG,
                                "onArrival: NÃO achei a ordem pro título '${waypoint.title}' " +
                                    "(ordemPorTituloWaypoint tem ${ordemPorTituloWaypoint.size} entradas)."
                            )
                            return
                        }

                        Log.d(TAG_DEBUG, "onArrival: ordem=$ordem encontrada, exibindo painel.")
                        runOnUiThread { viewModel.exibirPainelParaOrdem(ordem) }
                        viewModel.registrarChegadaParaOrdem(ordem)
                    }
                }
                arrivalListener = listener
                nav.addArrivalListener(listener)

                navigationView.getMapAsync { googleMap ->
                    googleMap.followMyLocation(GoogleMap.CameraPerspective.TOP_DOWN_HEADING_UP)
                }

                montarWaypointsEIniciarGuiaSePossivel(viewModel.paradas.value)
            }

            override fun onError(errorCode: Int) {
                Log.e(TAG_DEBUG, "onError: Navigator falhou ao iniciar, código=$errorCode")
                Toast.makeText(
                    this@NavegacaoViagemActivity,
                    "Não foi possível iniciar a navegação (código $errorCode).",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun montarWaypointsEIniciarGuiaSePossivel(paradas: List<ParadaViagemEntity>) {
        val navegador = navigator ?: run {
            Log.d(TAG_DEBUG, "montarWaypoints: ainda sem navigator, adiando.")
            return
        }
        if (ordemPorTituloWaypoint.isNotEmpty() || paradas.isEmpty()) {
            Log.d(
                TAG_DEBUG,
                "montarWaypoints: ignorado (ordemPorTituloWaypoint.isNotEmpty=${ordemPorTituloWaypoint.isNotEmpty()}, " +
                    "paradas.isEmpty=${paradas.isEmpty()})"
            )
            return
        }

        val pendentes = paradas.filter {
            it.status == ViagemRepository.PARADA_AGUARDANDO ||
                it.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE
        }

        if (pendentes.isEmpty()) {
            Log.d(TAG_DEBUG, "montarWaypoints: nenhuma parada pendente, nada a fazer.")
            return
        }

        val waypoints = pendentes
            .sortedBy { it.ordem }
            .groupBy { it.ordem }
            .map { (ordem, paradasDoGrupo) ->
                val referencia = paradasDoGrupo.first()
                val titulo = referencia.nomeLocal.ifBlank { referencia.endereco }
                val waypoint = Waypoint.builder()
                    .setLatLng(referencia.latitude, referencia.longitude)
                    .setTitle(titulo)
                    .setVehicleStopover(true)
                    .build()

                Log.d(
                    TAG_DEBUG,
                    "montarWaypoints: parada ordem=$ordem título='$titulo' " +
                        "lat=${referencia.latitude} lng=${referencia.longitude}"
                )

                ordemPorTituloWaypoint[titulo] = ordem
                waypoint
            }

        Log.d(TAG_DEBUG, "montarWaypoints: chamando setDestinations com ${waypoints.size} waypoint(s).")

        navegador.setDestinations(waypoints).setOnResultListener { status ->
            Log.d(TAG_DEBUG, "setDestinations: status=$status")
            if (status == Navigator.RouteStatus.OK) {
                Log.d(TAG_DEBUG, "setDestinations: OK, chamando startGuidance().")
                navegador.startGuidance()
            } else {
                Log.w(TAG_DEBUG, "setDestinations: FALHOU com status=$status")
                Toast.makeText(
                    this,
                    "Não foi possível calcular a rota de navegação (status: $status).",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Ordem do waypoint que o SDK ainda não "consumiu" — não necessariamente
     * o que ele já chegou fisicamente perto, só o próximo da fila que ainda
     * não recebeu um continueToNextDestination(). */
    private fun ordemAtualDoSdk(): Int? =
        ordemPorTituloWaypoint.values.toList().getOrNull(indiceProximoWaypoint)

    private fun avancarWaypointSdk() {
        Log.d(TAG_DEBUG, "continueToNextDestination: chamando (ordem=${ordemAtualDoSdk()}).")
        navigator?.continueToNextDestination()
        indiceProximoWaypoint++
    }

    /** Libera a navegação pra próxima parada quando o alvo atual do SDK fica
     * resolvido sem o motorista ter chegado fisicamente lá — ex.: falta
     * avisada aplicada numa viagem já em andamento
     * (ViagemRepository.aplicarAusenciaNaParadaAtiva). Sem isso, o SDK
     * continuaria guiando fisicamente até um ponto que não tem mais ninguém
     * pra embarcar/desembarcar. */
    private fun avancarSeAlvoAtualResolvido(paradas: List<ParadaViagemEntity>) {
        val ordemAtual = ordemAtualDoSdk() ?: return
        val grupo = paradas.filter { it.ordem == ordemAtual }
        if (grupo.isEmpty()) return

        val aindaPendente = grupo.any {
            it.status == ViagemRepository.PARADA_AGUARDANDO ||
                it.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE
        }
        if (aindaPendente) return

        if (viewModel.ordemPainelAtivo.value == ordemAtual) {
            // Card aberto pra esse grupo (motorista já chegou lá) — fecha e
            // deixa o coletor de ordemPainelAtivo chamar avancarWaypointSdk().
            viewModel.ocultarPainel()
        } else {
            avancarWaypointSdk()
        }
    }

    // =========================================================
    // ESTADO DA TELA (ViewModel)
    // =========================================================

    private fun observarEstado() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.viagem.collect { atualizarBotaoPausa(it) }
                }
                launch {
                    viewModel.paradas.collect { paradas ->
                        montarWaypointsEIniciarGuiaSePossivel(paradas)
                        progressoAdapter.submitLista(paradas)
                        avancarSeAlvoAtualResolvido(paradas)
                    }
                }
                launch {
                    viewModel.proximaParadaPendente.collect { atualizarResumoParada(it) }
                }
                launch {
                    viewModel.ordemPainelAtivo.collect { ordem ->
                        if (ordem != null) {
                            cardChegadaNavegacao.visibility = View.VISIBLE
                            iniciarTimerChegada()
                        } else {
                            cardChegadaNavegacao.visibility = View.GONE
                            pararTimerChegada()

                            // Card fechou porque a parada foi resolvida (não é o
                            // estado inicial, antes de qualquer chegada) -> avisa
                            // o SDK que pode seguir pra próxima parada da rota.
                            // Sem isso, o veículo continua andando fisicamente
                            // (inclusive no simulador), mas o SDK nunca troca o
                            // alvo de detecção de chegada pro próximo waypoint.
                            if (ordemCardAnterior != null) {
                                avancarWaypointSdk()
                            }
                        }
                        ordemCardAnterior = ordem
                    }
                }
                launch {
                    viewModel.paradasDoPainel.collect { paradasDoPainel ->
                        atualizarCardChegada(paradasDoPainel)
                    }
                }
                launch {
                    viewModel.ehParadaFinal.collect {
                        atualizarCardChegada(viewModel.paradasDoPainel.value)
                    }
                }
                launch {
                    publicarLocalizacaoPeriodicamente()
                }
            }
        }
    }

    // =========================================================
    // LOCALIZAÇÃO AO VIVO (pro responsável acompanhar no mapa)
    // =========================================================

    /** Publica a posição que o próprio Navigator está usando pra guiar —
     * [Navigator.getTraveledRoute] vem do RoadSnappedLocationProvider, então
     * reflete tanto o GPS real quanto a simulação de debug
     * (`simulator.simulateLocationsAlongExistingRoute`), ao contrário do
     * FusedLocationProviderClient, que só lê a localização real do sistema
     * e por isso fica parado durante uma simulação. */
    private suspend fun publicarLocalizacaoPeriodicamente() {
        while (true) {
            delay(5_000L)

            val posicao = navigator?.getTraveledRoute()?.lastOrNull()
            val viagem = viewModel.viagem.value

            if (posicao != null && viagem != null) {
                localizacaoViagemRepository.publicarLocalizacao(
                    viagemId = viagemId,
                    motoristaId = viagem.motoristaId,
                    responsaveisAutorizados = viagem.responsaveisAutorizados,
                    latitude = posicao.latitude,
                    longitude = posicao.longitude
                )
            }
        }
    }

    private fun atualizarResumoParada(proxima: ParadaViagemEntity?) {
        if (proxima == null) {
            txtProximaParadaNavegacao.text = "Todas as paradas foram concluídas."
            txtEnderecoProximaParadaNavegacao.text = ""
            fotoProximaParadaNavegacao.setImageResource(R.drawable.ic_perfil)
            return
        }

        txtProximaParadaNavegacao.text = "A caminho de: ${proxima.nomePassageiro}"
        txtEnderecoProximaParadaNavegacao.text = proxima.endereco.ifBlank { proxima.nomeLocal }

        Glide.with(fotoProximaParadaNavegacao)
            .load(proxima.fotoPassageiroUrl)
            .placeholder(R.drawable.ic_perfil)
            .error(R.drawable.ic_perfil)
            .circleCrop()
            .into(fotoProximaParadaNavegacao)
    }

    private fun atualizarBotaoPausa(viagem: ViagemEntity?) {
        btnPausarRetomarNavegacao.text = if (viagem?.status == ViagemRepository.STATUS_PAUSADA) {
            "Retomar"
        } else {
            "Pausar"
        }
    }

    private fun atualizarCardChegada(paradas: List<ParadaViagemEntity>) {
        val ehParadaFinal = viewModel.ehParadaFinal.value

        if (ehParadaFinal) {
            recyclerPassageirosPainelParada.visibility = View.GONE
            btnFinalizarParadaFinal.visibility = View.VISIBLE
            txtTituloPainelParada.text = "Chegada ao destino final"
        } else {
            recyclerPassageirosPainelParada.visibility = View.VISIBLE
            btnFinalizarParadaFinal.visibility = View.GONE
            passageiroAdapter.submitLista(paradas)

            val referencia = paradas.firstOrNull()
            if (referencia != null) {
                val ehEmbarque = referencia.tipo == ViagemRepository.TIPO_EMBARQUE
                txtTituloPainelParada.text = if (ehEmbarque) "Chegada — Embarque" else "Chegada — Desembarque"
            }
        }

        val referencia = paradas.firstOrNull() ?: return
        txtEnderecoPainelParada.text = referencia.endereco.ifBlank { referencia.nomeLocal }
    }

    // =========================================================
    // TIMER DE 2 MINUTOS NO CARD DE CHEGADA
    // =========================================================

    private fun iniciarTimerChegada() {
        pararTimerChegada()

        txtTimerCardChegada.setTextColor(ContextCompat.getColor(this, R.color.dark_gray))

        timerChegada = object : CountDownTimer(DURACAO_TIMER_CHEGADA_MS, 1_000L) {
            override fun onTick(millisRestantes: Long) {
                val segundosTotais = (millisRestantes / 1000).toInt()
                txtTimerCardChegada.text = String.format(
                    Locale("pt", "BR"),
                    "%d:%02d",
                    segundosTotais / 60,
                    segundosTotais % 60
                )
            }

            override fun onFinish() {
                txtTimerCardChegada.text = "Tempo esgotado"
                txtTimerCardChegada.setTextColor(ContextCompat.getColor(this@NavegacaoViagemActivity, R.color.status_danger))
                Toast.makeText(
                    this@NavegacaoViagemActivity,
                    "Tempo de espera esgotado. Confirme o embarque para continuar.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }

    private fun pararTimerChegada() {
        timerChegada?.cancel()
        timerChegada = null
    }

    // =========================================================
    // PAUSAR / RETOMAR / FINALIZAR
    // =========================================================

    private fun alternarPausa() {
        val navegador = navigator ?: return

        if (viewModel.viagem.value?.status == ViagemRepository.STATUS_PAUSADA) {
            navegador.startGuidance()
        } else {
            navegador.stopGuidance()
        }

        viewModel.alternarPausa()
    }

    /** Alterna entre guiagem com voz (alertas + instruções de condução) e
     * totalmente silenciada. setAudioGuidance(Int) é deprecated — a API atual
     * é setAudioGuidanceSettings(AudioGuidanceSettings). */
    private fun alternarVoz() {
        vozAtiva = !vozAtiva

        val modo = if (vozAtiva) {
            AudioGuidanceSettings.GuidanceMode.VOICE_ALERTS_AND_GUIDANCE
        } else {
            AudioGuidanceSettings.GuidanceMode.SILENT
        }

        navigator?.setAudioGuidanceSettings(
            AudioGuidanceSettings.builder().setGuidanceMode(modo).build()
        )

        btnAlternarVozNavegacao.setImageResource(
            if (vozAtiva) R.drawable.ic_som_ativado else R.drawable.ic_som_desativado
        )
    }

    private fun confirmarFinalizarViagem() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Finalizar viagem?")
            .setMessage("Isso encerra a navegação e a viagem atual.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Finalizar") { _, _ ->
                viewModel.finalizarViagem()
                finish()
            }
            .show()
    }

    // =========================================================
    // SIMULAÇÃO (só em build de debug, para testar sem veículo real)
    // =========================================================

    private fun iniciarSimulacaoSeDebug() {
        val simulador = navigator?.simulator
        Log.d(TAG_DEBUG, "iniciarSimulacaoSeDebug: navigator=${navigator != null} simulador=${simulador != null}")
        simulador?.simulateLocationsAlongExistingRoute()
    }

    /** Volta o veículo simulado para o início da rota e recomeça a simulação. */
    private fun reiniciarSimulacao() {
        val simulador = navigator?.simulator ?: return
        Log.d(TAG_DEBUG, "reiniciarSimulacao: reiniciando.")
        simulador.unsetUserLocation()
        simulador.simulateLocationsAlongExistingRoute()
    }

    // =========================================================
    // CICLO DE VIDA DO NavigationView (igual a um MapView)
    // =========================================================

    override fun onStart() {
        super.onStart()
        navigationView.onStart()
    }

    override fun onResume() {
        super.onResume()
        navigationView.onResume()
        if (BuildConfig.DEBUG) iniciarSimulacaoSeDebug()
    }

    override fun onPause() {
        navigationView.onPause()
        super.onPause()
    }

    override fun onStop() {
        navigationView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        pararTimerChegada()
        arrivalListener?.let { navigator?.removeArrivalListener(it) }
        navigationView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        navigationView.onSaveInstanceState(outState)
    }
}
