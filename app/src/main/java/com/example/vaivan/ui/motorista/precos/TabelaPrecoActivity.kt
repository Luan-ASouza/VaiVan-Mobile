package com.example.vaivan.ui.motorista.precos

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.models.TabelaPrecoMotorista
import com.example.vaivan.data.repository.TabelaPrecoRepository
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.example.vaivan.domain.usecase.preco.PrecoPolicy
import com.example.vaivan.domain.usecase.preco.TabelaPreco
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Tabela de preços do motorista: preenchida uma vez, vale para todas as rotas dele.
 *
 * Enquanto o motorista digita, os exemplos e o simulador ("Confira um exemplo")
 * se atualizam usando o mesmo cálculo da busca do responsável (PrecoPolicy).
 */
class TabelaPrecoActivity : AppCompatActivity() {

    private val repository = TabelaPrecoRepository()

    private lateinit var camposPreco: List<EditText>   // 1 a 5 dias
    private lateinit var edtPercentualUmTrecho: EditText
    private lateinit var edtKmIncluidos: EditText
    private lateinit var edtValorKmExtra: EditText
    private lateinit var edtDiaVencimento: EditText
    private lateinit var btnSalvar: Button

    private lateinit var txtExemploUmTrecho: TextView
    private lateinit var txtExemploDistancia: TextView

    // Simulador
    private lateinit var grupoSimModalidade: MaterialButtonToggleGroup
    private lateinit var grupoSimDias: MaterialButtonToggleGroup
    private lateinit var sliderSimDistancia: Slider
    private lateinit var txtSimDistancia: TextView
    private lateinit var txtSimValor: TextView
    private lateinit var txtSimConta: TextView

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

        txtExemploUmTrecho = findViewById(R.id.txtExemploUmTrecho)
        txtExemploDistancia = findViewById(R.id.txtExemploDistancia)

        grupoSimModalidade = findViewById(R.id.grupoSimModalidade)
        grupoSimDias = findViewById(R.id.grupoSimDias)
        sliderSimDistancia = findViewById(R.id.sliderSimDistancia)
        txtSimDistancia = findViewById(R.id.txtSimDistancia)
        txtSimValor = findViewById(R.id.txtSimValor)
        txtSimConta = findViewById(R.id.txtSimConta)

        // Qualquer mudança nos campos ou no simulador atualiza os exemplos
        (camposPreco + listOf(edtPercentualUmTrecho, edtKmIncluidos, edtValorKmExtra))
            .forEach { campo -> campo.doAfterTextChanged { atualizarExemplos() } }
        grupoSimModalidade.addOnButtonCheckedListener { _, _, marcado -> if (marcado) atualizarExemplos() }
        grupoSimDias.addOnButtonCheckedListener { _, _, marcado -> if (marcado) atualizarExemplos() }
        sliderSimDistancia.addOnChangeListener { _, _, _ -> atualizarExemplos() }

        btnSalvar.setOnClickListener { salvar(uid) }

