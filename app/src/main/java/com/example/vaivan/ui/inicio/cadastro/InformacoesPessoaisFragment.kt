package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.vaivan.R
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.core.validation.CadastroValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

class InformacoesPessoaisFragment :
    Fragment(R.layout.fragment_informacoes_pessoais) {

    private val viewModel: CadastroViewModel by activityViewModels()

    private lateinit var edtNomeCompleto: TextInputEditText
    private lateinit var edtCpf: TextInputEditText

    private lateinit var layoutNome: TextInputLayout
    private lateinit var layoutCpf: TextInputLayout

    private lateinit var txtErroCpf: TextView
    private lateinit var txtErroIdade: TextView
    private lateinit var txtErroNome: TextView

    private lateinit var spinnerDia: Spinner
    private lateinit var spinnerMes: Spinner
    private lateinit var spinnerAno: Spinner

    private lateinit var btnContinuar: MaterialButton

    companion object {
        private const val IDADE_MINIMA = 18
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        configurarViews(view)
        configurarNome()
        configurarCpf()
        configurarErros()
        configurarSpinnersData()
        configurarBotao()
    }

    private fun configurarViews(
        view: View
    ) {

        edtNomeCompleto =
            view.findViewById(R.id.edtNomeCompleto)

        edtCpf =
            view.findViewById(R.id.edtCpf)

        layoutNome =
            view.findViewById(R.id.layoutNome)

        layoutCpf =
            view.findViewById(R.id.layoutCpf)

        txtErroCpf =
            view.findViewById(R.id.txtErroCpf)

        txtErroIdade =
            view.findViewById(R.id.txtErroIdade)

        txtErroNome =
            view.findViewById(R.id.txtErroNome)

        spinnerDia =
            view.findViewById(R.id.spinnerDia)

        spinnerMes =
            view.findViewById(R.id.spinnerMes)

        spinnerAno =
            view.findViewById(R.id.spinnerAno)

        btnContinuar =
            view.findViewById(R.id.btnContinuar)
    }

    private fun configurarNome() {

        edtNomeCompleto.filters =
            arrayOf(
                InputFilter { source, _, _, _, _, _ ->

                    if (source.isNullOrEmpty()) {
                        return@InputFilter null
                    }

                    val permitido =
                        source.toString().all { caractere ->

                            caractere.isLetter() ||
                                    caractere == ' ' ||
                                    caractere == '-' ||
                                    caractere == '\''
                        }

                    if (permitido) {
                        null
                    } else {
                        ""
                    }
                }
            )
    }

    private fun configurarCpf() {

        edtCpf.addTextChangedListener(
            MascaraUtil.inserir(
                "###.###.###-##",
                edtCpf
            )
        )
    }

    private fun configurarErros() {

        txtErroCpf.visibility =
            View.GONE

        txtErroIdade.visibility =
            View.GONE

        txtErroNome.visibility =
            View.GONE
    }

    private fun configurarBotao() {

        btnContinuar.setOnClickListener {
            validarFormulario()
        }
    }

    private fun configurarSpinnersData() {

        val dias =
            Array(31) { index ->
                (index + 1).toString()
            }

        val meses =
            arrayOf(
                "Jan",
                "Fev",
                "Mar",
                "Abr",
                "Mai",
                "Jun",
                "Jul",
                "Ago",
                "Set",
                "Out",
                "Nov",
                "Dez"
            )

        val calendario =
            Calendar.getInstance()

        val anoAtual =
            calendario.get(Calendar.YEAR)

        val anos =
            Array(100) { index ->
                (anoAtual - index).toString()
            }

        configurarSpinner(
            spinnerDia,
            dias
        )

        configurarSpinner(
            spinnerMes,
            meses
        )

        configurarSpinner(
            spinnerAno,
            anos
        )
    }

    private fun configurarSpinner(
        spinner: Spinner,
        itens: Array<String>
    ) {

        val adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                itens
            )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spinner.adapter =
            adapter
    }

    private fun validarFormulario() {

        val nome =
            obterTexto(edtNomeCompleto)

        val cpf =
            obterTexto(edtCpf)

        var valido = true

        if (
            !CadastroValidator.nomeValido(nome)
        ) {

            mostrarErro(
                layout = layoutNome,
                mensagem = txtErroNome
            )

            valido = false

        } else {

            limparErro(
                layout = layoutNome,
                mensagem = txtErroNome
            )
        }

        if (
            !CadastroValidator.cpfValido(cpf)
        ) {

            mostrarErro(
                layout = layoutCpf,
                mensagem = txtErroCpf
            )

            valido = false

        } else {

            limparErro(
                layout = layoutCpf,
                mensagem = txtErroCpf
            )
        }

        val dataNascimento =
            obterDataNascimento()

        if (dataNascimento == null) {

            txtErroIdade.visibility =
                View.VISIBLE

            valido = false

        } else if (
            !CadastroValidator.idadeValida(
                dataNascimento,
                IDADE_MINIMA
            )
        ) {

            txtErroIdade.visibility =
                View.VISIBLE

            valido = false

        } else {

            txtErroIdade.visibility =
                View.GONE
        }

        if (
            valido &&
            dataNascimento != null
        ) {

            salvarDadosNoViewModel(
                nome = nome,
                cpf = cpf,
                dataNascimento = dataNascimento
            )

            abrirCodigoVerificacao()
        }
    }

    private fun salvarDadosNoViewModel(
        nome: String,
        cpf: String,
        dataNascimento: Calendar
    ) {

        val ano =
            dataNascimento.get(Calendar.YEAR)

        val mes =
            dataNascimento.get(Calendar.MONTH) + 1

        val dia =
            dataNascimento.get(Calendar.DAY_OF_MONTH)

        val dataFormatada =
            String.format(
                "%04d/%02d/%02d",
                ano,
                mes,
                dia
            )

        viewModel.definirDadosPessoais(
            nome = nome,
            cpf = cpf,
            dataNascimento = dataFormatada
        )
    }

    private fun obterDataNascimento(): Calendar? {

        val dia =
            spinnerDia.selectedItem
                .toString()
                .toInt()

        val mes =
            spinnerMes.selectedItemPosition

        val ano =
            spinnerAno.selectedItem
                .toString()
                .toInt()

        if (
            !CadastroValidator.dataValida(
                dia = dia,
                mes = mes,
                ano = ano
            )
        ) {
            return null
        }

        return try {

            val calendario =
                Calendar.getInstance()

            calendario.isLenient = false

            calendario.set(
                ano,
                mes,
                dia,
                0,
                0,
                0
            )

            calendario.set(
                Calendar.MILLISECOND,
                0
            )

            calendario

        } catch (
            e: IllegalArgumentException
        ) {

            null
        }
    }

    private fun mostrarErro(
        layout: TextInputLayout,
        mensagem: TextView
    ) {

        mensagem.visibility =
            View.VISIBLE

        layout.setBackgroundResource(
            R.drawable.bg_input_white_red
        )
    }

    private fun limparErro(
        layout: TextInputLayout,
        mensagem: TextView
    ) {

        mensagem.visibility =
            View.GONE

        layout.setBackgroundResource(
            R.drawable.bg_input_white
        )
    }

    private fun obterTexto(
        editText: TextInputEditText
    ): String {

        return editText.text
            ?.toString()
            ?.trim()
            ?: ""
    }

    private fun abrirCodigoVerificacao() {

        (requireActivity() as CadastroActivity)
            .abrirCodigoVerificacao()
    }
}