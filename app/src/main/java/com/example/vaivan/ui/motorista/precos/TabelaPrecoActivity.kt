package com.example.vaivan.ui.motorista.precos

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.models.TabelaPrecoMotorista
import com.example.vaivan.data.repository.TabelaPrecoRepository
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Tabela de preços do motorista: preenchida uma vez, vale para todas as rotas dele.
 */
class TabelaPrecoActivity : AppCompatActivity() {

    private val repository = TabelaPrecoRepository()

    private lateinit var camposPreco: List<EditText>   // 1 a 5 dias
    private lateinit var edtPercentualUmTrecho: EditText
    private lateinit var edtKmIncluidos: EditText
    private lateinit var edtValorKmExtra: EditText
    private lateinit var edtDiaVencimento: EditText
    private lateinit var btnSalvar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tabela_preco)

        AssinaturaUi.aplicarInsets(findViewById(R.id.rootTabelaPreco))
        findViewById<ImageButton>(R.id.btnVoltarTabelaPreco).setOnClickListener { finish() }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        camposPreco = listOf(
            findViewById(R.id.edtPreco1Dia),
            findViewById(R.id.edtPreco2Dias),
            findViewById(R.id.edtPreco3Dias),
            findViewById(R.id.edtPreco4Dias),
            findViewById(R.id.edtPreco5Dias)
        )
        edtPercentualUmTrecho = findViewById(R.id.edtPercentualUmTrecho)
        edtKmIncluidos = findViewById(R.id.edtKmIncluidos)
        edtValorKmExtra = findViewById(R.id.edtValorKmExtra)
        edtDiaVencimento = findViewById(R.id.edtDiaVencimento)
        btnSalvar = findViewById(R.id.btnSalvarTabelaPreco)

        btnSalvar.setOnClickListener { salvar(uid) }

        carregar(uid)
    }

    /** Se o motorista já tem tabela, preenche os campos com ela. */
    private fun carregar(uid: String) {
        btnSalvar.isEnabled = false
        lifecycleScope.launch {
            try {
                repository.buscar(uid)?.let { preencher(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar a tabela", e)
                Toast.makeText(this@TabelaPrecoActivity, "Não foi possível carregar sua tabela.", Toast.LENGTH_SHORT).show()
            } finally {
                btnSalvar.isEnabled = true
            }
        }
    }

    private fun preencher(tabela: TabelaPrecoMotorista) {
        tabela.precoPorDiasCentavos.take(5).forEachIndexed { i, valor ->
            if (valor > 0) camposPreco[i].setText(MensalidadePolicy.centavosParaTexto(valor))
        }
        edtPercentualUmTrecho.setText(tabela.percentualUmTrecho.toString())
        edtKmIncluidos.setText(tabela.kmIncluidos.toString())
        if (tabela.valorKmExtraCentavos > 0) {
            edtValorKmExtra.setText(MensalidadePolicy.centavosParaTexto(tabela.valorKmExtraCentavos))
        }
        edtDiaVencimento.setText(tabela.diaVencimento.toString())
    }

    private fun salvar(uid: String) {
        val precos = camposPreco.map { MensalidadePolicy.textoParaCentavos(it.text?.toString().orEmpty()) }
        if (precos.any { it == null || it <= 0 }) {
            aviso("Preencha os valores de 1 a 5 dias por semana.")
            return
        }

        val percentual = edtPercentualUmTrecho.text?.toString()?.trim()?.toIntOrNull()
        if (percentual == null || percentual !in 1..100) {
            aviso("O percentual de só ida/só volta deve ser de 1 a 100.")
            return
        }

        val kmIncluidos = edtKmIncluidos.text?.toString()?.trim()?.toIntOrNull()
        if (kmIncluidos == null || kmIncluidos < 0) {
            aviso("Informe os km incluídos (pode ser 0).")
            return
        }

        // Vazio = não cobra km extra
        val valorKmExtra = edtValorKmExtra.text?.toString().orEmpty().let {
            if (it.isBlank()) 0L else MensalidadePolicy.textoParaCentavos(it)
        }
        if (valorKmExtra == null) {
            aviso("Valor por km extra inválido.")
            return
        }

        val diaVencimento = edtDiaVencimento.text?.toString()?.trim()?.toIntOrNull()
        if (diaVencimento == null || diaVencimento !in 1..28) {
            aviso("O dia de vencimento deve ser entre 1 e 28.")
            return
        }

        val tabela = TabelaPrecoMotorista(
            motoristaId = uid,
            precoPorDiasCentavos = precos.map { it!! },
            percentualUmTrecho = percentual,
            kmIncluidos = kmIncluidos,
            valorKmExtraCentavos = valorKmExtra,
            diaVencimento = diaVencimento
        )

        btnSalvar.isEnabled = false
        lifecycleScope.launch {
            try {
                repository.salvar(tabela)
                aviso("Tabela de preços salva!")
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao salvar a tabela", e)
                aviso("Não foi possível salvar. Tente novamente.")
                btnSalvar.isEnabled = true
            }
        }
    }

    private fun aviso(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "TabelaPrecoActivity"
    }
}