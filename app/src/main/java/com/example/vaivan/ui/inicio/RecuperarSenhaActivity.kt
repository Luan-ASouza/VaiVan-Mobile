package com.example.vaivan.ui.inicio

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuthInvalidUserException

/** Recuperação de senha, no mesmo visual do cadastro: digita o e-mail, envia
 * o link do Firebase e mostra o aviso de "e-mail enviado" com o botão de
 * voltar pro login. Aberta pelo "Esqueci minha senha" da LoginActivity, que
 * passa o e-mail já digitado lá em [EXTRA_EMAIL]. */
class RecuperarSenhaActivity : BaseActivity() {

    companion object {
        const val EXTRA_EMAIL = "email"
    }

    private val authRepository = FirebaseAuthRepository()

    private lateinit var grupoFormulario: View
    private lateinit var grupoEnviado: View
    private lateinit var layoutEmail: TextInputLayout
    private lateinit var edtEmail: TextInputEditText
    private lateinit var txtErroEmail: TextView
    private lateinit var btnEnviar: MaterialButton
    private lateinit var txtDescricaoEnviado: TextView
    private lateinit var txtReenviar: TextView
    private lateinit var grupoStatusEnvio: View

    private var emailEnviado = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_recuperar_senha)
        aplicarGapsDeSistema()

        // Mesmo motivo do cadastro: com edge-to-edge o teclado cobriria o campo.
        WindowInsetsUtil.aplicarInsets(
            findViewById<ScrollView>(R.id.scrollRecuperarSenha),
            aplicarTopo = false
        )

        grupoFormulario = findViewById(R.id.grupoFormulario)
        grupoEnviado = findViewById(R.id.grupoEnviado)
        layoutEmail = findViewById(R.id.layoutEmail)
        edtEmail = findViewById(R.id.edtEmail)
        txtErroEmail = findViewById(R.id.txtErroEmail)
        btnEnviar = findViewById(R.id.btnEnviar)
        txtDescricaoEnviado = findViewById(R.id.txtDescricaoEnviado)
        txtReenviar = findViewById(R.id.txtReenviar)
        grupoStatusEnvio = findViewById(R.id.grupoStatusEnvio)

        edtEmail.setText(intent.getStringExtra(EXTRA_EMAIL).orEmpty())

        findViewById<ImageButton>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnVoltarLogin).setOnClickListener { finish() }

        btnEnviar.setOnClickListener { validarEEnviar() }
        edtEmail.setOnEditorActionListener { _, acao, _ ->
            if (acao == EditorInfo.IME_ACTION_SEND) {
                validarEEnviar()
                true
            } else {
                false
            }
        }

        txtReenviar.setOnClickListener { reenviar() }
    }

    private fun validarEEnviar() {
        val email = edtEmail.text?.toString()?.trim()?.lowercase().orEmpty()

        if (!CadastroValidator.emailValido(email)) {
            mostrarErro("*Digite um email válido")
            return
        }

        limparErro()
        esconderTeclado()

        // Troca otimista: a resposta do Firebase leva segundos (rede, reCAPTCHA
        // do Auth), muito acima dos ~400 ms em que a tela precisa reagir
        // (limiar de Doherty). Mostra o "enviado" já e desfaz se der erro.
        mostrarEnviado(email)
        grupoStatusEnvio.visibility = View.VISIBLE
        txtReenviar.isEnabled = false

        authRepository.recuperarSenha(
            email,
            onSuccess = {
                if (telaEncerrada()) return@recuperarSenha
                grupoStatusEnvio.visibility = View.GONE
                txtReenviar.isEnabled = true
            },
            onError = { erro ->
                val mensagem = if (erro is FirebaseAuthInvalidUserException) {
                    "*Não encontramos uma conta com este e-mail"
                } else {
                    "*Não foi possível enviar. Verifique sua conexão e tente novamente."
                }

                if (telaEncerrada()) {
                    // Já saiu da tela: avisa sem tocar em views destruídas.
                    Toast.makeText(applicationContext, mensagem.removePrefix("*"), Toast.LENGTH_LONG).show()
                    return@recuperarSenha
                }

                grupoStatusEnvio.visibility = View.GONE
                txtReenviar.isEnabled = true
                grupoEnviado.visibility = View.GONE
                grupoFormulario.visibility = View.VISIBLE
                mostrarErro(mensagem)
            }
        )
    }

    private fun reenviar() {
        // Mesma ideia: confirma na hora e só avisa de novo se falhar.
        Toast.makeText(this, "E-mail reenviado.", Toast.LENGTH_SHORT).show()

        authRepository.recuperarSenha(
            emailEnviado,
            onSuccess = {},
            onError = {
                Toast.makeText(
                    applicationContext,
                    "Não foi possível reenviar. Tente novamente.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    private fun mostrarEnviado(email: String) {
        emailEnviado = email
        txtDescricaoEnviado.text =
            "Enviamos um link para $email. Abra seu e-mail, toque no link e crie uma nova senha. " +
                "Se não encontrar, confira a caixa de spam."

        grupoFormulario.visibility = View.GONE
        grupoEnviado.visibility = View.VISIBLE
    }

    private fun telaEncerrada() = isFinishing || isDestroyed

    private fun esconderTeclado() {
        WindowInsetsControllerCompat(window, edtEmail).hide(WindowInsetsCompat.Type.ime())
        edtEmail.clearFocus()
    }

    private fun mostrarErro(mensagem: String) {
        txtErroEmail.text = mensagem
        txtErroEmail.visibility = View.VISIBLE
        layoutEmail.setBackgroundResource(R.drawable.bg_cadastro_input_erro)
    }

    private fun limparErro() {
        txtErroEmail.visibility = View.GONE
        layoutEmail.setBackgroundResource(R.drawable.bg_cadastro_input)
    }
}
