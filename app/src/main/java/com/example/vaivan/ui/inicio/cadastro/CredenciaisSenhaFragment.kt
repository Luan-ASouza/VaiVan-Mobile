package com.example.vaivan.ui.inicio.cadastro

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/** Etapa 2 — credenciais. Dois modos:
 *
 * - **Conta nova** (padrão): senha + confirmação, com medidor de força. Cria a
 *   conta no Firebase Auth e dispara o e-mail de verificação.
 * - **Continuar cadastro** ([CadastroViewModel.emailJaCadastrado]): o e-mail
 *   já existe. Pede só a senha criada antes e entra na conta; se o e-mail
 *   ainda não foi confirmado, segue o cadastro da verificação em diante. Se
 *   já foi confirmado, a conta é de verdade — manda a pessoa pro login.
 *
 * Mesmo no modo conta nova, se a criação esbarrar num e-mail já existente
 * (ex.: voltou da verificação pra cá, ou o Auth não revelou o e-mail na
 * identificação), cai na mesma lógica de "continuar cadastro". */
class CredenciaisSenhaFragment : Fragment(R.layout.fragment_credenciais_senha) {

    private val viewModel: CadastroViewModel by activityViewModels()
    private val authRepository = FirebaseAuthRepository()

    private lateinit var txtTituloSenha: TextView
    private lateinit var txtAvisoRetomada: TextView
    private lateinit var edtSenha: TextInputEditText
    private lateinit var edtConfirmarSenha: TextInputEditText
    private lateinit var layoutSenha: TextInputLayout
    private lateinit var layoutConfirmarSenha: TextInputLayout
    private lateinit var txtRotuloConfirmarSenha: TextView
    private lateinit var progressForcaSenha: LinearProgressIndicator
    private lateinit var txtForcaSenha: TextView
    private lateinit var txtInfoSenha: TextView
    private lateinit var txtErroSenha: TextView
    private lateinit var txtErro: TextView
    private lateinit var btnContinuar: MaterialButton

    private val retomando get() = viewModel.emailJaCadastrado

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarEtapaCadastro(atual = 2)
        inicializarViews(view)
        configurarModo()
        configurarMedidorDeForca()

