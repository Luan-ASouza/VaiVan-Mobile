package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.repository.CadastroRascunhoRepository
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Etapa 2 — credenciais: senha + confirmação, com medidor de força. É aqui
 * que a conta é criada de fato no Firebase Auth (o e-mail já foi validado na
 * etapa 1) e o e-mail de verificação é disparado. */
class CredenciaisSenhaFragment : Fragment(R.layout.fragment_credenciais_senha) {

    private val viewModel: CadastroViewModel by activityViewModels()
    private val authRepository = FirebaseAuthRepository()
    private val rascunhoRepository by lazy { CadastroRascunhoRepository(requireContext()) }

    private lateinit var edtSenha: TextInputEditText
    private lateinit var edtConfirmarSenha: TextInputEditText
    private lateinit var layoutSenha: TextInputLayout
    private lateinit var layoutConfirmarSenha: TextInputLayout
    private lateinit var progressForcaSenha: LinearProgressIndicator
    private lateinit var txtForcaSenha: TextView
    private lateinit var txtInfoSenha: TextView
    private lateinit var txtErroSenha: TextView
    private lateinit var txtErro: TextView
    private lateinit var btnContinuar: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarEtapaCadastro(atual = 2)
        inicializarViews(view)
        configurarMedidorDeForca()

        btnContinuar.setOnClickListener { validarFormulario() }
    }

    private fun inicializarViews(view: View) {
        edtSenha = view.findViewById(R.id.edtSenha)
        edtConfirmarSenha = view.findViewById(R.id.edtConfirmarSenha)
        layoutSenha = view.findViewById(R.id.layoutSenha)
        layoutConfirmarSenha = view.findViewById(R.id.layoutConfirmarSenha)
        progressForcaSenha = view.findViewById(R.id.progressForcaSenha)
        txtForcaSenha = view.findViewById(R.id.txtForcaSenha)
        txtInfoSenha = view.findViewById(R.id.txtInfoSenha)
        txtErroSenha = view.findViewById(R.id.txtErroSenha)
        txtErro = view.findViewById(R.id.txtErro)
        btnContinuar = view.findViewById(R.id.btnContinuar)

        txtInfoSenha.visibility = View.GONE
        txtErroSenha.visibility = View.GONE

        // Reabrindo essa etapa (voltou ou retomou um rascunho) — repõe a
        // senha já digitada antes (editável).
        val senhaSalva = viewModel.cadastro.senha
        edtSenha.setText(senhaSalva)
        edtConfirmarSenha.setText(senhaSalva)
        atualizarForcaSenha(senhaSalva)
    }

    private fun configurarMedidorDeForca() {
        edtSenha.addTextChangedListener(
            onTextChanged = { texto -> atualizarForcaSenha(texto?.toString() ?: "") }
        )
    }

    private fun atualizarForcaSenha(senha: String) {
        val forca = CadastroValidator.forcaSenha(senha)
        progressForcaSenha.progress = forca

        txtForcaSenha.text = when {
            senha.isEmpty() -> ""
            forca <= 1 -> "Fraca"
            forca == 2 -> "Média"
            else -> "Forte"
        }
    }

    private fun validarFormulario() {
        val senha = edtSenha.text?.toString()?.trim() ?: ""
        val confirmarSenha = edtConfirmarSenha.text?.toString()?.trim() ?: ""

        var valido = true

        if (!CadastroValidator.senhaValida(senha)) {
            txtInfoSenha.visibility = View.VISIBLE
            layoutSenha.setBackgroundResource(R.drawable.bg_input_white_red)
            valido = false
        } else {
            txtInfoSenha.visibility = View.GONE
            layoutSenha.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (!CadastroValidator.senhasConferem(senha, confirmarSenha)) {
            txtErroSenha.visibility = View.VISIBLE
            layoutConfirmarSenha.setBackgroundResource(R.drawable.bg_input_white_red)
            valido = false
        } else {
            txtErroSenha.visibility = View.GONE
            layoutConfirmarSenha.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (valido) {
            criarConta(senha)
        }
    }

    private fun criarConta(senha: String) {
        btnContinuar.isEnabled = false
        txtErro.visibility = View.GONE

        val email = viewModel.cadastro.usuario.email

        authRepository.cadastrar(
            email = email,
            senha = senha,
            onSuccess = {
                viewModel.definirSenha(senha)
                authRepository.usuarioAtual()?.sendEmailVerification()
                rascunhoRepository.salvar(viewModel.cadastro, CadastroActivity.ETAPA_VERIFICACAO_EMAIL)
                (requireActivity() as CadastroActivity).abrirVerificacaoEmail()
            },
            onError = { erro ->
                btnContinuar.isEnabled = true
                txtErro.text = erro.message ?: "*Erro ao criar a conta."
                txtErro.visibility = View.VISIBLE
            }
        )
    }
}

private fun TextInputEditText.addTextChangedListener(onTextChanged: (CharSequence?) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onTextChanged(s)
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    })
}
