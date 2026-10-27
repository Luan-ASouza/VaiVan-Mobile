
package com.example.vaivan.ui.responsavel.passageiros

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.core.util.SystemBarUtils.applyTopGap
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import java.util.concurrent.Executors

class SelecaoLocalizacaoActivity :
    AppCompatActivity(),
    OnMapReadyCallback {

    companion object {
        private const val REQUEST_LOCATION = 100

        const val EXTRA_ENDERECO = "endereco"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_PLACE_ID = "placeId"
        const val EXTRA_BAIRRO = "bairro"
        const val EXTRA_CIDADE = "cidade"
    }

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var txtEndereco: TextView
    private lateinit var txtBusca: TextView
    private lateinit var btnMinhaLocalizacao: FloatingActionButton
    private lateinit var btnConfirmar: MaterialButton

    private var localSelecionado: LatLng? = null
    private var enderecoSelecionado = ""
    private var bairroSelecionado = ""
    private var cidadeSelecionada = ""
    private var placeIdSelecionado: String? = null

    private var geocodeToken = 0
    private var buscaEmAndamento = false

    private val executor = Executors.newSingleThreadExecutor()

    private val buscaEnderecoLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            when (result.resultCode) {

                Activity.RESULT_OK -> {
                    val data = result.data ?: return@registerForActivityResult

                    val lugar = Autocomplete.getPlaceFromIntent(data)
                    val latLng = lugar.location

                    if (latLng == null) {
                        Toast.makeText(
                            this,
                            "Não foi possível localizar esse resultado. Tente outro endereço.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@registerForActivityResult
                    }

                    placeIdSelecionado = lugar.id
                    txtBusca.text = lugar.name ?: lugar.address ?: "Local selecionado"

                    if (::googleMap.isInitialized) {
                        localSelecionado = latLng
                        posicionarLocalNaPinca(latLng)
                    }
                }

                AutocompleteActivity.RESULT_ERROR -> {
                    val data = result.data
                    val status = data?.let {
                        Autocomplete.getStatusFromIntent(it)
                    }

                    Toast.makeText(
                        this,
                        "Falha na pesquisa: ${
                            status?.statusMessage ?: "verifique sua conexão e a configuração do Google Places"
                        }",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_selecao_localizacao)

        inicializarViews()
        inicializarPlaces()
        inicializarLocalizacao()
        configurarMapa()
        configurarBotoes()

        // --------------------------------------------------
        // WINDOW INSETS
        // --------------------------------------------------

        WindowCompat.setDecorFitsSystemWindows(window, false)

        applyTopAndBottomGaps(
            findViewById(android.R.id.content)
        )
    }

    private fun inicializarViews() {
        txtEndereco = findViewById(R.id.txtEndereco)
        txtBusca = findViewById(R.id.txtBusca)
        btnMinhaLocalizacao = findViewById(R.id.btnMinhaLocalizacao)
        btnConfirmar = findViewById(R.id.btnConfirmar)

        btnConfirmar.isEnabled = false

        findViewById<MaterialCardView>(R.id.cardBusca)
            .setOnClickListener {
                abrirBuscaEndereco()
            }
    }

    @Suppress("DEPRECATION")
    private fun inicializarPlaces() {
        if (Places.isInitialized()) return

        val chave = try {
            packageManager
                .getApplicationInfo(
                    packageName,
                    PackageManager.GET_META_DATA
                )
                .metaData
                ?.getString("com.google.android.geo.API_KEY")
                .orEmpty()
        } catch (_: Exception) {
            ""
        }

        if (chave.isBlank()) {
            Toast.makeText(
                this,
                "Chave da API do Google não encontrada.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Places.initializeWithNewPlacesApiEnabled(
            applicationContext,
            chave
        )
    }

    private fun abrirBuscaEndereco() {
        if (!Places.isInitialized()) {
            Toast.makeText(
                this,
                "Pesquisa indisponível. Verifique a chave do Google Places.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = try {
            Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY,
                listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.ADDRESS,
                    Place.Field.LOCATION
                )
            )
                .setCountries(listOf("BR"))
                .build(this)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Não foi possível abrir a pesquisa: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        buscaEnderecoLauncher.launch(intent)
    }

    private fun inicializarLocalizacao() {
        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)
    }

    private fun configurarMapa() {
        val fragment = supportFragmentManager
            .findFragmentById(R.id.map) as? SupportMapFragment

        if (fragment == null) {
            Toast.makeText(
                this,
                "Não foi possível carregar o mapa.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        fragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.uiSettings.isZoomControlsEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = false
        googleMap.uiSettings.isCompassEnabled = false
        googleMap.uiSettings.isMapToolbarEnabled = false

        googleMap.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                placeIdSelecionado = null
                txtBusca.text = "Buscar endereço ou lugar"
            }
        }

        googleMap.setOnCameraIdleListener {
            val pin = findViewById<android.widget.ImageView>(R.id.imgPinCentral)
            val mapView = findViewById<android.view.View>(R.id.map)

            val posicaoPin = IntArray(2)
            val posicaoMapa = IntArray(2)

            pin.getLocationOnScreen(posicaoPin)
            mapView.getLocationOnScreen(posicaoMapa)

            val x = posicaoPin[0] + pin.width / 2 - posicaoMapa[0]
            val y = posicaoPin[1] + pin.height * 51 / 52 - posicaoMapa[1]

            val ponto = googleMap.projection.fromScreenLocation(
                android.graphics.Point(x, y)
            )

            atualizarLocalPeloCentro(ponto)
        }

        verificarPermissaoLocalizacao()
    }

    private fun atualizarLocalPeloCentro(latLng: LatLng) {
        localSelecionado = latLng
        btnConfirmar.isEnabled = false

        obterEndereco(latLng)
    }

    private fun obterEndereco(latLng: LatLng) {
        val token = ++geocodeToken
        txtEndereco.text = "Identificando endereço..."
        btnConfirmar.isEnabled = false

        val geocoder = Geocoder(this, Locale("pt", "BR"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(
                latLng.latitude,
                latLng.longitude,
                1
            ) { enderecos ->
                runOnUiThread {
                    if (token == geocodeToken) {
                        processarEndereco(enderecos.firstOrNull())
                    }
                }
            }
        } else {
            executor.execute {
                val endereco = try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(
                        latLng.latitude,
                        latLng.longitude,
                        1
                    )?.firstOrNull()
                } catch (_: Exception) {
                    null
                }

                runOnUiThread {
                    if (token == geocodeToken) {
                        processarEndereco(endereco)
                    }
                }
            }
        }
    }

    private fun processarEndereco(address: Address?) {
        if (address == null) {
            enderecoSelecionado = ""
            bairroSelecionado = ""
            cidadeSelecionada = ""
            txtEndereco.text = "Endereço não encontrado. Mova o mapa ou tente outro local."
            btnConfirmar.isEnabled = false
            return
        }

        enderecoSelecionado = address.getAddressLine(0).orEmpty()
        bairroSelecionado = address.subLocality
            ?: address.locality.orEmpty()
        cidadeSelecionada = address.locality
            ?: address.subAdminArea.orEmpty()

        if (enderecoSelecionado.isBlank()) {
            txtEndereco.text = "Não foi possível identificar esse endereço."
            btnConfirmar.isEnabled = false
            return
        }

        txtEndereco.text = enderecoSelecionado
        btnConfirmar.isEnabled = true
    }

    private fun verificarPermissaoLocalizacao() {
        if (temPermissaoLocalizacao()) {
            ativarLocalizacaoNoMapa()
            obterLocalizacaoAtual()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION
            )
        }
    }

    private fun temPermissaoLocalizacao(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun ativarLocalizacaoNoMapa() {
        if (temPermissaoLocalizacao()) {
            googleMap.isMyLocationEnabled = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun obterLocalizacaoAtual() {
        if (!temPermissaoLocalizacao()) {
            verificarPermissaoLocalizacao()
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location == null) {
                    Toast.makeText(
                        this,
                        "Localização indisponível. Verifique se o GPS está ativo.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                placeIdSelecionado = null

                val latLng = LatLng(
                    location.latitude,
                    location.longitude
                )

                posicionarLocalNaPinca(latLng)
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Não foi possível obter sua localização.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun configurarBotoes() {
        btnMinhaLocalizacao.setOnClickListener {
            obterLocalizacaoAtual()
        }

        btnConfirmar.setOnClickListener {
            devolverLocalSelecionado()
        }
    }

    private fun devolverLocalSelecionado() {
        val local = localSelecionado

        if (local == null || enderecoSelecionado.isBlank()) {
            Toast.makeText(
                this,
                "Aguarde a identificação do endereço.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val resultado = Intent().apply {
            putExtra(EXTRA_ENDERECO, enderecoSelecionado)
            putExtra(EXTRA_LATITUDE, local.latitude)
            putExtra(EXTRA_LONGITUDE, local.longitude)
            putExtra(EXTRA_PLACE_ID, placeIdSelecionado)
            putExtra(EXTRA_BAIRRO, bairroSelecionado)
            putExtra(EXTRA_CIDADE, cidadeSelecionada)
        }

        setResult(Activity.RESULT_OK, resultado)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode != REQUEST_LOCATION) return

        if (grantResults.any {
                it == PackageManager.PERMISSION_GRANTED
            }) {
            if (::googleMap.isInitialized) {
                ativarLocalizacaoNoMapa()
                obterLocalizacaoAtual()
            }
        } else {
            Toast.makeText(
                this,
                "Você pode escolher o local movendo o mapa.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun posicionarLocalNaPinca(
        latLng: LatLng,
        zoom: Float = 17f
    ) {
        val mapView = findViewById<android.view.View>(R.id.map)
        val pin = findViewById<android.view.View>(R.id.imgPinCentral)

        val posicaoPin = IntArray(2)
        val posicaoMapa = IntArray(2)

        pin.getLocationOnScreen(posicaoPin)
        mapView.getLocationOnScreen(posicaoMapa)

        val x = posicaoPin[0] + pin.width / 2 - posicaoMapa[0]
        val y = posicaoPin[1] + pin.height * 51 / 52 - posicaoMapa[1]

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, zoom)
        )

        mapView.post {
            val pontoNaTela = googleMap.projection.toScreenLocation(latLng)

            googleMap.animateCamera(
                CameraUpdateFactory.scrollBy(
                    (pontoNaTela.x - x).toFloat(),
                    (pontoNaTela.y - y).toFloat()
                )
            )
        }
    }
}