        btnContinuar.setOnClickListener { validarFormulario() }
    }

    private fun inicializarViews(view: View) {
        txtTituloSenha = view.findViewById(R.id.txtTituloSenha)
        txtAvisoRetomada = view.findViewById(R.id.txtAvisoRetomada)
        edtSenha = view.findViewById(R.id.edtSenha)
        edtConfirmarSenha = view.findViewById(R.id.edtConfirmarSenha)
        layoutSenha = view.findViewById(R.id.layoutSenha)
        layoutConfirmarSenha = view.findViewById(R.id.layoutConfirmarSenha)
        txtRotuloConfirmarSenha = view.findViewById(R.id.txtRotuloConfirmarSenha)
        progressForcaSenha = view.findViewById(R.id.progressForcaSenha)
        txtForcaSenha = view.findViewById(R.id.txtForcaSenha)
        txtInfoSenha = view.findViewById(R.id.txtInfoSenha)
        txtErroSenha = view.findViewById(R.id.txtErroSenha)
        txtErro = view.findViewById(R.id.txtErro)
        btnContinuar = view.findViewById(R.id.btnContinuar)

        txtInfoSenha.visibility = View.GONE
        txtErroSenha.visibility = View.GONE

        // Voltou pra essa etapa — repõe a senha já digitada antes (editável).
        val senhaSalva = viewModel.cadastro.senha
        edtSenha.setText(senhaSalva)
        edtConfirmarSenha.setText(senhaSalva)
        atualizarForcaSenha(senhaSalva)
    }

    /** No modo "continuar cadastro" não há confirmação nem medidor de força:
     * a senha já existe, só precisa ser a mesma. */
    private fun configurarModo() {
        val visibilidadeNova = if (retomando) View.GONE else View.VISIBLE

        txtTituloSenha.text = if (retomando) "Continue seu cadastro" else "Crie sua senha"
        txtAvisoRetomada.visibility = if (retomando) View.VISIBLE else View.GONE
        txtRotuloConfirmarSenha.visibility = visibilidadeNova
        layoutConfirmarSenha.visibility = visibilidadeNova
        progressForcaSenha.visibility = visibilidadeNova
        txtForcaSenha.visibility = visibilidadeNova
    }

    private fun configurarMedidorDeForca() {
        edtSenha.addTextChangedListener(
            onTextChanged = { texto -> atualizarForcaSenha(texto?.toString() ?: "") }
        )
    }

    private fun atualizarForcaSenha(senha: String) {
        val forca = CadastroValidator.forcaSenha(senha)

        val cor = when {
            senha.isEmpty() -> Color.TRANSPARENT
            forca <= 1 -> Color.parseColor("#F44336") // Fraca
            forca == 2 -> Color.parseColor("#FF9800") // Média
            else -> Color.parseColor("#4CAF50")       // Forte
        }

        progressForcaSenha.setIndicatorColor(cor)
        progressForcaSenha.setProgressCompat(
            if (senha.isEmpty()) 0 else forca.coerceIn(0, 3),
            true
        )

        txtForcaSenha.text = when {
            senha.isEmpty() -> ""
            forca <= 1 -> "Fraca"
            forca == 2 -> "Média"
            else -> "Forte"
        }

        txtForcaSenha.setTextColor(cor)
    }

    private fun validarFormulario() {
        val senha = edtSenha.text?.toString()?.trim() ?: ""

        if (retomando) {
            if (senha.isEmpty()) {
                mostrarErroGeral("*Digite a sua senha.")
                return
            }
            continuar(senha)
            return
        }

        val confirmarSenha = edtConfirmarSenha.text?.toString()?.trim() ?: ""
        var valido = true

        if (!CadastroValidator.senhaValida(senha)) {
            txtInfoSenha.visibility = View.VISIBLE
            layoutSenha.setBackgroundResource(R.drawable.bg_cadastro_input_erro)
            valido = false
        } else {
            txtInfoSenha.visibility = View.GONE
            layoutSenha.setBackgroundResource(R.drawable.bg_cadastro_input)
        }

        if (!CadastroValidator.senhasConferem(senha, confirmarSenha)) {
            txtErroSenha.visibility = View.VISIBLE
            layoutConfirmarSenha.setBackgroundResource(R.drawable.bg_cadastro_input_erro)
            valido = false
        } else {
            txtErroSenha.visibility = View.GONE
            layoutConfirmarSenha.setBackgroundResource(R.drawable.bg_cadastro_input)
        }

        if (valido) {
            continuar(senha)
        }
    }

    private fun continuar(senha: String) {
        btnContinuar.isEnabled = false
        txtErro.visibility = View.GONE

        val email = viewModel.cadastro.usuario.email

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (retomando) {
                    retomarCadastro(email, senha)
                } else {
                    try {
                        authRepository.criarConta(email, senha)
                        seguirParaVerificacao(senha)
                    } catch (erro: FirebaseAuthUserCollisionException) {
                        retomarCadastro(email, senha)
                    }
                }
            } catch (erro: FirebaseAuthInvalidCredentialsException) {
                mostrarErroGeral(
                    "*Já existe um cadastro com este e-mail e a senha não confere. " +
                        "Use a senha que você criou quando começou o cadastro."
                )
            } catch (erro: Exception) {
                mostrarErroGeral(erro.message ?: "*Erro ao continuar. Tente novamente.")
            } finally {
                btnContinuar.isEnabled = true
            }
        }
    }

    /** E-mail já existe: entra com a senha digitada. Só e-mail ainda não
     * confirmado pode seguir por aqui — conta confirmada é cadastro de
     * verdade, e quem a tiver deve usar o login (que também retoma um perfil
     * incompleto). */
    private suspend fun retomarCadastro(email: String, senha: String) {
        val usuario: FirebaseUser = authRepository.entrar(email, senha)

        if (usuario.isEmailVerified) {
            authRepository.logout()
            mostrarErroGeral("*Este e-mail já está cadastrado e confirmado. Entre pela tela de login.")
            return
        }

        seguirParaVerificacao(senha)
    }

    private fun seguirParaVerificacao(senha: String) {
        viewModel.definirSenha(senha)
        authRepository.usuarioAtual()?.sendEmailVerification()
        (requireActivity() as CadastroActivity).abrirVerificacaoEmail()
    }

    private fun mostrarErroGeral(mensagem: String) {
        txtErro.text = mensagem
        txtErro.visibility = View.VISIBLE
    }
}

private fun TextInputEditText.addTextChangedListener(onTextChanged: (CharSequence?) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onTextChanged(s)
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    })
}
