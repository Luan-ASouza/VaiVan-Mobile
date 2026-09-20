package com.example.vaivan.ui.responsavel.passageiros

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.vaivan.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AdicionarLocalActivity : AppCompatActivity() {

    // ---------------------------------------------------------
    // VIEWS
    // ---------------------------------------------------------

    private lateinit var edtNome: TextInputEditText

    private lateinit var btnSalvar: MaterialButton
    private lateinit var btnVoltar: ImageButton
    private lateinit var cardMapa: MaterialCardView

    private lateinit var txtEnderecoSelecionado: TextView

    private lateinit var mapPreview: MapView

    // ---------------------------------------------------------
    // FIREBASE
    // ---------------------------------------------------------

    private val firestore =
        FirebaseFirestore.getInstance()

    private val auth =
        FirebaseAuth.getInstance()

    // ---------------------------------------------------------
    // LOCAL SELECIONADO
    // ---------------------------------------------------------

    private var latitude: Double? = null
    private var longitude: Double? = null
    private var placeId: String? = null

    // ---------------------------------------------------------
    // RESULTADO DA SELEÇÃO
    // ---------------------------------------------------------

    private lateinit var selecaoLocalizacaoLauncher:
            ActivityResultLauncher<Intent>

    // ---------------------------------------------------------
    // CICLO DE VIDA
    // ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(
            R.layout.activity_adicionar_local
        )

        inicializarViews()

        configurarMapaPreview(savedInstanceState)

        configurarResultadoLocalizacao()

        configurarBotoes()
    }

    // ---------------------------------------------------------
    // VIEWS
    // ---------------------------------------------------------

    private fun inicializarViews() {

        edtNome =
            findViewById(R.id.edtNome)

        cardMapa =
            findViewById(R.id.cardMapa)

        btnSalvar =
            findViewById(R.id.btnSalvar)

        btnVoltar =
            findViewById(R.id.btnVoltar)

        txtEnderecoSelecionado =
            findViewById(R.id.txtEnderecoSelecionado)

        mapPreview =
            findViewById(R.id.mapPreview)

        // Inicialmente não existe endereço selecionado.
        txtEnderecoSelecionado.isVisible = false

        // Inicialmente o preview do mapa pode ficar oculto.
        mapPreview.isVisible = false
    }

    // ---------------------------------------------------------
    // MAPA DE PRÉVIA
    // ---------------------------------------------------------

    private fun configurarMapaPreview(
        savedInstanceState: Bundle?
    ) {

        MapsInitializer.initialize(
            applicationContext
        )

        mapPreview.onCreate(
            savedInstanceState
        )

        mapPreview.getMapAsync { googleMap ->

            // O mapa é somente uma prévia.
            googleMap.uiSettings.isZoomControlsEnabled = false
            googleMap.uiSettings.isMyLocationButtonEnabled = false
            googleMap.uiSettings.isMapToolbarEnabled = false

            // Impede interação com o mapa.
            googleMap.uiSettings.setAllGesturesEnabled(false)

            // Se já existir uma localização,
            // mostra o ponto.
            atualizarPreviewMapa(
                googleMap
            )
        }
    }

    private fun atualizarPreviewMapa(
        googleMap: com.google.android.gms.maps.GoogleMap
    ) {

        val latitudeAtual =
            latitude

        val longitudeAtual =
            longitude

        if (
            latitudeAtual == null ||
            longitudeAtual == null
        ) {
            mapPreview.isVisible = false
            return
        }

        val local =
            LatLng(
                latitudeAtual,
                longitudeAtual
            )

        googleMap.clear()

        googleMap.addMarker(
            MarkerOptions()
                .position(local)
                .title("Local selecionado")
        )

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                local,
                16f
            )
        )

        mapPreview.isVisible = true
    }

    // ---------------------------------------------------------
    // RESULTADO DA SELEÇÃO
    // ---------------------------------------------------------

    private fun configurarResultadoLocalizacao() {

        selecaoLocalizacaoLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->

                if (
                    result.resultCode !=
                    RESULT_OK
                ) {
                    return@registerForActivityResult
                }

                val data =
                    result.data
                        ?: return@registerForActivityResult

                // -------------------------------------------------
                // ENDEREÇO
                // -------------------------------------------------

                val endereco =
                    data.getStringExtra(
                        SelecaoLocalizacaoActivity.EXTRA_ENDERECO
                    )

                // -------------------------------------------------
                // LATITUDE
                // -------------------------------------------------

                val latitudeRecebida =
                    data.getDoubleExtra(
                        SelecaoLocalizacaoActivity.EXTRA_LATITUDE,
                        Double.NaN
                    )

                // -------------------------------------------------
                // LONGITUDE
                // -------------------------------------------------

                val longitudeRecebida =
                    data.getDoubleExtra(
                        SelecaoLocalizacaoActivity.EXTRA_LONGITUDE,
                        Double.NaN
                    )

                // -------------------------------------------------
                // PLACE ID
                // -------------------------------------------------

                val placeIdRecebido =
                    data.getStringExtra(
                        SelecaoLocalizacaoActivity.EXTRA_PLACE_ID
                    )

                // -------------------------------------------------
                // VALIDAÇÃO
                // -------------------------------------------------

                if (
                    endereco.isNullOrBlank() ||
                    latitudeRecebida.isNaN() ||
                    longitudeRecebida.isNaN()
                ) {

                    Toast.makeText(
                        this,
                        "Não foi possível obter o endereço selecionado.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@registerForActivityResult
                }

                // -------------------------------------------------
                // SALVA OS DADOS TEMPORARIAMENTE
                // -------------------------------------------------

                latitude =
                    latitudeRecebida

                longitude =
                    longitudeRecebida

                placeId =
                    placeIdRecebido


                txtEnderecoSelecionado.text =
                    endereco

                txtEnderecoSelecionado.isVisible =
                    true

                // -------------------------------------------------
                // ATUALIZA O MAPA
                // -------------------------------------------------

                mapPreview.getMapAsync { googleMap ->

                    atualizarPreviewMapa(
                        googleMap
                    )
                }
            }
    }

    // ---------------------------------------------------------
    // BOTÕES
    // ---------------------------------------------------------

    private fun configurarBotoes() {

        btnVoltar.setOnClickListener {

            finish()
        }

        // Clicar no cardMapa
        // abre a tela de seleção.
        cardMapa.setOnClickListener {

            abrirSelecaoLocalizacao()
        }

        btnSalvar.setOnClickListener {

            salvarLocal()
        }
    }

    // ---------------------------------------------------------
    // ABRIR SELEÇÃO DE LOCALIZAÇÃO
    // ---------------------------------------------------------

    private fun abrirSelecaoLocalizacao() {

        val intent =
            Intent(
                this,
                SelecaoLocalizacaoActivity::class.java
            )

        selecaoLocalizacaoLauncher.launch(
            intent
        )
    }

    // ---------------------------------------------------------
    // VALIDAÇÃO
    // ---------------------------------------------------------

    private fun salvarLocal() {

        val nome =
            edtNome.text
                ?.toString()
                ?.trim()

        val endereco =
            txtEnderecoSelecionado.text
                ?.toString()
                ?.trim()

        // -----------------------------------------------------
        // NOME
        // -----------------------------------------------------

        if (nome.isNullOrEmpty()) {

            edtNome.error =
                "Informe um nome para o local"

            edtNome.requestFocus()

            return
        }

        // -----------------------------------------------------
        // ENDEREÇO
        // -----------------------------------------------------

        if (endereco.isNullOrEmpty()) {

            txtEnderecoSelecionado.error =
                "Selecione um endereço"

            txtEnderecoSelecionado.requestFocus()

            return
        }

        // -----------------------------------------------------
        // COORDENADAS
        // -----------------------------------------------------

        val latitudeAtual =
            latitude

        val longitudeAtual =
            longitude

        if (
            latitudeAtual == null ||
            longitudeAtual == null
        ) {

            Toast.makeText(
                this,
                "Selecione um endereço no mapa.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // -----------------------------------------------------
        // SALVAR
        // -----------------------------------------------------

        salvarNoFirebase(
            nome = nome,
            endereco = endereco,
            latitude = latitudeAtual,
            longitude = longitudeAtual,
            placeId = placeId
        )
    }

    // ---------------------------------------------------------
    // FIREBASE
    // ---------------------------------------------------------

    private fun salvarNoFirebase(
        nome: String,
        endereco: String,
        latitude: Double,
        longitude: Double,
        placeId: String?
    ) {

        val uid = auth.currentUser?.uid

        if (uid == null) {

            Toast.makeText(
                this,
                "Usuário não autenticado.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        btnSalvar.isEnabled = false

        val local = hashMapOf(
            "nome" to nome,
            "endereco" to endereco,
            "latitude" to latitude,
            "longitude" to longitude,
            "placeId" to placeId,
            "criadoEm" to FieldValue.serverTimestamp(),
            "responsavelId" to uid
        )

        android.util.Log.d(
            "FIRESTORE_LOCAIS",
            "Tentando salvar local para UID: $uid"
        )

        firestore
            .collection("locais")
            .add(local)
            .addOnSuccessListener { documentReference ->

                android.util.Log.d(
                    "FIRESTORE_LOCAIS",
                    "LOCAL SALVO: ${documentReference.id}"
                )

                Toast.makeText(
                    this,
                    "Local salvo com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->

                android.util.Log.e(
                    "FIRESTORE_LOCAIS",
                    "ERRO AO SALVAR LOCAL",
                    e
                )

                btnSalvar.isEnabled = true

                Toast.makeText(
                    this,
                    "${e.javaClass.simpleName}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ---------------------------------------------------------
    // CICLO DE VIDA DO MAPVIEW
    // ---------------------------------------------------------

    override fun onStart() {
        super.onStart()
        mapPreview.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapPreview.onResume()
    }

    override fun onPause() {
        mapPreview.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapPreview.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        mapPreview.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapPreview.onLowMemory()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        super.onSaveInstanceState(
            outState
        )

        mapPreview.onSaveInstanceState(
            outState
        )
    }
}