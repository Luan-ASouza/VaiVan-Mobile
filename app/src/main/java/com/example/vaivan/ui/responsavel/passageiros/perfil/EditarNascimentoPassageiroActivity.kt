package com.example.vaivan.ui.responsavel.passageiros.perfil

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.DatePicker
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.DataUtil
import com.example.vaivan.ui.comum.perfil.acompanharEdicao
import com.google.android.material.button.MaterialButton
import java.util.Locale

/** Data de nascimento do passageiro. Mesma regra do cadastro: precisa ser
 * menor de idade (maior de 18 cria a própria conta). Grava em ISO
 * "yyyy-MM-dd" (DataUtil). */
class EditarNascimentoPassageiroActivity : BaseActivity() {

    companion object {
        private const val IDADE_MAXIMA = 18

        fun intent(context: Context, passageiroId: String): Intent =
            Intent(context, EditarNascimentoPassageiroActivity::class.java)
                .putExtra(PerfilPassageiroActivity.EXTRA_PASSAGEIRO_ID, passageiroId)
    }

    private val viewModel: EditarPassageiroViewModel by viewModels {
        viewModelFactory {
            initializer {
                EditarPassageiroViewModel(
                    EditarPassageiroViewModel.criarRepository(this@EditarNascimentoPassageiroActivity),
                    intent.getStringExtra(PerfilPassageiroActivity.EXTRA_PASSAGEIRO_ID).orEmpty()
                )
            }
        }
    }

    private lateinit var datePicker: DatePicker
    private lateinit var txtErro: TextView
    private lateinit var btnSalvar: MaterialButton
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_data_nascimento)
        aplicarGapsDeSistema()

        findViewById<TextView>(R.id.txtDescricaoNascimento).text =
            "O passageiro precisa ser menor de idade. A partir dos 18 anos, ele cria a própria conta."

        datePicker = findViewById(R.id.datePickerNascimento)
        txtErro = findViewById(R.id.txtErroNascimento)
        btnSalvar = findViewById(R.id.btnSalvarEdicaoPerfil)
        progress = findViewById(R.id.progressEdicaoPerfil)

        datePicker.maxDate = System.currentTimeMillis()
        datePicker.setOnDateChangedListener { _, _, _, _ -> txtErro.visibility = View.GONE }

        btnSalvar.setOnClickListener { validarESalvar() }

        acompanharEdicao(
            viewModel = viewModel,
            aoMudarOcupado = { ocupado ->
                progress.visibility = if (ocupado) View.VISIBLE else View.GONE
                btnSalvar.text = if (ocupado) "" else "Salvar"
                btnSalvar.isEnabled = !ocupado
                datePicker.isEnabled = !ocupado
            },
            aoCarregar = { passageiro -> selecionarData(passageiro.dataNascimento) }
        )
    }

    /** "yyyy-MM-dd" → posiciona o DatePicker. */
    private fun selecionarData(dataIso: String) {
        val partes = dataIso.split("-").mapNotNull { it.toIntOrNull() }
        if (partes.size != 3) return

        val (ano, mes, dia) = partes
        datePicker.updateDate(ano, mes - 1, dia)
    }

    private fun validarESalvar() {
        val dataIso = String.format(
            Locale.ROOT,
            "%04d-%02d-%02d",
            datePicker.year,
            datePicker.month + 1,
            datePicker.dayOfMonth
        )

        if (DataUtil.calcularIdade(dataIso) >= IDADE_MAXIMA) {
            txtErro.text = "*Este passageiro já é maior de idade. Peça para ele criar a própria conta."
            txtErro.visibility = View.VISIBLE
            return
        }

        viewModel.salvar("Data de nascimento atualizada!") { passageiro ->
            passageiro.copy(dataNascimento = dataIso)
        }
    }
}
