package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.vaivan.R
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CredenciaisFragment :
    Fragment(R.layout.fragment_credenciais) {

    private val viewModel: CadastroViewModel by activityViewModels()

    private val authRepository =
        FirebaseAuthRepository()

    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtTelefone: TextInputEditText
    private lateinit var edtSenha: TextInputEditText
    private lateinit var edtConfirmarSenha: TextInputEditText

    private lateinit var txtErroEmail: TextView
    private lateinit var txtInfoSenha: TextView
    private lateinit var txtErroSenha: TextView
    private lateinit var txtErroTermos: TextView
    private lateinit var txtErroTelefone: TextView

    private lateinit var spinnerDDD: Spinner
    private lateinit var checkTermos: CheckBox

    private lateinit var layoutEmail: TextInputLayout
    private lateinit var layoutSenha: TextInputLayout
    private lateinit var layoutConfirmarSenha: TextInputLayout
    private lateinit var layoutTelefone: TextInputLayout

    private lateinit var btnCadastrar: MaterialButton

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        inicializarViews(view)
        configurarTelefone()
        configurarDDD()
        configurarMensagens()
        configurarBotao()
    }

    private fun inicializarViews(view: View) {

        edtEmail =
            view.findViewById(R.id.edtEmail)

        edtTelefone =
            view.findViewById(R.id.edtTelefone)

        edtSenha =
            view.findViewById(R.id.edtSenha)

        edtConfirmarSenha =
            view.findViewById(R.id.edtConfirmarSenha)

        txtErroEmail =
            view.findViewById(R.id.txtErroEmail)

        txtInfoSenha =
            view.findViewById(R.id.txtInfoSenha)

        txtErroSenha =
            view.findViewById(R.id.txtErroSenha)

        txtErroTelefone =
            view.findViewById(R.id.txtErroTelefone)

        txtErroTermos =
            view.findViewById(R.id.txtErroTermos)

        spinnerDDD =
            view.findViewById(R.id.spinnerDDD)

        checkTermos =
            view.findViewById(R.id.checkTermos)

        layoutEmail =
            view.findViewById(R.id.layoutEmail)

        layoutSenha =
            view.findViewById(R.id.layoutSenha)

        layoutConfirmarSenha =
            view.findViewById(R.id.layoutConfirmarSenha)

        layoutTelefone =
            view.findViewById(R.id.layoutTelefone)

        btnCadastrar =
            view.findViewById(R.id.btnCadastrar)
    }

    private fun configurarTelefone() {

        edtTelefone.addTextChangedListener(
            MascaraUtil.inserir(
                "(##) #####-####",
                edtTelefone
            )
        )
    }

    private fun configurarDDD() {

        val ddds = arrayOf(
            "+55",
            "+1",
            "+351"
        )

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            ddds
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerDDD.adapter = adapter
        spinnerDDD.setSelection(0)
    }

    private fun configurarMensagens() {

        txtErroEmail.visibility = View.GONE
        txtInfoSenha.visibility = View.GONE
        txtErroSenha.visibility = View.GONE
        txtErroTelefone.visibility = View.GONE
        txtErroTermos.visibility = View.GONE
    }

    private fun configurarBotao() {

        btnCadastrar.setOnClickListener {
            validarFormulario()
        }
    }

    private fun validarFormulario() {

        val email =
            edtEmail.text
                ?.toString()
                ?.trim()
                ?: ""

        val telefone =
            edtTelefone.text
                ?.toString()
                ?.trim()
                ?: ""

        val senha =
            edtSenha.text
                ?.toString()
                ?.trim()
                ?: ""

        val confirmarSenha =
            edtConfirmarSenha.text
                ?.toString()
                ?.trim()
                ?: ""

        var formularioValido = true

        if (!validarEmail(email)) {
            formularioValido = false
        }

        if (!validarTelefone(telefone)) {
            formularioValido = false
        }

        if (!validarSenha(senha)) {
            formularioValido = false
        }

        if (!validarConfirmacaoSenha(
                senha,
                confirmarSenha
            )
        ) {
            formularioValido = false
        }

        if (!validarTermos()) {
            formularioValido = false
        }

        if (formularioValido) {

            verificarEmail(
                email = email,
                telefone = telefone,
                senha = senha
            )
        }
    }

    private fun validarEmail(
        email: String
    ): Boolean {

        if (!CadastroValidator.emailValido(email)) {

            mostrarErro(
                mensagem = txtErroEmail,
                layout = layoutEmail,
                texto = "*Digite um email válido"
            )

            return false
        }

        limparErro(
            mensagem = txtErroEmail,
            layout = layoutEmail
        )

        return true
    }

    private fun validarTelefone(
        telefone: String
    ): Boolean {

        if (!CadastroValidator.telefoneValido(telefone)) {

            txtErroTelefone.visibility =
                View.VISIBLE

            layoutTelefone.setBackgroundResource(
                R.drawable.bg_input_white_red
            )

            return false
        }

        txtErroTelefone.visibility =
            View.GONE

        layoutTelefone.setBackgroundResource(
            R.drawable.bg_input_white
        )

        return true
    }

    private fun validarSenha(
        senha: String
    ): Boolean {

        if (!CadastroValidator.senhaValida(senha)) {

            txtInfoSenha.visibility =
                View.VISIBLE

            layoutSenha.setBackgroundResource(
                R.drawable.bg_input_white_red
            )

            return false
        }

        txtInfoSenha.visibility =
            View.GONE

        layoutSenha.setBackgroundResource(
            R.drawable.bg_input_white
        )

        return true
    }

    private fun validarConfirmacaoSenha(
        senha: String,
        confirmarSenha: String
    ): Boolean {

        if (
            !CadastroValidator.senhasConferem(
                senha,
                confirmarSenha
            )
        ) {

            txtErroSenha.visibility =
                View.VISIBLE

            layoutConfirmarSenha.setBackgroundResource(
                R.drawable.bg_input_white_red
            )

            return false
        }

        txtErroSenha.visibility =
            View.GONE

        layoutConfirmarSenha.setBackgroundResource(
            R.drawable.bg_input_white
        )

        return true
    }

    private fun validarTermos(): Boolean {

        if (!checkTermos.isChecked) {

            txtErroTermos.visibility =
                View.VISIBLE

            return false
        }

        txtErroTermos.visibility =
            View.GONE

        return true
    }

    private fun verificarEmail(
        email: String,
        telefone: String,
        senha: String
    ) {

        btnCadastrar.isEnabled = false

        authRepository.verificarEmail(
            email = email,

            onResult = { existe ->

                btnCadastrar.isEnabled = true

                if (existe) {

                    mostrarErro(
                        mensagem = txtErroEmail,
                        layout = layoutEmail,
                        texto = "*Email já cadastrado no sistema"
                    )

                    return@verificarEmail
                }

                viewModel.definirCredenciais(
                    email = email,
                    telefone = telefone,
                    senha = senha
                )

                abrirProximaEtapa()
            },

            onError = {

                btnCadastrar.isEnabled = true

                mostrarErro(
                    mensagem = txtErroEmail,
                    layout = layoutEmail,
                    texto = "*Erro ao verificar e-mail. Tente novamente."
                )
            }
        )
    }

    private fun mostrarErro(
        mensagem: TextView,
        layout: TextInputLayout,
        texto: String
    ) {

        mensagem.text = texto
        mensagem.visibility = View.VISIBLE

        layout.setBackgroundResource(
            R.drawable.bg_input_white_red
        )
    }

    private fun limparErro(
        mensagem: TextView,
        layout: TextInputLayout
    ) {

        mensagem.visibility =
            View.GONE

        layout.setBackgroundResource(
            R.drawable.bg_input_white
        )
    }

    private fun abrirProximaEtapa() {

        (requireActivity() as CadastroActivity)
            .abrirInformacoesPessoais()
    }
}