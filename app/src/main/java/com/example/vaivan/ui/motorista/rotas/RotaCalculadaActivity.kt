package com.example.vaivan.ui.motorista.rotas

import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch

class RotaCalculadaActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_ROTA_ID = "rotaId"
    }

    private lateinit var rotaId: String
    private lateinit var googleMap: GoogleMap
    private lateinit var txtResumoMapa: TextView
    private lateinit var containerParadasMapa: LinearLayout

    private var rotaAtual: RotaEntity? = null
    private var paradasAtuais: List<ParadaRotaEntity> = emptyList()

    val db = VaivanDatabase.getInstance(this)

    val rotaRepository =
        RotaRepository(
            rotaDao = db.rotaDao(),
            paradaRotaDao = db.paradaRotaDao(),
            routesClient = GoogleRoutesClient(this)
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rota_calculada)

        rotaId = intent.getStringExtra(EXTRA_ROTA_ID) ?: run { finish(); return }

        txtResumoMapa = findViewById(R.id.txtResumoMapa)
        containerParadasMapa = findViewById(R.id.containerParadasMapa)
        findViewById<ImageButton>(R.id.btnVoltarMapa).setOnClickListener { finish() }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        observarDados()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
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

        val pontosPolyline = PolylineUtil.decode(rota.polylineEncoded)
        googleMap.addPolyline(
            PolylineOptions().addAll(pontosPolyline).width(12f).color(android.graphics.Color.parseColor("#F6B12F"))
        )

        val boundsBuilder = LatLngBounds.Builder()

        val origem = LatLng(rota.origemLatitude, rota.origemLongitude)
        googleMap.addMarker(MarkerOptions().position(origem).title("Início").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
        boundsBuilder.include(origem)

        paradasAtuais.forEach { parada ->
            val ponto = LatLng(parada.latitude, parada.longitude)
            googleMap.addMarker(MarkerOptions().position(ponto).title(parada.nomePassageiro).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)))
            boundsBuilder.include(ponto)
        }

        val destino = LatLng(rota.destinoLatitude, rota.destinoLongitude)
        googleMap.addMarker(MarkerOptions().position(destino).title(rota.destinoNome).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
        boundsBuilder.include(destino)

        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
    }
}