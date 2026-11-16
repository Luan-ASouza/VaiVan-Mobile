package com.example.vaivan.ui.inicio

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.ui.inicio.cadastro.CadastroActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {

    private lateinit var behavior: BottomSheetBehavior<LinearLayout>

    private val authRepository = FirebaseAuthRepository()

    private val usuarioRepository by lazy {
        UsuarioRepository(
            VaivanDatabase.getInstance(this).usuarioDao()
        )
    }

    private var processando = false

    private companion object {
        /** Quanto da altura do teclado a imagem de fundo sobe (1 = junto com o sheet). */
        const val FATOR_SUBIDA_FUNDO = 0.5f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Componentes principais
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val btnCadastrar = findViewById<Button>(R.id.btnCadastrar)

        // Componentes do BottomSheet
        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val txtEsqueci = findViewById<TextView>(R.id.txtEsqueci)
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)

        // Configuração do BottomSheet
        behavior = BottomSheetBehavior.from(bottomSheet).apply {
            isHideable = true
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }

        // Ajuste para teclado e barras do sistema.
        // O listener fica na raiz (não no bottomSheet) porque o
        // BottomSheetBehavior instala o próprio listener de insets no sheet
        // no primeiro layout e substituiria o nosso. Com o teclado aberto, a
        // tela inteira (fundo + sheet) sobe a altura dele, sem redimensionar
        // a imagem; fechado, o sheet só reserva o espaço da barra de navegação.
        val raiz = findViewById<View>(android.R.id.content)
        val telaLogin = findViewById<View>(R.id.activityLogin)
        val fundoLogin = findViewById<View>(R.id.fundoLogin)
        val paddingBaseSheet = bottomSheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { _, insets ->
            val tecladoAberto = insets.isVisible(WindowInsetsCompat.Type.ime())
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

            val deslocamento = if (tecladoAberto) teclado.toFloat() else 0f
            telaLogin.translationY = -deslocamento
            // O fundo compensa parte da subida: no total sobe só FATOR_SUBIDA_FUNDO do teclado.
            fundoLogin.translationY = deslocamento * (1f - FATOR_SUBIDA_FUNDO)
            bottomSheet.setPadding(
                bottomSheet.paddingLeft,
                bottomSheet.paddingTop,
                bottomSheet.paddingRight,
                paddingBaseSheet + if (tecladoAberto) 0 else navBar
            )
            insets
        }

        // Abrir painel de login
        btnEntrar.setOnClickListener {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        // LOGIN
        btnLogin.setOnClickListener {

            if (processando) return@setOnClickListener

            val email = edtEmail.text.toString()
                .trim()
                .lowercase()

            val senha = edtSenha.text.toString()

            if (email.isEmpty()) {
                edtEmail.error = "Por favor, preencha o e-mail"
                edtEmail.requestFocus()
                return@setOnClickListener
            }

            if (senha.isEmpty()) {
                edtSenha.error = "Por favor, preencha a senha"
                edtSenha.requestFocus()
                return@setOnClickListener
            }

            processando = true
            btnLogin.isEnabled = false

            authRepository.login(
                email,
                senha,
                onSuccess = {
                    val uid = FirebaseAuth.getInstance()
                        .currentUser?.uid

                    if (uid != null) {
                        irParaProximaTela(
                            uid = uid,
                            onFinalizado = {
                                processando = false
                                btnLogin.isEnabled = true
                            }
                        )
                    } else {
                        processando = false
                        btnLogin.isEnabled = true

                        mostrarMensagem(
                            "Não foi possível identificar o usuário."
                        )
                    }
                },
                onError = { erro ->
                    processando = false
                    btnLogin.isEnabled = true

                    mostrarMensagem(
                        erro.message ?: "Erro desconhecido ao entrar."
                    )
                }
            )
        }

        // RECUPERAÇÃO DE SENHA — tela própria; leva o e-mail já digitado aqui.
        txtEsqueci.setOnClickListener {
            startActivity(
                Intent(this, RecuperarSenhaActivity::class.java).putExtra(
                    RecuperarSenhaActivity.EXTRA_EMAIL,
                    edtEmail.text.toString().trim().lowercase()
                )
            )
        }

        // CADASTRO
        btnCadastrar.setOnClickListener {

            if (processando) return@setOnClickListener

            val usuario = FirebaseAuth.getInstance().currentUser

            // Sem sessão, ou sessão de um cadastro que parou antes de confirmar
            // o e-mail: começa do zero. Se for o mesmo e-mail, a etapa de
            // senha retoma a conta existente (ver CredenciaisSenhaFragment).
            if (usuario == null || !usuario.isEmailVerified) {
                if (usuario != null) authRepository.logout()
                abrirCadastro()
                return@setOnClickListener
            }

            processando = true
            btnCadastrar.isEnabled = false

            irParaProximaTela(
                uid = usuario.uid,
                onFinalizado = {
                    processando = false
                    btnCadastrar.isEnabled = true
                }
            )
        }
    }

    /**
     * Verifica se o usuário possui cadastro completo.
     *
     * Com cadastro: escolha de perfil.
     * Sem cadastro: continuar cadastro.
     * Erro de consulta: permanece na tela.
     */
    private fun irParaProximaTela(
        uid: String,
        onFinalizado: () -> Unit = {}
    ) {
        lifecycleScope.launch {

            try {
                val usuario = usuarioRepository.consultarUsuario(uid)

                if (usuario != null) {
                    mostrarMensagem("Login realizado com sucesso!")

                    startActivity(
                        Intent(
                            this@LoginActivity,
                            EscolhaTipoPerfilActivity::class.java
                        )
                    )

                    finish()
                } else {
                    abrirCadastro(jaAutenticado = true)
                    finish()
                }

            } catch (erro: Exception) {
                mostrarMensagem(
                    "Não foi possível consultar seu cadastro. " +
                            "Verifique sua conexão e tente novamente."
                )
            } finally {
                onFinalizado()
            }
        }
    }

    /**
     * Abre o cadastro, permitindo continuar
     * com uma conta já autenticada.
     */
    private fun abrirCadastro(
        jaAutenticado: Boolean = false
    ) {
        val intent = Intent(
            this,
            CadastroActivity::class.java
        )

        if (jaAutenticado) {
            intent.putExtra(
                CadastroActivity.EXTRA_JA_AUTENTICADO,
                true
            )
        }

        startActivity(intent)
    }

    private fun mostrarMensagem(mensagem: String) {
        Toast.makeText(
            this,
            mensagem,
            Toast.LENGTH_LONG
        ).show()
    }
}
