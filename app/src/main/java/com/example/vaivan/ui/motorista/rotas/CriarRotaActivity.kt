package com.example.vaivan.ui.motorista.rotas

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.core.util.TextoUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.models.Escola
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.ui.escolas.SelecionarEscolaActivity
import com.example.vaivan.ui.responsavel.passageiros.SelecaoLocalizacaoActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class CriarRotaActivity : AppCompatActivity() {

    private lateinit var edtNomeRota: EditText
    private lateinit var edtCapacidade: EditText
    private lateinit var txtDestinoAtual: TextView
    private lateinit var btnCriarRota: Button

    private lateinit var botoesTipoTrajeto: Map<String, Button>
    private lateinit var botoesTurno: Map<String, Button>
    private lateinit var botoesDias: Map<String, Button>

    private var tipoTrajetoSelecionado: String? = null // "IDA", "VOLTA", "IDA_E_VOLTA"
    private var turnoSelecionado: String? = null
    private val diasSelecionados = mutableSetOf<String>()

    private var destinoNome: String? = null
    private var destinoLatitude: Double? = null
    private var destinoLongitude: Double? = null

    private var origemNome: String? = null
    private var origemEndereco: String? = null
    private var origemLatitude: Double? = null
    private var origemLongitude: Double? = null
    private lateinit var txtOrigemAtual: TextView
    private lateinit var containerPonto: View
    private lateinit var txtTituloPonto: TextView
    private lateinit var txtExplicacaoPonto: TextView
    private lateinit var txtResumoSentido: TextView
    private lateinit var selecaoOrigemLauncher: ActivityResultLauncher<Intent>

    private var escolaSelecionada: Escola? = null
    private lateinit var txtBairros: TextView

    // chave normalizada -> nome para mostrar na tela
    private val bairrosAtendidos = linkedMapOf<String, String>()

    private lateinit var selecaoBairroLauncher: ActivityResultLauncher<Intent>
    private lateinit var selecaoEscolaLauncher: ActivityResultLauncher<Intent>

    val db by lazy { VaivanDatabase.getInstance(this) }

    val rotaRepository by lazy {
        RotaRepository(
            rotaDao = db.rotaDao(),
            paradaRotaDao = db.paradaRotaDao(),
            routesClient = GoogleRoutesClient(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_criar_rota)

        findViewById<ImageButton>(R.id.btnVoltar).setOnClickListener { finish() }

        edtNomeRota = findViewById(R.id.edtNomeRota)
        edtCapacidade = findViewById(R.id.edtCapacidade)
        txtDestinoAtual = findViewById(R.id.txtDestinoAtual)
        txtOrigemAtual = findViewById(R.id.txtOrigemAtual)
        containerPonto = findViewById(R.id.containerPonto)
        txtTituloPonto = findViewById(R.id.txtTituloPonto)
        txtExplicacaoPonto = findViewById(R.id.txtExplicacaoPonto)
        txtResumoSentido = findViewById(R.id.txtResumoSentido)
        txtBairros = findViewById(R.id.txtBairros)
        btnCriarRota = findViewById(R.id.btnCriarRota)

        // Botoes do Sentido da Rota (Substituindo Ponto de Partida)
        botoesTipoTrajeto = mapOf(
            "IDA" to findViewById(R.id.btnIda),
            "VOLTA" to findViewById(R.id.btnVolta),
            "IDA_E_VOLTA" to findViewById(R.id.btnIdaVolta)
        )

        botoesTurno = mapOf(
            "MANHA" to findViewById(R.id.btnManha),
            "TARDE" to findViewById(R.id.btnTarde),
            "NOITE" to findViewById(R.id.btnNoite)
        )

        botoesDias = mapOf(
            "SEG" to findViewById(R.id.btnSeg),
            "TER" to findViewById(R.id.btnTer),
            "QUA" to findViewById(R.id.btnQua),
            "QUI" to findViewById(R.id.btnQui),
            "SEX" to findViewById(R.id.btnSex),
            "SAB" to findViewById(R.id.btnSab),
            "DOM" to findViewById(R.id.btnDom)
        )

        configurarTipoTrajeto()
        configurarTurno()
        configurarDias()
        configurarLaunchers()

        findViewById<Button>(R.id.btnDefinirDestino).setOnClickListener {
            selecaoEscolaLauncher.launch(Intent(this, SelecionarEscolaActivity::class.java))
        }

        findViewById<Button>(R.id.btnDefinirOrigem).setOnClickListener {
            selecaoOrigemLauncher.launch(Intent(this, SelecaoLocalizacaoActivity::class.java))
        }

        findViewById<Button>(R.id.btnAdicionarBairro).setOnClickListener {
            selecaoBairroLauncher.launch(Intent(this, SelecaoLocalizacaoActivity::class.java))
        }

        findViewById<Button>(R.id.btnLimparBairros).setOnClickListener {
            bairrosAtendidos.clear()
            atualizarTextoBairros()
        }

        btnCriarRota.setOnClickListener {
            criarRota()
        }

        applyTopAndBottomGaps(findViewById(android.R.id.content))
    }

    // ---------------------------------------------------------
    // SENTIDO DA ROTA (IDA / VOLTA / IDA E VOLTA)
    // ---------------------------------------------------------

    private fun configurarTipoTrajeto() {
        botoesTipoTrajeto.forEach { (chave, botao) ->
            botao.setOnClickListener {
                tipoTrajetoSelecionado = chave
                atualizarCoresTipoTrajeto()
                atualizarCamposDoPonto()
            }
        }
        atualizarCoresTipoTrajeto()
        atualizarCamposDoPonto()
    }

    /**
     * A escola é sempre um dos extremos da rota, então o motorista só informa UM ponto:
     *  - Ida e Ida e volta: o ponto de EMBARQUE (a escola é o desembarque)
     *  - Volta: o ponto de DESEMBARQUE (a escola é o embarque)
     * O campo só aparece depois que o sentido for escolhido.
     */
    private fun atualizarCamposDoPonto() {

        val tipo = tipoTrajetoSelecionado

        if (tipo == null) {
            containerPonto.visibility = View.GONE
            return
        }

        containerPonto.visibility = View.VISIBLE

        val botaoPonto = findViewById<Button>(R.id.btnDefinirOrigem)

        if (tipo == "VOLTA") {

            txtTituloPonto.text = "Ponto de desembarque"
            txtExplicacaoPonto.text = "Onde a van deixa os alunos na volta da escola."
            botaoPonto.text = "Selecionar ponto de desembarque no mapa"

            if (origemEndereco == null) {
                txtOrigemAtual.text = "Nenhum ponto de desembarque selecionado"
            }

        } else {

            txtTituloPonto.text = "Ponto de embarque"
            txtExplicacaoPonto.text =
                if (tipo == "IDA") "Onde a van busca os alunos para levar à escola."
                else "Onde a van busca os alunos na ida e deixa na volta."
            botaoPonto.text = "Selecionar ponto de embarque no mapa"

            if (origemEndereco == null) {
                txtOrigemAtual.text = "Nenhum ponto de embarque selecionado"
            }
        }

        txtResumoSentido.text = when (tipo) {
            "IDA" -> "Ida: embarque no ponto escolhido e desembarque na escola."
            "VOLTA" -> "Volta: embarque na escola e desembarque no ponto escolhido."
            else -> "Ida: do ponto escolhido até a escola.\nVolta: da escola de volta ao ponto escolhido."
        }
    }

    private fun atualizarCoresTipoTrajeto() {
        botoesTipoTrajeto.forEach { (chave, botao) ->
            val cor = if (chave == tipoTrajetoSelecionado) {
                ContextCompat.getColor(this, R.color.orange)
            } else {
                ContextCompat.getColor(this, R.color.light_gray)
            }
            botao.backgroundTintList = android.content.res.ColorStateList.valueOf(cor)
        }
    }

    // ---------------------------------------------------------
    // TURNO E DIAS
    // ---------------------------------------------------------

    private fun configurarTurno() {
        botoesTurno.forEach { (chave, botao) ->
            botao.setOnClickListener {
                turnoSelecionado = chave
                atualizarCoresTurno()
            }
        }
        atualizarCoresTurno()
    }

    private fun atualizarCoresTurno() {
        botoesTurno.forEach { (chave, botao) ->
            val cor = if (chave == turnoSelecionado) {
                ContextCompat.getColor(this, R.color.orange)
            } else {
                ContextCompat.getColor(this, R.color.light_gray)
            }
            botao.backgroundTintList = android.content.res.ColorStateList.valueOf(cor)
        }
    }

    private fun configurarDias() {
        botoesDias.forEach { (chave, botao) ->
            botao.setOnClickListener {
                if (diasSelecionados.contains(chave)) {
                    diasSelecionados.remove(chave)
                } else {
                    diasSelecionados.add(chave)
                }
                atualizarCoresDias()
            }
        }
        atualizarCoresDias()
    }

    private fun atualizarCoresDias() {
        botoesDias.forEach { (chave, botao) ->
            val cor = if (diasSelecionados.contains(chave)) {
                ContextCompat.getColor(this, R.color.orange)
            } else {
                ContextCompat.getColor(this, R.color.light_gray)
            }
            botao.backgroundTintList = android.content.res.ColorStateList.valueOf(cor)
        }
    }

    // ---------------------------------------------------------
    // LAUNCHERS
    // ---------------------------------------------------------

    private fun configurarLaunchers() {
        // Mapa aberto para escolher o PONTO DE PARTIDA (ponto A)
        selecaoOrigemLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val dados = extrairResultado(result) ?: return@registerForActivityResult

            origemEndereco = dados.first
            origemLatitude = dados.second
            origemLongitude = dados.third
            origemNome = result.data
                ?.getStringExtra(SelecaoLocalizacaoActivity.EXTRA_BAIRRO)
                .orEmpty()
                .ifBlank { dados.first }

            txtOrigemAtual.text = dados.first
        }

        // Tela de busca de escolas (lista oficial, não permite cadastrar)
        selecaoEscolaLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            SelecionarEscolaActivity.extrairEscola(result.data)?.let { escolherEscola(it) }
        }

        // Mapa aberto para ADICIONAR UM BAIRRO
        selecaoBairroLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            val bairro = data.getStringExtra(SelecaoLocalizacaoActivity.EXTRA_BAIRRO).orEmpty()
            val cidade = data.getStringExtra(SelecaoLocalizacaoActivity.EXTRA_CIDADE).orEmpty()

            if (bairro.isBlank()) {
                Toast.makeText(
                    this,
                    "Não foi possível identificar o bairro. Toque em outro ponto dele.",
                    Toast.LENGTH_LONG
                ).show()
                return@registerForActivityResult
            }

            bairrosAtendidos[TextoUtil.chaveBairro(bairro, cidade)] = bairro
            atualizarTextoBairros()
        }
    }

    private fun extrairResultado(result: androidx.activity.result.ActivityResult): Triple<String, Double, Double>? {
        if (result.resultCode != Activity.RESULT_OK) return null
        val data = result.data ?: return null
        val endereco = data.getStringExtra(SelecaoLocalizacaoActivity.EXTRA_ENDERECO)
        val lat = data.getDoubleExtra(SelecaoLocalizacaoActivity.EXTRA_LATITUDE, Double.NaN)
        val lng = data.getDoubleExtra(SelecaoLocalizacaoActivity.EXTRA_LONGITUDE, Double.NaN)
        if (endereco.isNullOrBlank() || lat.isNaN() || lng.isNaN()) return null
        return Triple(endereco, lat, lng)
    }

    private fun atualizarTextoBairros() {
        txtBairros.text =
            if (bairrosAtendidos.isEmpty()) "Nenhum bairro adicionado"
            else bairrosAtendidos.values.joinToString(", ")
    }

    private fun escolherEscola(escola: Escola) {
        escolaSelecionada = escola
        destinoNome = escola.nome
        destinoLatitude = escola.latitude
        destinoLongitude = escola.longitude
        txtDestinoAtual.text = escola.nome
    }

    // ---------------------------------------------------------
    // CRIAÇÃO DA ROTA
    // ---------------------------------------------------------

    private fun criarRota() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sessão expirada.", Toast.LENGTH_SHORT).show()
            return
        }

        val nome = edtNomeRota.text?.toString()?.trim() ?: ""
        if (nome.length < 3) {
            Toast.makeText(this, "Informe um nome para a rota.", Toast.LENGTH_SHORT).show()
            return
        }

        val tipoTrajeto = tipoTrajetoSelecionado
        if (tipoTrajeto == null) {
            Toast.makeText(this, "Selecione o sentido da rota (Ida, Volta ou Ida e Volta).", Toast.LENGTH_SHORT).show()
            return
        }

        val oEndereco = origemEndereco
        val oLat = origemLatitude
        val oLng = origemLongitude
        if (oEndereco == null || oLat == null || oLng == null) {
            val ponto = if (tipoTrajeto == "VOLTA") "desembarque" else "embarque"
            Toast.makeText(this, "Selecione o ponto de $ponto.", Toast.LENGTH_SHORT).show()
            return
        }

        val escola = escolaSelecionada
        val dNome = destinoNome; val dLat = destinoLatitude; val dLng = destinoLongitude
        if (escola == null || dNome == null || dLat == null || dLng == null) {
            Toast.makeText(this, "Escolha a escola.", Toast.LENGTH_SHORT).show()
            return
        }

        if (bairrosAtendidos.isEmpty()) {
            Toast.makeText(this, "Adicione ao menos um bairro atendido.", Toast.LENGTH_SHORT).show()
            return
        }

        val turno = turnoSelecionado
        if (turno == null) {
            Toast.makeText(this, "Selecione um turno.", Toast.LENGTH_SHORT).show()
            return
        }

        if (diasSelecionados.isEmpty()) {
            Toast.makeText(this, "Selecione ao menos um dia da semana.", Toast.LENGTH_SHORT).show()
            return
        }

        val capacidade = edtCapacidade.text?.toString()?.trim()?.toIntOrNull()
        if (capacidade == null || capacidade <= 0) {
            Toast.makeText(this, "Informe uma capacidade válida.", Toast.LENGTH_SHORT).show()
            return
        }

        btnCriarRota.isEnabled = false

        lifecycleScope.launch {
            try {
                val rota = rotaRepository.criarRota(
                    motoristaId = uid,
                    veiculoId = null,
                    nome = nome,
                    tipoTrajeto = tipoTrajeto,
                    origemNome = origemNome ?: oEndereco,
                    origemEndereco = oEndereco,
                    origemLatitude = oLat,
                    origemLongitude = oLng,
                    destinoNome = dNome,
                    destinoEndereco = escola.endereco,
                    destinoLatitude = dLat,
                    destinoLongitude = dLng,
                    turno = turno,
                    diasSemana = diasSelecionados.toList(),
                    capacidadeTotal = capacidade,
                    escolaId = escola.id,
                    escolaNome = escola.nome,
                    bairrosAtendidos = bairrosAtendidos.keys.toList()
                )
                Toast.makeText(this@CriarRotaActivity, "Rota criada!", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this@CriarRotaActivity, VisualizarRotaActivity::class.java)
                        .putExtra(VisualizarRotaActivity.EXTRA_ROTA_ID, rota.id)
                )
                finish()
            } catch (e: Exception) {
                btnCriarRota.isEnabled = true
                Toast.makeText(this@CriarRotaActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}