        atualizarExemplos()
        carregar(uid)
    }

    // =========================================================
    // CARREGAR E SALVAR
    // =========================================================

    /** Se o motorista já tem tabela, preenche os campos com ela. */
    private fun carregar(uid: String) {
        btnSalvar.isEnabled = false
        lifecycleScope.launch {
            try {
                repository.buscar(uid)?.let { preencher(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar a tabela", e)
                aviso("Não foi possível carregar sua tabela.")
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
            aviso("Preencha o valor mensal de 1 a 5 dias por semana.")
            return
        }

        val percentual = lerPercentual()
        if (percentual == null || percentual !in 1..100) {
            aviso("Em \"Só ida ou só volta\", informe um percentual de 1 a 100.")
            return
        }

        val kmIncluidos = lerKmIncluidos()
        if (kmIncluidos == null || kmIncluidos < 0) {
            aviso("Informe até quantos km da escola o valor mensal cobre (pode ser 0).")
            return
        }

        val valorKmExtra = lerValorKmExtra()
        if (valorKmExtra == null) {
            aviso("O valor por km a mais não é válido. Use, por exemplo, 15,00.")
            return
        }

        val diaVencimento = edtDiaVencimento.text?.toString()?.trim()?.toIntOrNull()
        if (diaVencimento == null || diaVencimento !in 1..28) {
            aviso("O dia de vencimento deve ser de 1 a 28.")
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
                aviso("Tabela de preços salva.")
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao salvar a tabela", e)
                aviso("Não foi possível salvar. Verifique a internet e tente de novo.")
                btnSalvar.isEnabled = true
            }
        }
    }

    // =========================================================
    // LEITURA DOS CAMPOS
    // =========================================================

    private fun lerPercentual(): Int? = edtPercentualUmTrecho.text?.toString()?.trim()?.toIntOrNull()

    private fun lerKmIncluidos(): Int? = edtKmIncluidos.text?.toString()?.trim()?.toIntOrNull()

    /** Vazio = não cobra por distância (0). Texto inválido = null. */
    private fun lerValorKmExtra(): Long? = edtValorKmExtra.text?.toString().orEmpty().let {
        if (it.isBlank()) 0L else MensalidadePolicy.textoParaCentavos(it)
    }

    /** A tabela como está na tela agora, ou null se ainda faltar algo para calcular. */
    private fun tabelaDaTela(): TabelaPreco? {
        val precos = camposPreco.map { MensalidadePolicy.textoParaCentavos(it.text?.toString().orEmpty()) ?: 0L }
        return TabelaPreco(
            precoPorDiasCentavos = precos,
            percentualUmTrecho = lerPercentual()?.coerceIn(1, 100) ?: PERCENTUAL_PADRAO,
            kmIncluidos = lerKmIncluidos()?.coerceAtLeast(0) ?: KM_INCLUIDOS_PADRAO,
            valorKmExtraCentavos = lerValorKmExtra() ?: 0L
        ).takeIf { it.completa }
    }

    // =========================================================
    // EXEMPLOS E SIMULADOR
    // =========================================================

    private fun atualizarExemplos() {
        val valor = AssinaturaUi::formatarValor

        // --- Só ida ou só volta
        val percentual = lerPercentual()?.coerceIn(1, 100) ?: PERCENTUAL_PADRAO
        val base = MensalidadePolicy.textoParaCentavos(camposPreco[4].text?.toString().orEmpty())
            ?.takeIf { it > 0 } ?: 10_000L
        txtExemploUmTrecho.text =
            "Exemplo: com $percentual%, quem pagaria ${valor(base)} por ida e volta " +
                "paga ${valor(base * percentual / 100)} indo só de ida."

        // --- Distância
        val km = lerKmIncluidos()?.coerceAtLeast(0) ?: KM_INCLUIDOS_PADRAO
        val valorKm = lerValorKmExtra() ?: 0L
        txtExemploDistancia.text = if (valorKm > 0) {
            val mora = km + 3
            "Exemplo: quem mora a $mora km da escola passou 3 km do limite de $km km, " +
                "então paga ${valor(valorKm * 3)} a mais por mês.\n" +
                "A distância é medida em linha reta, da casa até a escola."
        } else {
            "Deixe em branco se a distância não muda o seu preço. " +
                "Assim, todos pagam só o valor mensal, morem perto ou longe."
        }

        atualizarSimulacao()
    }

    private fun atualizarSimulacao() {
        val km = sliderSimDistancia.value.toInt()
        txtSimDistancia.text = "Mora a $km km da escola"

        val tabela = tabelaDaTela()
        if (tabela == null) {
            txtSimValor.text = "R$ —"
            txtSimConta.text = "Preencha o valor mensal de 1 a 5 dias para ver o exemplo."
            return
        }

        val modalidade =
            if (grupoSimModalidade.checkedButtonId == R.id.btnSimUmTrecho) PrecoPolicy.IDA
            else PrecoPolicy.IDA_E_VOLTA

        val dias = when (grupoSimDias.checkedButtonId) {
            R.id.btnSimDia1 -> 1
            R.id.btnSimDia2 -> 2
            R.id.btnSimDia3 -> 3
            R.id.btnSimDia4 -> 4
            else -> 5
        }

        val o = PrecoPolicy.calcular(tabela, modalidade, dias, km * 1000.0)
        val valor = AssinaturaUi::formatarValor

        txtSimValor.text = valor(o.valorCentavos)

        val linhas = mutableListOf<String>()
        linhas += "${if (dias == 1) "1 dia" else "$dias dias"} por semana: ${valor(o.valorDiasCentavos)}"
        linhas += if (o.kmExtras > 0) {
            "Mora ${o.kmExtras} km além dos ${tabela.kmIncluidos} km: + ${valor(o.valorDistanciaCentavos)}"
        } else {
            "Mora dentro dos ${tabela.kmIncluidos} km: sem valor a mais"
        }
        if (o.percentual < 100) {
            linhas += "Só ida ou só volta: paga ${o.percentual}% do total"
        }
        txtSimConta.text = linhas.joinToString("\n")
    }

    private fun aviso(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "TabelaPrecoActivity"
        const val PERCENTUAL_PADRAO = 60
        const val KM_INCLUIDOS_PADRAO = 5
    }
}
