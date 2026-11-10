package com.example.vaivan.ui.inicio

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.CadastroRascunhoRepository
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

    private val rascunhoRepository by lazy {
        CadastroRascunhoRepository(this)
    }

    private var processando = false

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

        // Ajuste para teclado e barras do sistema
        WindowInsetsUtil.aplicarInsets(
            bottomSheet,
            aplicarTopo = false
        )

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

        // RECUPERAÇÃO DE SENHA
        txtEsqueci.setOnClickListener {

            val email = edtEmail.text.toString()
                .trim()
                .lowercase()

            if (email.isEmpty()) {
                edtEmail.error =
                    "Digite seu e-mail para recuperar a senha"

                edtEmail.requestFocus()
                return@setOnClickListener
            }

            authRepository.recuperarSenha(
                email,
                onSuccess = {
                    mostrarMensagem(
                        "E-mail de recuperação enviado!"
                    )
                },
                onError = { erro ->
                    mostrarMensagem(
                        erro.message ?: "Erro ao recuperar senha."
                    )
                }
            )
        }

        // CADASTRO
        btnCadastrar.setOnClickListener {

            if (processando) return@setOnClickListener

            val rascunho = rascunhoRepository.carregar()

            if (rascunho != null) {
                abrirCadastro()
                return@setOnClickListener
            }

            val usuario = FirebaseAuth.getInstance().currentUser

            if (usuario == null) {
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
