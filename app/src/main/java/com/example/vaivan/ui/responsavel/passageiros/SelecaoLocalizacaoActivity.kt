
package com.example.vaivan.ui.responsavel.passageiros

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Locale
import java.util.concurrent.Executors

class SelecaoLocalizacaoActivity :
    BaseActivity(),
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
    private lateinit var placesClient: PlacesClient

    private lateinit var txtEndereco: TextView
    private lateinit var edtBusca: EditText
    private lateinit var cardSugestoesBusca: View
    private lateinit var containerSugestoesBusca: LinearLayout
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

    // Um token é criado ao começar a digitar e "gasto" numa única chamada de
    // fetchPlace() ao escolher um resultado — agrupa toda a sessão de busca
    // (cada tecla + a escolha final) numa cobrança só, em vez de uma por chamada.
    private var sessionToken: AutocompleteSessionToken? = null

    private val debounceHandler = Handler(Looper.getMainLooper())
    private var buscaPendente: Runnable? = null
    private var preenchendoBuscaProgramaticamente = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selecao_localizacao)

        inicializarViews()
        inicializarPlaces()
        inicializarLocalizacao()
        configurarMapa()
        configurarBotoes()

        // --------------------------------------------------
        // WINDOW INSETS
        // --------------------------------------------------

        aplicarGapsDeSistema()
    }

    private fun inicializarViews() {
        txtEndereco = findViewById(R.id.txtEndereco)
        edtBusca = findViewById(R.id.edtBusca)
        cardSugestoesBusca = findViewById(R.id.cardSugestoesBusca)
        containerSugestoesBusca = findViewById(R.id.containerSugestoesBusca)
        btnMinhaLocalizacao = findViewById(R.id.btnMinhaLocalizacao)
        btnConfirmar = findViewById(R.id.btnConfirmar)

        btnConfirmar.isEnabled = false

        edtBusca.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(texto: Editable?) {
                if (preenchendoBuscaProgramaticamente) return
                agendarBusca(texto?.toString().orEmpty())
            }
        })
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

        // Places.initialize() puro CONTINUA chamando a Places API (Legacy) nesta
        // versão do SDK, mesmo com os endpoints renomeados/o field set novo -
        // confirmado testando: a chamada aparecia no Cloud Console como "Places API"
        // (legada), não "Places API (New)". initializeWithNewPlacesApiEnabled() está
        // marcado @Deprecated pelo Google, mas ainda é o único jeito de fato ligar
        // a API New nesta versão do SDK (5.3.0). Suprime o aviso de propósito.
        Places.initializeWithNewPlacesApiEnabled(
            applicationContext,
            chave
        )

        placesClient = Places.createClient(this)
    }

    // =========================================================
    // AUTOCOMPLETE (busca própria, com sessão/campos controlados)
    // =========================================================

    private fun obterSessionToken(): AutocompleteSessionToken {
        return sessionToken ?: AutocompleteSessionToken.newInstance().also {
            sessionToken = it
        }
    }

    /** Debounce: cancela a busca anterior e só dispara uma nova requisição
     * depois que o usuário parar de digitar por um instante — evita uma
     * chamada de autocomplete por tecla. */
    private fun agendarBusca(texto: String) {
        buscaPendente?.let { debounceHandler.removeCallbacks(it) }

        if (texto.isBlank()) {
            placeIdSelecionado = null
            ocultarSugestoes()
            return
        }

        val runnable = Runnable { buscarPredicoes(texto) }
        buscaPendente = runnable
        debounceHandler.postDelayed(runnable, 350L)
    }

    private fun buscarPredicoes(texto: String) {
        if (!Places.isInitialized()) return

        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(obterSessionToken())
            .setCountries(listOf("BR"))
            .setQuery(texto)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { resposta ->
                exibirSugestoes(resposta.autocompletePredictions)
            }
            .addOnFailureListener { erro ->
                ocultarSugestoes()
                Toast.makeText(
                    this,
                    "Falha na pesquisa: ${erro.localizedMessage ?: "verifique sua conexão e a configuração do Google Places"}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun exibirSugestoes(predicoes: List<AutocompletePrediction>) {
        containerSugestoesBusca.removeAllViews()

        if (predicoes.isEmpty()) {
            ocultarSugestoes()
            return
        }

        val inflater = LayoutInflater.from(this)

        predicoes.forEach { predicao ->
            val item = inflater.inflate(
                R.layout.item_sugestao_local,
                containerSugestoesBusca,
                false
            )

            item.findViewById<TextView>(R.id.txtPrincipalSugestao).text =
                predicao.getPrimaryText(null)

            item.findViewById<TextView>(R.id.txtSecundarioSugestao).text =
                predicao.getSecondaryText(null)

            item.setOnClickListener {
                selecionarSugestao(predicao)
            }

            containerSugestoesBusca.addView(item)
        }

        cardSugestoesBusca.visibility = View.VISIBLE
    }

    private fun ocultarSugestoes() {
        cardSugestoesBusca.visibility = View.GONE
        containerSugestoesBusca.removeAllViews()
    }

    private fun selecionarSugestao(predicao: AutocompletePrediction) {
        if (buscaEmAndamento) return
        buscaEmAndamento = true

        ocultarSugestoes()
        ocultarTeclado()

        // Só os 4 campos realmente usados: ID (vinculado ao local), DISPLAY_NAME/
        // FORMATTED_ADDRESS (texto mostrado) e LOCATION (pino no mapa) — pedir
        // campos extras (telefone, horários, avaliações...) custa mais caro por
        // sessão e nada mais aqui os usa.
        val request = FetchPlaceRequest.builder(
            predicao.placeId,
            listOf(
                Place.Field.ID,
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.LOCATION
            )
        )
            .setSessionToken(obterSessionToken())
            .build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { resposta ->
                buscaEmAndamento = false

                // O token foi gasto nesse fetchPlace; a próxima busca cria um novo.
                sessionToken = null

                val lugar = resposta.place
                val latLng = lugar.location

                if (latLng == null) {
                    Toast.makeText(
                        this,
                        "Não foi possível localizar esse resultado. Tente outro endereço.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                placeIdSelecionado = lugar.id

                preenchendoBuscaProgramaticamente = true
                edtBusca.setText(lugar.displayName ?: lugar.formattedAddress ?: "Local selecionado")
                preenchendoBuscaProgramaticamente = false

                if (::googleMap.isInitialized) {
                    localSelecionado = latLng
                    posicionarLocalNaPinca(latLng)
                }
            }
            .addOnFailureListener { erro ->
                buscaEmAndamento = false
                sessionToken = null

                Toast.makeText(
                    this,
                    "Falha ao carregar o local: ${erro.localizedMessage ?: "tente novamente"}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun ocultarTeclado() {
        edtBusca.clearFocus()
        val gerenciador = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        gerenciador?.hideSoftInputFromWindow(edtBusca.windowToken, 0)
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
                ocultarSugestoes()
                ocultarTeclado()

                preenchendoBuscaProgramaticamente = true
                edtBusca.setText("")
                preenchendoBuscaProgramaticamente = false
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
        buscaPendente?.let { debounceHandler.removeCallbacks(it) }
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
