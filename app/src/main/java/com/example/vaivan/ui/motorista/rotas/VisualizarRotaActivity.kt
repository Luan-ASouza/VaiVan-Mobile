package com.example.vaivan.ui.motorista.rotas

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.example.vaivan.core.util.MapaEstiloUtil
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.PolylineUtil
import com.example.vaivan.data.local.VaivanDatabase
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
import java.util.Locale

class VisualizarRotaActivity : BaseActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_ROTA_ID = "rotaId"
    }

    private lateinit var googleMap: GoogleMap
    private lateinit var txtTituloRota: TextView
    private lateinit var txtResumoRota: TextView
    private lateinit var txtPartidaRota: TextView
    private lateinit var txtDestinoRota: TextView
    private lateinit var secaoTrajetoRota: View

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

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        txtTituloRota = findViewById(R.id.txtTituloRota)
        txtResumoRota = findViewById(R.id.txtResumoRota)
        txtPartidaRota = findViewById(R.id.txtPartidaRota)
        txtDestinoRota = findViewById(R.id.txtDestinoRota)
        secaoTrajetoRota = findViewById(R.id.secaoTrajetoRota)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapaRota) as SupportMapFragment
        mapFragment.getMapAsync(this)

        aplicarGapsDeSistema()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        MapaEstiloUtil.aplicarEstiloClaro(this, googleMap)
        // O mapa ocupa a tela toda: o padding faz o enquadramento caber só na
        // área visível (entre a status bar e o painel inferior).
        val painel = findViewById<View>(R.id.painelRota)
        painel.doOnLayout {
            googleMap.setPadding(0, findViewById<View>(R.id.topGap).height, 0, painel.height)
        }
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

        MapaEstiloUtil.desenharRota(this, googleMap, pontos)

        val partida =
            if (rota.origemLatitude != 0.0 || rota.origemLongitude != 0.0)
                LatLng(rota.origemLatitude, rota.origemLongitude)
            else pontos.first()

        val escola = LatLng(rota.destinoLatitude, rota.destinoLongitude)

        val pinoPartida = MapaEstiloUtil.criarPino(
            context = this,
            corPino = ContextCompat.getColor(this, R.color.mapa_rota),
            rotulo = "Partida",
            iconeResId = R.drawable.ic_van
        )
        googleMap.addMarker(
            MarkerOptions()
                .position(partida)
                .icon(pinoPartida.descriptor)
                .anchor(0.5f, pinoPartida.anchorY)
                .zIndex(3f)
        )
        val pinoEscola = MapaEstiloUtil.criarPino(
            context = this,
            corPino = ContextCompat.getColor(this, R.color.mapa_pino_escuro),
            rotulo = rota.destinoNome.ifBlank { "Escola" },
            iconeResId = R.drawable.ic_escola_generica
        )
        googleMap.addMarker(
            MarkerOptions()
                .position(escola)
                .icon(pinoEscola.descriptor)
                .anchor(0.5f, pinoEscola.anchorY)
                .zIndex(3f)
        )

        val km = String.format(Locale("pt", "BR"), "%.1f km", rota.distanciaMetros / 1000.0)
        val minutos = (rota.duracaoSegundos / 60).coerceAtLeast(1)
        txtResumoRota.text = "${rotulo(rota.tipoTrajeto)} · $km · cerca de $minutos min"

        txtPartidaRota.text = rota.origemEndereco.ifBlank { rota.origemNome }
        txtDestinoRota.text = rota.destinoNome.ifBlank { rota.destinoEndereco }
        secaoTrajetoRota.visibility = View.VISIBLE

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