package com.example.vaivan.ui.motorista.rotas.navegacao

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.BuildConfig
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.repository.ViagemRepository
import com.google.android.gms.maps.GoogleMap
import com.google.android.libraries.navigation.ArrivalEvent
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationView
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.Waypoint
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Tela de navegação turno-a-turno do motorista (Google Navigation SDK).
 *
 * Não escreve a posição do GPS em lugar nenhum — isso é tarefa do serviço de
 * rastreamento (Etapa 3). Aqui só reage a chegadas em paradas e repassa a
 * confirmação de embarque/desembarque para o ViagemRepository.
 */
class NavegacaoViagemActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIAGEM_ID = "viagemId"
    }

    private lateinit var viagemId: String
    private lateinit var viagemRepository: ViagemRepository

    private lateinit var navigationView: NavigationView
    private lateinit var barraTopoNavegacao: View
    private lateinit var txtProximaParadaNavegacao: TextView
    private lateinit var btnPausarRetomarNavegacao: MaterialButton
    private lateinit var btnFinalizarNavegacao: MaterialButton
    private lateinit var painelParadaNavegacao: View
    private lateinit var txtTituloPainelParada: TextView
    private lateinit var txtEnderecoPainelParada: TextView
    private lateinit var containerPassageirosPainelParada: LinearLayout

    private var navigator: Navigator? = null
    private var viagemAtual: ViagemEntity? = null
    private var paradasAtuais: List<ParadaViagemEntity> = emptyList()

    /** O mesmo objeto Waypoint devolvido pelo SDK no evento de chegada -> as paradas daquele ponto. */
    private val paradasPorWaypoint = mutableMapOf<Waypoint, List<ParadaViagemEntity>>()

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
        setContentView(R.layout.activity_navegacao_viagem)

        viagemId = intent.getStringExtra(EXTRA_VIAGEM_ID) ?: run {
            finish()
            return
        }

        val database = VaivanDatabase.getInstance(this)
        viagemRepository = ViagemRepository(
            database.viagemDao(),
            database.paradaViagemDao(),
            database.eventoViagemDao()
        )

        inicializarViews(savedInstanceState)
        observarViagem()
        observarParadas()

        if (temPermissaoLocalizacao()) {
            inicializarNavegador()
        } else {
            solicitarPermissaoLocalizacao.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun inicializarViews(savedInstanceState: Bundle?) {
        navigationView = findViewById(R.id.navigationView)
        navigationView.onCreate(savedInstanceState)

        barraTopoNavegacao = findViewById(R.id.barraTopoNavegacao)
        txtProximaParadaNavegacao = findViewById(R.id.txtProximaParadaNavegacao)
        btnPausarRetomarNavegacao = findViewById(R.id.btnPausarRetomarNavegacao)
        btnFinalizarNavegacao = findViewById(R.id.btnFinalizarNavegacao)
        painelParadaNavegacao = findViewById(R.id.painelParadaNavegacao)
        txtTituloPainelParada = findViewById(R.id.txtTituloPainelParada)
        txtEnderecoPainelParada = findViewById(R.id.txtEnderecoPainelParada)
        containerPassageirosPainelParada = findViewById(R.id.containerPassageirosPainelParada)

        btnPausarRetomarNavegacao.setOnClickListener { alternarPausa() }
        btnFinalizarNavegacao.setOnClickListener { confirmarFinalizarViagem() }
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
                navigator = nav

                nav.addArrivalListener(object : Navigator.ArrivalListener {
                    override fun onArrival(arrivalEvent: ArrivalEvent) {
                        exibirPainelParaWaypoint(arrivalEvent.waypoint)
                    }
                })

                navigationView.getMapAsync { googleMap ->
                    googleMap.followMyLocation(GoogleMap.CameraPerspective.TILTED)
                }

                if (paradasAtuais.isNotEmpty()) {
                    montarWaypointsEIniciarGuia()
                }
            }

            override fun onError(errorCode: Int) {
                Toast.makeText(
                    this@NavegacaoViagemActivity,
                    "Não foi possível iniciar a navegação (código $errorCode).",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun montarWaypointsEIniciarGuia() {
        val navegador = navigator ?: return
        if (paradasPorWaypoint.isNotEmpty()) return // já iniciado

        val gruposPorOrdem = paradasAtuais
            .sortedBy { it.ordem }
            .groupBy { it.ordem }

        val waypoints = gruposPorOrdem.map { (_, paradasDoGrupo) ->
            val referencia = paradasDoGrupo.first()
            val waypoint = Waypoint.builder()
                .setLatLng(referencia.latitude, referencia.longitude)
                .setTitle(referencia.nomeLocal.ifBlank { referencia.endereco })
                .build()

            paradasPorWaypoint[waypoint] = paradasDoGrupo
            waypoint
        }

        if (waypoints.isEmpty()) return

        navegador.setDestinations(waypoints).setOnResultListener { status ->
            if (status == Navigator.RouteStatus.OK) {
                navegador.startGuidance()
            } else {
                Toast.makeText(
                    this,
                    "Não foi possível calcular a rota de navegação (status: $status).",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // =========================================================
    // ESTADO DA VIAGEM (Firestore/Room via ViagemRepository)
    // =========================================================

    private fun observarViagem() {
        viagemRepository.iniciarSincronizacao(viagemId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viagemRepository.observarViagem(viagemId).collect { viagem ->
                    viagemAtual = viagem
                    atualizarBotaoPausa(viagem)
                }
            }
        }
    }

    private fun observarParadas() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viagemRepository.observarParadas(viagemId).collect { paradas ->
                    paradasAtuais = paradas
                    atualizarTextoProximaParada(paradas)

                    if (navigator != null && paradasPorWaypoint.isEmpty() && paradas.isNotEmpty()) {
                        montarWaypointsEIniciarGuia()
                    }
                }
            }
        }
    }

    private fun atualizarTextoProximaParada(paradas: List<ParadaViagemEntity>) {
        val proxima = paradas
            .filter { it.status == ViagemRepository.PARADA_AGUARDANDO || it.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE }
            .minByOrNull { it.ordem }

        txtProximaParadaNavegacao.text = if (proxima == null) {
            "Todas as paradas foram concluídas."
        } else {
            val quantosNessaParada = paradas.count { it.ordem == proxima.ordem }
            if (quantosNessaParada > 1) {
                "Próxima parada: ${proxima.nomeLocal} ($quantosNessaParada passageiros)"
            } else {
                "Próxima parada: ${proxima.nomeLocal}"
            }
        }
    }

    private fun atualizarBotaoPausa(viagem: ViagemEntity?) {
        btnPausarRetomarNavegacao.text = if (viagem?.status == ViagemRepository.STATUS_PAUSADA) {
            "Retomar"
        } else {
            "Pausar"
        }
    }

    // =========================================================
    // PAINEL DE EMBARQUE / DESEMBARQUE
    // =========================================================

    private fun exibirPainelParaWaypoint(waypoint: Waypoint) {
        val paradas = paradasPorWaypoint[waypoint] ?: return
        runOnUiThread { exibirPainelDeParadas(paradas) }
    }

    private fun exibirPainelDeParadas(paradas: List<ParadaViagemEntity>) {
        if (paradas.isEmpty()) return

        val referencia = paradas.first()
        val ehEmbarque = referencia.tipo == ViagemRepository.TIPO_EMBARQUE

        txtTituloPainelParada.text = if (ehEmbarque) "Próxima parada — Embarque" else "Próxima parada — Desembarque"
        txtEnderecoPainelParada.text = referencia.endereco.ifBlank { referencia.nomeLocal }

        containerPassageirosPainelParada.removeAllViews()

        val inflater = LayoutInflater.from(this)

        paradas.forEach { parada ->
            val item = inflater.inflate(
                R.layout.item_passageiro_parada_navegacao,
                containerPassageirosPainelParada,
                false
            )

            desenharItemPassageiro(item, parada, paradas)
            containerPassageirosPainelParada.addView(item)
        }

        painelParadaNavegacao.visibility = View.VISIBLE
    }

    private fun desenharItemPassageiro(
        item: View,
        parada: ParadaViagemEntity,
        paradasDoGrupo: List<ParadaViagemEntity>
    ) {
        val nome = item.findViewById<TextView>(R.id.nomePassageiroItemParada)
        val status = item.findViewById<TextView>(R.id.statusPassageiroItemParada)
        val btnConfirmar = item.findViewById<MaterialButton>(R.id.btnConfirmarAcaoItemParada)
        val btnNao = item.findViewById<MaterialButton>(R.id.btnNaoAcaoItemParada)

        nome.text = parada.nomePassageiro

        val resolvido = parada.status != ViagemRepository.PARADA_AGUARDANDO &&
            parada.status != ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE

        status.text = textoStatus(parada.status)
        btnConfirmar.isEnabled = !resolvido
        btnNao.isEnabled = !resolvido

        if (parada.tipo == ViagemRepository.TIPO_EMBARQUE) {
            btnConfirmar.text = "Confirmar embarque"
            btnNao.text = "Não embarcou"
            btnNao.visibility = View.VISIBLE

            btnConfirmar.setOnClickListener {
                resolverParada(parada, paradasDoGrupo) { viagemRepository.confirmarEmbarque(parada) }
            }
            btnNao.setOnClickListener {
                resolverParada(parada, paradasDoGrupo) { viagemRepository.marcarNaoEmbarcou(parada, null) }
            }
        } else {
            btnConfirmar.text = "Confirmar desembarque"
            btnNao.visibility = View.GONE

            btnConfirmar.setOnClickListener {
                resolverParada(parada, paradasDoGrupo) { viagemRepository.confirmarDesembarque(parada) }
            }
        }
    }

    private fun textoStatus(status: String): String = when (status) {
        ViagemRepository.PARADA_EMBARCADO -> "Embarcado"
        ViagemRepository.PARADA_NAO_EMBARCOU -> "Não embarcou"
        ViagemRepository.PARADA_DESEMBARCADO -> "Desembarcado"
        else -> "Aguardando"
    }

    private fun resolverParada(
        parada: ParadaViagemEntity,
        paradasDoGrupo: List<ParadaViagemEntity>,
        acao: suspend () -> Unit
    ) {
        lifecycleScope.launch {
            try {
                acao()
                verificarGrupoResolvidoEAvancar(parada.ordem)
            } catch (erro: Exception) {
                Toast.makeText(
                    this@NavegacaoViagemActivity,
                    "Falha ao registrar: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun verificarGrupoResolvidoEAvancar(ordem: Int) {
        val grupo = paradasAtuais.filter { it.ordem == ordem }
        val todosResolvidos = grupo.all {
            it.status != ViagemRepository.PARADA_AGUARDANDO &&
                it.status != ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE
        }

        if (todosResolvidos) {
            painelParadaNavegacao.visibility = View.GONE

            val proxima = paradasAtuais
                .filter { it.ordem > ordem }
                .minByOrNull { it.ordem }

            lifecycleScope.launch {
                viagemRepository.avancarParaProximaParada(viagemId, proxima?.id)

                if (proxima == null) {
                    viagemRepository.finalizarViagem(viagemId)
                    Toast.makeText(
                        this@NavegacaoViagemActivity,
                        "Viagem finalizada.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    // =========================================================
    // PAUSAR / RETOMAR / FINALIZAR
    // =========================================================

    private fun alternarPausa() {
        val navegador = navigator ?: return

        if (viagemAtual?.status == ViagemRepository.STATUS_PAUSADA) {
            navegador.startGuidance()
        } else {
            navegador.stopGuidance()
        }
    }

    private fun confirmarFinalizarViagem() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Finalizar viagem?")
            .setMessage("Isso encerra a navegação e a viagem atual.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Finalizar") { _, _ ->
                lifecycleScope.launch {
                    viagemRepository.finalizarViagem(viagemId)
                    finish()
                }
            }
            .show()
    }

    // =========================================================
    // SIMULAÇÃO (só em build de debug, para testar sem veículo real)
    // =========================================================

    private fun iniciarSimulacaoSeDebug() {
        if (!BuildConfig.DEBUG) return
        navigator?.simulator?.simulateLocationsAlongExistingRoute()
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
        viagemRepository.pararSincronizacao()
        super.onStop()
    }

    override fun onDestroy() {
        navigationView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        navigationView.onSaveInstanceState(outState)
    }
}
