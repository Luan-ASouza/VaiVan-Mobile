package com.example.vaivan.ui.motorista.rotas

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.PolylineUtil
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.data.local.VaivanDatabase
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
import java.util.Locale

class VisualizarRotaActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_ROTA_ID = "rotaId"
    }

    private lateinit var googleMap: GoogleMap
    private lateinit var txtTituloRota: TextView
    private lateinit var txtResumoRota: TextView

    private var rotaId: String = ""

    private val db by lazy { VaivanDatabase.getInstance(this) }

    private val rotaRepository by lazy {
        RotaRepository(
            rotaDao = db.rotaDao(),
            paradaRotaDao = db.paradaRotaDao(),
            routesClient = GoogleRoutesClient(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visualizar_rota)

        rotaId = intent.getStringExtra(EXTRA_ROTA_ID).orEmpty()
        if (rotaId.isBlank()) {
            Toast.makeText(this, "Rota não informada.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btnVoltar).setOnClickListener { finish() }
        txtTituloRota = findViewById(R.id.txtTituloRota)
        txtResumoRota = findViewById(R.id.txtResumoRota)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapaRota) as SupportMapFragment
        mapFragment.getMapAsync(this)

        applyTopAndBottomGaps(findViewById(android.R.id.content))
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        carregarRota()
    }

    private fun carregarRota() {
        lifecycleScope.launch {
            try {
                val rota = rotaRepository.consultarRota(rotaId)
                if (rota == null) {
                    Toast.makeText(
                        this@VisualizarRotaActivity,
                        "Rota não encontrada.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                desenharRota(rota)
            } catch (e: Exception) {
                Toast.makeText(
                    this@VisualizarRotaActivity,
                    "Erro ao carregar a rota: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun desenharRota(rota: RotaEntity) {
        txtTituloRota.text = rota.nome

        val pontos: List<LatLng> =
            if (rota.polylineEncoded.isBlank()) emptyList()
            else runCatching { PolylineUtil.decode(rota.polylineEncoded) }
                .getOrDefault(emptyList())

        if (pontos.size < 2) {
            txtResumoRota.text = "Esta rota não tem trajeto salvo."
            Toast.makeText(this, "Esta rota não tem trajeto salvo.", Toast.LENGTH_LONG).show()
            return
        }

        googleMap.addPolyline(
            PolylineOptions()
                .addAll(pontos)
                .width(14f)
                .color(ContextCompat.getColor(this, R.color.orange))
        )

        val partida =
            if (rota.origemLatitude != 0.0 || rota.origemLongitude != 0.0)
                LatLng(rota.origemLatitude, rota.origemLongitude)
            else pontos.first()

        val escola = LatLng(rota.destinoLatitude, rota.destinoLongitude)

        googleMap.addMarker(
            MarkerOptions()
                .position(partida)
                .title("Partida")
                .snippet(rota.origemEndereco)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        googleMap.addMarker(
            MarkerOptions()
                .position(escola)
                .title("Escola")
                .snippet(rota.destinoEndereco)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        val km = String.format(Locale("pt", "BR"), "%.1f km", rota.distanciaMetros / 1000.0)
        val minutos = (rota.duracaoSegundos / 60).coerceAtLeast(1)
        txtResumoRota.text = "${rotulo(rota.tipoTrajeto)} · $km · cerca de $minutos min"

        val bounds = LatLngBounds.Builder().apply {
            pontos.forEach { include(it) }
            include(partida)
            include(escola)
        }.build()

        enquadrar(bounds)
    }

    private fun enquadrar(bounds: LatLngBounds) {
        val margem = 120
        try {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, margem))
        } catch (e: IllegalStateException) {
            googleMap.setOnMapLoadedCallback {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, margem))
            }
        }
    }

    private fun rotulo(tipoTrajeto: String): String = when (tipoTrajeto) {
        "IDA" -> "Ida"
        "VOLTA" -> "Volta"
        "IDA_E_VOLTA" -> "Ida e volta"
        else -> tipoTrajeto
    }
}