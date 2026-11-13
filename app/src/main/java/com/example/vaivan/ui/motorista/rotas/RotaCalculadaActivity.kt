package com.example.vaivan.ui.motorista.rotas


import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.MapaEstiloUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.core.util.PolylineUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.RotaRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch

class RotaCalculadaActivity : BaseActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_ROTA_ID = "rotaId"
    }

    private lateinit var rotaId: String
    private lateinit var googleMap: GoogleMap
    private lateinit var txtResumoMapa: TextView
    private lateinit var containerParadasMapa: LinearLayout
    private lateinit var rotaRepository: RotaRepository

    private var rotaAtual: RotaEntity? = null
    private var paradasAtuais: List<ParadaRotaEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rota_calculada)

        rotaId = intent.getStringExtra(EXTRA_ROTA_ID) ?: run { finish(); return }

        // Inicialização do Banco e Repositório no ciclo de vida correto
        val db = VaivanDatabase.getInstance(this)
        rotaRepository = RotaRepository(
            rotaDao = db.rotaDao(),
            paradaRotaDao = db.paradaRotaDao(),
            routesClient = GoogleRoutesClient(this)
        )

        txtResumoMapa = findViewById(R.id.txtResumoMapa)
        containerParadasMapa = findViewById(R.id.containerParadasMapa)
        findViewById<View>(R.id.btnVoltarMapa).setOnClickListener { finish() }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        BottomSheetBehavior.from(findViewById<View>(R.id.painelInferior)).state =
            BottomSheetBehavior.STATE_COLLAPSED

        aplicarGapsDeSistema()
        observarDados()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        MapaEstiloUtil.aplicarEstiloClaro(this, googleMap)
        // O mapa ocupa a tela toda: o padding faz o enquadramento caber só na
        // área visível (entre a status bar e o sheet recolhido).
        val raiz = findViewById<View>(R.id.rootRotaCalculada)
        raiz.doOnLayout {
            val peek = BottomSheetBehavior.from(findViewById<View>(R.id.painelInferior)).peekHeight
            googleMap.setPadding(0, findViewById<View>(R.id.topGap).height, 0, peek)
        }
        desenharSeTiverDados()
    }

    private fun observarDados() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rotaRepository.observarRotaPorId(rotaId).collect { rota ->
                    rotaAtual = rota
                    atualizarResumo()
                    desenharSeTiverDados()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rotaRepository.observarParadas(rotaId).collect { paradas ->
                    paradasAtuais = paradas.sortedBy { it.ordem }
                    renderizarListaParadas()
                    desenharSeTiverDados()
                }
            }
        }
    }

    private fun atualizarResumo() {
        val rota = rotaAtual ?: return
        val km = rota.distanciaMetros / 1000.0
        val minutos = rota.duracaoSegundos / 60
        txtResumoMapa.text = "${rota.nome} · %.1f km · ~$minutos min (com trânsito)".format(km)
    }

    private fun renderizarListaParadas() {
        containerParadasMapa.removeAllViews()
        val inflater = layoutInflater

        paradasAtuais.forEachIndexed { index, parada ->
            val itemView = inflater.inflate(R.layout.item_parada_mapa, containerParadasMapa, false)
            itemView.findViewById<TextView>(R.id.txtOrdemParadaMapa).text = "${index + 1}"
            itemView.findViewById<TextView>(R.id.txtNomeParadaMapa).text = parada.nomePassageiro
            itemView.findViewById<TextView>(R.id.txtEnderecoParadaMapa).text = parada.endereco
            itemView.findViewById<TextView>(R.id.txtHorarioParadaMapa).text = "+${parada.horarioEstimadoMinutos} min"
            containerParadasMapa.addView(itemView)
        }
    }

    private fun desenharSeTiverDados() {
        if (!::googleMap.isInitialized) return
        val rota = rotaAtual ?: return
        if (rota.polylineEncoded.isBlank()) return

        googleMap.clear()

        // 1. Desenha a linha da rota
        val pontosPolyline = PolylineUtil.decode(rota.polylineEncoded)
        if (pontosPolyline.isNotEmpty()) {
            MapaEstiloUtil.desenharRota(this, googleMap, pontosPolyline)
        }

        val boundsBuilder = LatLngBounds.Builder()
        var temPontos = false

        // 2. Adiciona pinos numerados para cada parada, com o nome do passageiro
        val corParada = ContextCompat.getColor(this, R.color.mapa_rota)
        paradasAtuais.forEachIndexed { index, parada ->
            val ponto = LatLng(parada.latitude, parada.longitude)
            val pino = MapaEstiloUtil.criarPino(
                context = this,
                corPino = corParada,
                rotulo = parada.nomePassageiro.ifBlank { null },
                numero = "${index + 1}"
            )
            googleMap.addMarker(
                MarkerOptions()
                    .position(ponto)
                    .icon(pino.descriptor)
                    .anchor(0.5f, pino.anchorY)
                    .zIndex(3f)
            )
            boundsBuilder.include(ponto)
            temPontos = true
        }

        // 3. Adiciona marcador do destino (Escola)
        if (rota.destinoLatitude != 0.0 && rota.destinoLongitude != 0.0) {
            val destino = LatLng(rota.destinoLatitude, rota.destinoLongitude)
            val pinoEscola = MapaEstiloUtil.criarPino(
                context = this,
                corPino = ContextCompat.getColor(this, R.color.mapa_pino_escuro),
                rotulo = rota.destinoNome.ifBlank { "Escola" },
                iconeResId = R.drawable.ic_escola_generica
            )
            googleMap.addMarker(
                MarkerOptions()
                    .position(destino)
                    .icon(pinoEscola.descriptor)
                    .anchor(0.5f, pinoEscola.anchorY)
                    .zIndex(3f)
            )
            boundsBuilder.include(destino)
            temPontos = true
        }

        // 4. Enquadra a câmera nos marcadores existentes
        if (temPontos) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
        }
    }
}