package com.example.vaivan.ui.responsavel.passageiros

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopGap
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import java.util.Locale

class SelecaoLocalizacaoActivity :
    AppCompatActivity(),
    OnMapReadyCallback {

    companion object {
        private const val REQUEST_LOCATION = 100

        const val EXTRA_ENDERECO = "endereco"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_PLACE_ID = "placeId"
    }

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var marcador: Marker? = null

    private var localSelecionado: LatLng? = null
    private var enderecoSelecionado: String = ""

    private var placeIdSelecionado: String? = null

    private lateinit var txtEndereco: TextView
    private lateinit var btnMinhaLocalizacao: MaterialButton
    private lateinit var btnConfirmar: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_selecao_localizacao)

        inicializarViews()
        inicializarLocalizacao()
        configurarMapa()
        configurarBotoes()

        applyTopGap(
            findViewById(android.R.id.content)
        )
    }

    // ---------------------------------------------------------
    // VIEWS
    // ---------------------------------------------------------

    private fun inicializarViews() {

        txtEndereco = findViewById(R.id.txtEndereco)
        btnMinhaLocalizacao = findViewById(R.id.btnMinhaLocalizacao)
        btnConfirmar = findViewById(R.id.btnConfirmar)

        btnConfirmar.isEnabled = false
    }

    // ---------------------------------------------------------
    // LOCALIZAÇÃO
    // ---------------------------------------------------------

    private fun inicializarLocalizacao() {

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)
    }

    // ---------------------------------------------------------
    // MAPA
    // ---------------------------------------------------------

    private fun configurarMapa() {

        val mapFragment =
            supportFragmentManager.findFragmentById(
                R.id.map
            ) as SupportMapFragment

        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {

        googleMap = map

        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        googleMap.setOnMapClickListener { latLng ->

            selecionarLocalizacao(latLng)
        }

        verificarPermissaoLocalizacao()
    }

    // ---------------------------------------------------------
    // PERMISSÃO
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // LOCALIZAÇÃO ATUAL
    // ---------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun ativarLocalizacaoNoMapa() {

        if (!temPermissaoLocalizacao()) {
            return
        }

        googleMap.isMyLocationEnabled = true
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
                        "Não foi possível obter sua localização.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                val latLng = LatLng(
                    location.latitude,
                    location.longitude
                )

                selecionarLocalizacao(latLng)

                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        latLng,
                        17f
                    )
                )
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "Erro ao obter localização.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // ---------------------------------------------------------
    // SELEÇÃO DO LOCAL
    // ---------------------------------------------------------

    private fun selecionarLocalizacao(
        latLng: LatLng
    ) {

        localSelecionado = latLng

        marcador?.remove()

        marcador = googleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Ponto selecionado")
        )

        marcador?.showInfoWindow()

        btnConfirmar.isEnabled = false

        obterEndereco(latLng)
    }

    // ---------------------------------------------------------
    // GEOCODER
    // ---------------------------------------------------------

    private fun obterEndereco(
        latLng: LatLng
    ) {

        txtEndereco.text = "Obtendo endereço..."

        val geocoder = Geocoder(
            this,
            Locale.getDefault()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            geocoder.getFromLocation(
                latLng.latitude,
                latLng.longitude,
                1
            ) { enderecos ->

                val endereco =
                    enderecos
                        .firstOrNull()
                        ?.getAddressLine(0)

                atualizarEndereco(endereco)
            }

        } else {

            @Suppress("DEPRECATION")
            try {

                val enderecos =
                    geocoder.getFromLocation(
                        latLng.latitude,
                        latLng.longitude,
                        1
                    )

                val endereco =
                    enderecos
                        ?.firstOrNull()
                        ?.getAddressLine(0)

                atualizarEndereco(endereco)

            } catch (e: Exception) {

                runOnUiThread {

                    txtEndereco.text =
                        "Não foi possível obter o endereço."

                    btnConfirmar.isEnabled = false
                }
            }
        }
    }

    private fun atualizarEndereco(
        endereco: String?
    ) {

        enderecoSelecionado =
            endereco ?: ""

        runOnUiThread {

            if (enderecoSelecionado.isNotEmpty()) {

                txtEndereco.text =
                    enderecoSelecionado

                btnConfirmar.isEnabled = true

            } else {

                txtEndereco.text =
                    "Endereço não encontrado"

                btnConfirmar.isEnabled = false
            }
        }
    }

    // ---------------------------------------------------------
    // BOTÕES
    // ---------------------------------------------------------

    private fun configurarBotoes() {

        btnMinhaLocalizacao.setOnClickListener {

            obterLocalizacaoAtual()
        }

        btnConfirmar.setOnClickListener {

            devolverLocalSelecionado()
        }
    }

    // ---------------------------------------------------------
    // DEVOLVER PARA ADICIONAR LOCAL
    // ---------------------------------------------------------

    private fun devolverLocalSelecionado() {

        val local = localSelecionado

        if (local == null) {

            Toast.makeText(
                this,
                "Selecione um local no mapa.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (enderecoSelecionado.isBlank()) {

            Toast.makeText(
                this,
                "Não foi possível obter o endereço.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val resultado = Intent().apply {

            putExtra(
                EXTRA_ENDERECO,
                enderecoSelecionado
            )

            putExtra(
                EXTRA_LATITUDE,
                local.latitude
            )

            putExtra(
                EXTRA_LONGITUDE,
                local.longitude
            )

            putExtra(
                EXTRA_PLACE_ID,
                placeIdSelecionado
            )
        }

        setResult(
            Activity.RESULT_OK,
            resultado
        )

        finish()
    }

    // ---------------------------------------------------------
    // PERMISSÃO
    // ---------------------------------------------------------

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

        if (requestCode != REQUEST_LOCATION) {
            return
        }

        val permissaoConcedida =
            grantResults.any {
                it == PackageManager.PERMISSION_GRANTED
            }

        if (permissaoConcedida) {

            if (::googleMap.isInitialized) {

                ativarLocalizacaoNoMapa()
                obterLocalizacaoAtual()
            }

        } else {

            Toast.makeText(
                this,
                "Permissão de localização necessária.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}