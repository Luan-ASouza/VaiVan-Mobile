package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.vaivan.R
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Etapa 1 — identificação: nome, sobrenome e e-mail. */
class IdentificacaoFragment : Fragment(R.layout.fragment_identificacao) {

    private val viewModel: CadastroViewModel by activityViewModels()
    private val authRepository = FirebaseAuthRepository()

    private lateinit var edtNome: TextInputEditText
    private lateinit var edtSobrenome: TextInputEditText
    private lateinit var edtEmail: TextInputEditText
    private lateinit var layoutNome: TextInputLayout
    private lateinit var layoutSobrenome: TextInputLayout
    private lateinit var layoutEmail: TextInputLayout
    private lateinit var txtErroNome: TextView
    private lateinit var txtErroSobrenome: TextView
    private lateinit var txtErroEmail: TextView
    private lateinit var btnContinuar: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarEtapaCadastro(atual = 1)
        inicializarViews(view)

        btnContinuar.setOnClickListener { validarFormulario() }
    }

    private fun inicializarViews(view: View) {
        edtNome = view.findViewById(R.id.edtNome)
        edtSobrenome = view.findViewById(R.id.edtSobrenome)
        edtEmail = view.findViewById(R.id.edtEmail)
        layoutNome = view.findViewById(R.id.layoutNome)
        layoutSobrenome = view.findViewById(R.id.layoutSobrenome)
        layoutEmail = view.findViewById(R.id.layoutEmail)
        txtErroNome = view.findViewById(R.id.txtErroNome)
        txtErroSobrenome = view.findViewById(R.id.txtErroSobrenome)
        txtErroEmail = view.findViewById(R.id.txtErroEmail)
        btnContinuar = view.findViewById(R.id.btnContinuar)

        edtNome.filters = arrayOf(FILTRO_NOME_CADASTRO)
        edtSobrenome.filters = arrayOf(FILTRO_NOME_CADASTRO)

        // Reabrindo essa etapa (o usuário voltou pra ela) — repõe o que
        // já tinha sido digitado antes.
        val usuario = viewModel.cadastro.usuario
        edtNome.setText(usuario.nome)
        edtSobrenome.setText(usuario.sobrenome)
        edtEmail.setText(usuario.email)
    }

    private fun validarFormulario() {
        val nome = edtNome.text?.toString()?.trim() ?: ""
        val sobrenome = edtSobrenome.text?.toString()?.trim() ?: ""
        val email = edtEmail.text?.toString()?.trim() ?: ""

        var valido = true

        if (!CadastroValidator.nomeValido(nome)) {
            mostrarErro(txtErroNome, layoutNome, "*Campo obrigatório")
            valido = false
        } else {
            limparErro(txtErroNome, layoutNome)
        }

        if (!CadastroValidator.nomeValido(sobrenome)) {
            mostrarErro(txtErroSobrenome, layoutSobrenome, "*Campo obrigatório")
            valido = false
        } else {
            limparErro(txtErroSobrenome, layoutSobrenome)
        }

        if (!CadastroValidator.emailValido(email)) {
            mostrarErro(txtErroEmail, layoutEmail, "*Digite um email válido")
            valido = false
        } else {
            limparErro(txtErroEmail, layoutEmail)
        }

        if (valido) {
            verificarEmail(nome, sobrenome, email)
        }
    }

    private fun verificarEmail(nome: String, sobrenome: String, email: String) {
        btnContinuar.isEnabled = false

        authRepository.verificarEmail(
            email = email,
            onResult = { existe ->
                btnContinuar.isEnabled = true

                // E-mail já existente não bloqueia aqui: pode ser um cadastro
                // que parou antes da confirmação do e-mail. A etapa de senha
                // pede a senha antiga e decide (não confirmado → continua;
                // confirmado → manda pro login).
                viewModel.emailJaCadastrado = existe
                viewModel.definirIdentificacao(nome = nome, sobrenome = sobrenome, email = email)
                (requireActivity() as CadastroActivity).abrirCredenciaisSenha()
            },
            onError = {
                btnContinuar.isEnabled = true
                mostrarErro(txtErroEmail, layoutEmail, "*Erro ao verificar e-mail. Tente novamente.")
            }
        )
    }

    private fun mostrarErro(mensagem: TextView, layout: TextInputLayout, texto: String) {
        mensagem.text = texto
        mensagem.visibility = View.VISIBLE
        layout.setBackgroundResource(R.drawable.bg_cadastro_input_erro)
    }

    private fun limparErro(mensagem: TextView, layout: TextInputLayout) {
        mensagem.visibility = View.GONE
        layout.setBackgroundResource(R.drawable.bg_cadastro_input)
    }
}
