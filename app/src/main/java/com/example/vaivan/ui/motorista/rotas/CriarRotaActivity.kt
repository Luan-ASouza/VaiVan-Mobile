package com.example.vaivan.ui.motorista.rotas

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.core.util.TextoUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.models.Escola
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.EscolaRepository
import com.example.vaivan.data.repository.RotaRepository
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

    private var escolaSelecionada: Escola? = null
    private lateinit var txtBairros: TextView

    // chave normalizada -> nome para mostrar na tela
    private val bairrosAtendidos = linkedMapOf<String, String>()

    private val escolaRepository = EscolaRepository()
    private lateinit var selecaoBairroLauncher: ActivityResultLauncher<Intent>
    private lateinit var selecaoDestinoLauncher: ActivityResultLauncher<Intent>

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
            mostrarDialogEscolas()
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
            }
        }
        atualizarCoresTipoTrajeto()
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
        // Mapa aberto para CADASTRAR UMA NOVA ESCOLA
        selecaoDestinoLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val dados = extrairResultado(result) ?: return@registerForActivityResult
            val cidade = result.data
                ?.getStringExtra(SelecaoLocalizacaoActivity.EXTRA_CIDADE)
                .orEmpty()

            pedirNomeDaEscola(dados.first, cidade, dados.second, dados.third)
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

    private fun mostrarDialogEscolas() {
        lifecycleScope.launch {
            try {
                val escolas = escolaRepository.listarEscolas()
                val itens = escolas.map { it.nome } + "+ Cadastrar nova escola"

                AlertDialog.Builder(this@CriarRotaActivity)
                    .setTitle("Escolha a escola")
                    .setItems(itens.toTypedArray()) { _, posicao ->
                        if (posicao < escolas.size) {
                            escolherEscola(escolas[posicao])
                        } else {
                            selecaoDestinoLauncher.launch(
                                Intent(this@CriarRotaActivity, SelecaoLocalizacaoActivity::class.java)
                            )
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@CriarRotaActivity,
                    "Erro ao carregar escolas: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun pedirNomeDaEscola(
        endereco: String,
        cidade: String,
        latitude: Double,
        longitude: Double
    ) {
        val campo = EditText(this).apply {
            hint = "Nome da escola (ex: La Salle Carmo)"
        }

        AlertDialog.Builder(this)
            .setTitle("Nome da escola")
            .setMessage(endereco)
            .setView(campo)
            .setPositiveButton("Cadastrar") { _, _ ->
                val nome = campo.text?.toString()?.trim().orEmpty()

                if (nome.length < 3) {
                    Toast.makeText(this, "Informe o nome da escola.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

                lifecycleScope.launch {
                    try {
                        val escola = escolaRepository.cadastrarEscola(
                            nome, endereco, cidade, latitude, longitude, uid
                        )
                        escolherEscola(escola)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@CriarRotaActivity,
                            "Erro ao cadastrar escola: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
                rotaRepository.criarRota(
                    motoristaId = uid,
                    veiculoId = null,
                    nome = nome,
                    tipoTrajeto = tipoTrajeto, // Passando tipoTrajeto no lugar de origem fixo
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
                finish()
            } catch (e: Exception) {
                btnCriarRota.isEnabled = true
                Toast.makeText(this@CriarRotaActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}