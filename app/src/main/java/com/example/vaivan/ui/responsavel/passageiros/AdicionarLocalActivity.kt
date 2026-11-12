package com.example.vaivan.ui.responsavel.passageiros

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.LocalEntity
import com.example.vaivan.data.repository.LocalRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Criação de um local salvo — ou edição/exclusão, quando aberta com
 * [intentEdicao] (a partir da tela Locais salvos).
 */
class AdicionarLocalActivity : BaseActivity() {

    companion object {
        private const val EXTRA_LOCAL_ID = "local_id"
        private const val EXTRA_NOME = "local_nome"
        private const val EXTRA_ENDERECO = "local_endereco"
        private const val EXTRA_LATITUDE = "local_latitude"
        private const val EXTRA_LONGITUDE = "local_longitude"
        private const val EXTRA_PLACE_ID = "local_place_id"

        fun intentEdicao(context: Context, local: LocalEntity): Intent =
            Intent(context, AdicionarLocalActivity::class.java)
                .putExtra(EXTRA_LOCAL_ID, local.id)
                .putExtra(EXTRA_NOME, local.nome)
                .putExtra(EXTRA_ENDERECO, local.endereco)
                .putExtra(EXTRA_LATITUDE, local.latitude)
                .putExtra(EXTRA_LONGITUDE, local.longitude)
                .putExtra(EXTRA_PLACE_ID, local.placeId)
    }

    private val localRepository by lazy {
        LocalRepository(VaivanDatabase.getInstance(this).localDao())
    }

    /** Preenchido no modo edição; vazio = criando um local novo. */
    private var localId: String = ""

    // ---------------------------------------------------------
    // VIEWS
    // ---------------------------------------------------------

    private lateinit var edtNome: TextInputEditText
    private lateinit var btnExcluir: TextView

    private lateinit var btnSalvar: MaterialButton
    private lateinit var btnVoltar: ImageButton
    private lateinit var cardMapa: MaterialCardView

    private lateinit var txtEnderecoSelecionado: TextView

    private lateinit var mapPreview: MapView

    // ---------------------------------------------------------
    // FIREBASE
    // ---------------------------------------------------------

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

        setContentView(
            R.layout.activity_adicionar_local
        )

        inicializarViews()

        carregarModoEdicao()

        aplicarGapsDeSistema()

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

        btnExcluir =
            findViewById(R.id.btnExcluirLocal)
    }

    // ---------------------------------------------------------
    // MODO EDIÇÃO
    // ---------------------------------------------------------

    private fun carregarModoEdicao() {

        localId = intent.getStringExtra(EXTRA_LOCAL_ID).orEmpty()
        if (localId.isBlank()) return

        findViewById<TextView>(R.id.txtTitulo).text = "Editar local"
        btnSalvar.text = "Salvar alterações"
        btnExcluir.isVisible = true

        edtNome.setText(intent.getStringExtra(EXTRA_NOME).orEmpty())

        latitude = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN).takeUnless { it.isNaN() }
        longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN).takeUnless { it.isNaN() }
        placeId = intent.getStringExtra(EXTRA_PLACE_ID)

        val endereco = intent.getStringExtra(EXTRA_ENDERECO).orEmpty()
        txtEnderecoSelecionado.text = endereco
        txtEnderecoSelecionado.isVisible = endereco.isNotBlank()
        // O mapa mostra o ponto assim que ficar pronto (configurarMapaPreview).
    }

    private fun confirmarExclusao() {

        ConfirmacaoExclusaoHelper.confirmar(
            contexto = this,
            titulo = "Excluir local?",
            mensagem = "\"${edtNome.text?.toString()?.trim().orEmpty()}\" será removido dos seus locais salvos."
        ) {
            btnExcluir.isEnabled = false

            lifecycleScope.launch {
                try {
                    localRepository.excluirLocal(localId)

                    Toast.makeText(this@AdicionarLocalActivity, "Local excluído.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()

                } catch (e: Exception) {
                    btnExcluir.isEnabled = true
                    Toast.makeText(
                        this@AdicionarLocalActivity,
                        "Não foi possível excluir: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
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

        btnExcluir.setOnClickListener {

            confirmarExclusao()
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
    // SALVAR (Firestore + Room via LocalRepository)
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
            Toast.makeText(this, "Usuário não autenticado.", Toast.LENGTH_SHORT).show()
            return
        }

        btnSalvar.isEnabled = false

        val local = LocalEntity(
            id = localId,
            nome = nome,
            endereco = endereco,
            latitude = latitude,
            longitude = longitude,
            placeId = placeId,
            responsavelId = uid
        )

        lifecycleScope.launch {
            try {
                localRepository.salvarLocal(local)

                Toast.makeText(
                    this@AdicionarLocalActivity,
                    if (localId.isBlank()) "Local salvo com sucesso!" else "Local atualizado!",
                    Toast.LENGTH_SHORT
                ).show()

                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                btnSalvar.isEnabled = true

                Toast.makeText(
                    this@AdicionarLocalActivity,
                    "Não foi possível salvar o local: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
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
