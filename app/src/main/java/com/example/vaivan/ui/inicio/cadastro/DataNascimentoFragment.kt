package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.view.View
import android.widget.DatePicker
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.vaivan.R
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.repository.CadastroRascunhoRepository
import com.google.android.material.button.MaterialButton
import java.util.Calendar

/** Etapa 3 (continuação) — data de nascimento, numa tela própria com um
 * seletor de roleta (dia/mês/ano), em vez de três spinners soltos dentro da
 * etapa de informações pessoais. */
class DataNascimentoFragment : Fragment(R.layout.fragment_data_nascimento) {

    private val viewModel: CadastroViewModel by activityViewModels()
    private val rascunhoRepository by lazy { CadastroRascunhoRepository(requireContext()) }

    private lateinit var datePicker: DatePicker
    private lateinit var txtErroIdade: TextView
    private lateinit var btnContinuar: MaterialButton

    companion object {
        private const val IDADE_MINIMA = 18
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarEtapaCadastro(atual = 5)

        datePicker = view.findViewById(R.id.datePickerNascimento)
        txtErroIdade = view.findViewById(R.id.txtErroIdade)
        btnContinuar = view.findViewById(R.id.btnContinuar)

        datePicker.maxDate = System.currentTimeMillis()

        preencherDataSalva()

        btnContinuar.setOnClickListener { validarFormulario() }
    }

    /** Reabrindo essa etapa (voltou ou retomou um rascunho) — repõe a data
     * já escolhida antes; sem nada salvo, começa 18 anos atrás (em vez de
     * "hoje", que já nasceria inválido). */
    private fun preencherDataSalva() {

        val dataSalva = viewModel.cadastro.usuario.dataNascimento
        val partes = dataSalva?.split("/")

        if (partes?.size == 3) {
            val ano = partes[0].toIntOrNull()
            val mes = partes[1].toIntOrNull()
            val dia = partes[2].toIntOrNull()

            if (ano != null && mes != null && dia != null) {
                datePicker.updateDate(ano, mes - 1, dia)
                return
            }
        }

        val dezoitoAnosAtras = Calendar.getInstance().apply { add(Calendar.YEAR, -IDADE_MINIMA) }
        datePicker.updateDate(
            dezoitoAnosAtras.get(Calendar.YEAR),
            dezoitoAnosAtras.get(Calendar.MONTH),
            dezoitoAnosAtras.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun validarFormulario() {

        val dataNascimento = Calendar.getInstance().apply {
            set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (!CadastroValidator.idadeValida(dataNascimento, IDADE_MINIMA)) {
            txtErroIdade.visibility = View.VISIBLE
            return
        }

        txtErroIdade.visibility = View.GONE

        val dataFormatada = String.format(
            "%04d/%02d/%02d",
            datePicker.year,
            datePicker.month + 1,
            datePicker.dayOfMonth
        )

        viewModel.definirDataNascimento(dataFormatada)
        rascunhoRepository.salvar(viewModel.cadastro, CadastroActivity.ETAPA_ENDERECO)

        (requireActivity() as CadastroActivity).abrirEndereco()
    }
}
