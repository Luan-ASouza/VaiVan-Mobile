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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.ui.responsavel.passageiros.SelecaoLocalizacaoActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class CriarRotaActivity : AppCompatActivity() {

    private lateinit var edtNomeRota: EditText
    private lateinit var edtCapacidade: EditText
    private lateinit var txtOrigemAtual: TextView
    private lateinit var txtDestinoAtual: TextView
    private lateinit var btnCriarRota: Button

    private lateinit var botoesTurno: Map<String, Button>
    private lateinit var botoesDias: Map<String, Button>

    private var turnoSelecionado: String? = null
    private val diasSelecionados = mutableSetOf<String>()

    private var origemNome: String? = null
    private var origemLatitude: Double? = null
    private var origemLongitude: Double? = null

    private var destinoNome: String? = null
    private var destinoLatitude: Double? = null
    private var destinoLongitude: Double? = null

    private lateinit var selecaoOrigemLauncher: ActivityResultLauncher<Intent>
    private lateinit var selecaoDestinoLauncher: ActivityResultLauncher<Intent>

    private val rotaRepository by lazy {
        val db = VaivanDatabase.getInstance(this)
        RotaRepository(this, db.rotaDao(), db.paradaRotaDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_criar_rota)

        findViewById<ImageButton>(R.id.btnVoltar).setOnClickListener { finish() }

        edtNomeRota = findViewById(R.id.edtNomeRota)
        edtCapacidade = findViewById(R.id.edtCapacidade)
        txtOrigemAtual = findViewById(R.id.txtOrigemAtual)
        txtDestinoAtual = findViewById(R.id.txtDestinoAtual)
        btnCriarRota = findViewById(R.id.btnCriarRota)

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

        configurarTurno()
        configurarDias()
        configurarLaunchers()

        findViewById<Button>(R.id.btnDefinirOrigem).setOnClickListener {
            selecaoOrigemLauncher.launch(Intent(this, SelecaoLocalizacaoActivity::class.java))
        }

        findViewById<Button>(R.id.btnDefinirDestino).setOnClickListener {
            selecaoDestinoLauncher.launch(Intent(this, SelecaoLocalizacaoActivity::class.java))
        }

        btnCriarRota.setOnClickListener { criarRota() }

        applyTopAndBottomGaps(findViewById(android.R.id.content))
    }

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

    private fun configurarLaunchers() {
        selecaoOrigemLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            extrairResultado(result)?.let { (nome, lat, lng) ->
                origemNome = nome; origemLatitude = lat; origemLongitude = lng
                txtOrigemAtual.text = nome
            }
        }

        selecaoDestinoLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            extrairResultado(result)?.let { (nome, lat, lng) ->
                destinoNome = nome; destinoLatitude = lat; destinoLongitude = lng
                txtDestinoAtual.text = nome
            }
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

        val oNome = origemNome; val oLat = origemLatitude; val oLng = origemLongitude
        if (oNome == null || oLat == null || oLng == null) {
            Toast.makeText(this, "Defina o ponto de partida.", Toast.LENGTH_SHORT).show()
            return
        }

        val dNome = destinoNome; val dLat = destinoLatitude; val dLng = destinoLongitude
        if (dNome == null || dLat == null || dLng == null) {
            Toast.makeText(this, "Defina o destino.", Toast.LENGTH_SHORT).show()
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
                    origemNome = oNome, origemLatitude = oLat, origemLongitude = oLng,
                    destinoNome = dNome, destinoEndereco = dNome, destinoLatitude = dLat, destinoLongitude = dLng,
                    turno = turno,
                    diasSemana = diasSelecionados.toList(),
                    capacidadeTotal = capacidade
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