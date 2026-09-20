package com.example.vaivan.ui.inicio.cadastro

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.vaivan.R
import com.example.vaivan.core.util.MascaraUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

class CadastroInformacoesPessoaisActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_informacoes_pessoais_responsavel)

        configurarViews()
        configurarNome()
        configurarCpf()
        configurarErros()
        configurarSpinnersData()

        btnContinuar.setOnClickListener {
            validarFormulario()
        }
    }

    private fun configurarViews() {

        edtNomeCompleto = findViewById(R.id.edtNomeCompleto)
        edtCpf = findViewById(R.id.edtCpf)

        layoutNome = findViewById(R.id.layoutNome)
        layoutCpf = findViewById(R.id.layoutCpf)

        txtErroCpf = findViewById(R.id.txtErroCpf)
        txtErroIdade = findViewById(R.id.txtErroIdade)
        txtErroNome = findViewById(R.id.txtErroNome)

        spinnerDia = findViewById(R.id.spinnerDia)
        spinnerMes = findViewById(R.id.spinnerMes)
        spinnerAno = findViewById(R.id.spinnerAno)

        btnContinuar = findViewById(R.id.btnContinuar)
    }

    private fun configurarNome() {

        edtNomeCompleto.filters = arrayOf(
            InputFilter { source, _, _, _, _, _ ->

                if (source.isNullOrEmpty()) {
                    return@InputFilter null
                }

                val textoPermitido = source.toString().all { caractere ->
                    caractere.isLetter() ||
                            caractere == ' ' ||
                            caractere == '-' ||
                            caractere == '\''
                }

                if (!textoPermitido) {
                    ""
                } else {
                    null
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

        txtErroCpf.visibility = View.GONE
        txtErroIdade.visibility = View.GONE
        txtErroNome.visibility = View.GONE
    }

    private fun configurarSpinnersData() {

        // DIAS
        val dias = Array(31) { index ->
            (index + 1).toString()
        }

        // MESES
        val meses = arrayOf(
            "Jan", "Fev", "Mar", "Abr",
            "Mai", "Jun", "Jul", "Ago",
            "Set", "Out", "Nov", "Dez"
        )

        // ANOS
        val calendario = Calendar.getInstance()
        val anoAtual = calendario.get(Calendar.YEAR)

        val anos = Array(100) { index ->
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

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            itens
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spinner.adapter = adapter
    }

    private fun validarFormulario() {

        val nome = obterTexto(edtNomeCompleto)
        val cpf = obterTexto(edtCpf)

        var formularioValido = true

        // =========================
        // VALIDAR NOME
        // =========================

        if (nome.length < 3) {

            txtErroNome.visibility = View.VISIBLE

            layoutNome.setBackgroundResource(
                R.drawable.bg_input_white_red
            )

            formularioValido = false

        } else {

            txtErroNome.visibility = View.GONE

            layoutNome.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }

        // =========================
        // VALIDAR CPF
        // =========================

        if (!cpfValido(cpf)) {

            txtErroCpf.visibility = View.VISIBLE

            layoutCpf.setBackgroundResource(
                R.drawable.bg_input_white_red
            )

            formularioValido = false

        } else {

            txtErroCpf.visibility = View.GONE

            layoutCpf.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }

        // =========================
        // OBTER DATA
        // =========================

        val dataNascimento = obterDataNascimento()

        if (dataNascimento == null) {

            txtErroIdade.visibility = View.VISIBLE
            formularioValido = false

        } else {

            val idade = calcularIdade(dataNascimento)

            // =========================
            // VALIDAR IDADE
            // =========================

            if (idade < IDADE_MINIMA) {

                txtErroIdade.visibility = View.VISIBLE
                formularioValido = false

            } else {

                txtErroIdade.visibility = View.GONE
            }
        }

        // =========================
        // PRÓXIMA TELA
        // =========================

        if (formularioValido && dataNascimento != null) {

            salvarDadosTemporarios(
                nome,
                cpf,
                dataNascimento
            )

            startActivity(
                Intent(
                    this,
                    CadastroCodigoVerificacaoActivity::class.java
                )
            )

            finish()
        }
    }

    private fun obterDataNascimento(): Calendar? {

        val dia = spinnerDia.selectedItem
            .toString()
            .toInt()

        val mes = spinnerMes.selectedItemPosition

        val ano = spinnerAno.selectedItem
            .toString()
            .toInt()

        return try {

            val calendario = Calendar.getInstance()

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

            // Força o Calendar a validar a data.
            // Exemplo: 31/02 gera IllegalArgumentException.
            calendario.time

            calendario

        } catch (e: IllegalArgumentException) {

            null
        }
    }

    private fun calcularIdade(
        dataNascimento: Calendar
    ): Int {

        val hoje = Calendar.getInstance()

        var idade = hoje.get(Calendar.YEAR) -
                dataNascimento.get(Calendar.YEAR)

        if (
            hoje.get(Calendar.MONTH) <
            dataNascimento.get(Calendar.MONTH) ||

            (
                    hoje.get(Calendar.MONTH) ==
                            dataNascimento.get(Calendar.MONTH) &&

                            hoje.get(Calendar.DAY_OF_MONTH) <
                            dataNascimento.get(Calendar.DAY_OF_MONTH)
                    )
        ) {
            idade--
        }

        return idade
    }

    private fun salvarDadosTemporarios(
        nome: String,
        cpf: String,
        dataNascimento: Calendar
    ) {

        val cadastro = CadastroSession.cadastroUsuario

        cadastro.nome = nome
        cadastro.cpf = cpf

        val ano = dataNascimento.get(Calendar.YEAR)
        val mes = dataNascimento.get(Calendar.MONTH) + 1
        val dia = dataNascimento.get(Calendar.DAY_OF_MONTH)

        val dataFormatada = String.format(
            "%04d-%02d-%02d",
            ano,
            mes,
            dia
        )

        cadastro.dataNascimento = (dataFormatada)
    }

    private fun obterTexto(
        editText: TextInputEditText
    ): String {

        return editText.text
            ?.toString()
            ?.trim()
            ?: ""
    }

    private fun cpfValido(cpf: String): Boolean {

        val cpfLimpo = cpf.replace(
            Regex("[^0-9]"),
            ""
        )

        if (cpfLimpo.length != 11) {
            return false
        }

        // Rejeita CPFs com todos os números iguais
        if (cpfLimpo.all { it == cpfLimpo[0] }) {
            return false
        }

        // Primeiro dígito verificador
        var soma = 0

        for (i in 0 until 9) {
            soma += cpfLimpo[i].digitToInt() * (10 - i)
        }

        var resto = soma % 11

        val primeiroDigito =
            if (resto < 2) 0 else 11 - resto

        if (primeiroDigito != cpfLimpo[9].digitToInt()) {
            return false
        }

        // Segundo dígito verificador
        soma = 0

        for (i in 0 until 10) {
            soma += cpfLimpo[i].digitToInt() * (11 - i)
        }

        resto = soma % 11

        val segundoDigito =
            if (resto < 2) 0 else 11 - resto

        return segundoDigito == cpfLimpo[10].digitToInt()
    }
}