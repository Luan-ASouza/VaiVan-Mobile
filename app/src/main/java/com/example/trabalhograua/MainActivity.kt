package com.example.trabalhograua

import android.content.Intent
import com.example.trabalhograua.repository.FirebaseAuthRepository
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.trabalhograua.cadastro.PerfilActivity
import com.example.trabalhograua.cadastro.TipoCadastroActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior

class MainActivity : AppCompatActivity() {

    private lateinit var behavior: BottomSheetBehavior<LinearLayout>
    private val authRepository = FirebaseAuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Botões principais da tela de fundo
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val btnCadastrar = findViewById<Button>(R.id.btnCadastrar)

        // Componentes do painel deslizante (Bottom Sheet)
        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val txtEsqueci = findViewById<TextView>(R.id.txtEsqueci)
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)

        // Configuração do comportamento do Bottom Sheet
        behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        behavior.isHideable = true       // Permite fechar arrastando
        behavior.skipCollapsed = true   // Faz sumir direto sem parar no meio

        // Abre o painel
        btnEntrar.setOnClickListener {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        // Fluxo de Login Seguro
        btnLogin.setOnClickListener {
            val email = edtEmail.text.toString().trim().lowercase()
            val senha = edtSenha.text.toString().trim()

            // Validações simples para o app não quebrar/crashar com inputs vazios
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

            // Chamada de Autenticação com Firebase
            authRepository.login(
                email,
                senha,
                onSuccess = {
                    Toast.makeText(this, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, PerfilActivity::class.java))
                    finish()
                },
                onError = { erro ->
                    Toast.makeText(this, erro.message ?: "Erro desconhecido ao entrar.", Toast.LENGTH_LONG).show()
                }
            )
        }

        // Fluxo de Recuperação de Senha
        txtEsqueci.setOnClickListener {
            val email = edtEmail.text.toString().trim().lowercase()

            if (email.isEmpty()) {
                edtEmail.error = "Digite seu e-mail para recuperar a senha"
                edtEmail.requestFocus()
                return@setOnClickListener
            }

            authRepository.recuperarSenha(
                email,
                onSuccess = {
                    Toast.makeText(this, "Link de recuperação enviado para: $email", Toast.LENGTH_LONG).show()
                    behavior.state = BottomSheetBehavior.STATE_HIDDEN // Esconde o painel após o envio
                },
                onError = { erro ->
                    Toast.makeText(this, "Erro: ${erro.message}", Toast.LENGTH_LONG).show()
                }
            )
        }

        // Ir para a tela de Cadastro
        btnCadastrar.setOnClickListener {
            startActivity(Intent(this, TipoCadastroActivity::class.java))
        }
    }
}