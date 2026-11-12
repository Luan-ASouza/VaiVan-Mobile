package com.example.vaivan.ui.comum.perfil

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
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar
import java.util.Locale

/** Edição da data de nascimento do perfil. Mesmo formato gravado no cadastro
 * (DataNascimentoFragment): "yyyy/MM/dd". */
class EditarDataNascimentoActivity : BaseActivity() {

    companion object {
        private const val IDADE_MINIMA = 18
    }

    private val viewModel: EditarPerfilViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(this@EditarDataNascimentoActivity)
                EditarPerfilViewModel(UsuarioRepository(db.usuarioDao()), FirebaseAuth.getInstance().currentUser?.uid)
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
            aoCarregar = { usuario -> selecionarData(usuario.dataNascimento) }
        )
    }

    /** "yyyy/MM/dd" → posiciona o DatePicker. Data ausente/inválida: mantém hoje. */
    private fun selecionarData(dataNascimento: String?) {
        val partes = dataNascimento?.split("/")?.mapNotNull { it.toIntOrNull() }
        if (partes == null || partes.size != 3) return

        val (ano, mes, dia) = partes
        datePicker.updateDate(ano, mes - 1, dia)
    }

    private fun validarESalvar() {
        val nascimento = Calendar.getInstance().apply {
            clear()
            set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
        }

        if (!CadastroValidator.idadeValida(nascimento, IDADE_MINIMA)) {
            txtErro.visibility = View.VISIBLE
            return
        }

        val dataFormatada = String.format(
            Locale.ROOT,
            "%04d/%02d/%02d",
            datePicker.year,
            datePicker.month + 1,
            datePicker.dayOfMonth
        )

        viewModel.salvar("Data de nascimento atualizada!") { usuario ->
            usuario.copy(dataNascimento = dataFormatada)
        }
    }
}
