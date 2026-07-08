package com.example.trabalhograua

import android.content.Intent
import com.example.trabalhograua.repository.FirebaseAuthRepository
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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

        val btnEntrar = findViewById<Button>(R.id.btnEntrar)

        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnCadastrar = findViewById<Button>(R.id.btnCadastrar)

        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)

        behavior = BottomSheetBehavior.from(bottomSheet)

        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        // Abre o painel
        btnEntrar.setOnClickListener {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        // Login
        btnLogin.setOnClickListener {

            val email = edtEmail.text.toString().trim().lowercase()

            val senha = edtSenha.text.toString().trim()


            //Login com autenticacao firebase
            authRepository.login(

                email,
                senha,

                onSuccess = {

                    Toast.makeText(
                        this,
                        "Login realizado com sucesso!",
                        Toast.LENGTH_SHORT
                    ).show()

                    startActivity(
                        Intent(this, PerfilActivity::class.java)
                    )

                    finish()
                },

                onError = { erro ->

                    Toast.makeText(
                        this,
                        erro.message,
                        Toast.LENGTH_LONG
                    ).show()

                }

            )
        }

        // Cadastrar
        btnCadastrar.setOnClickListener {

            startActivity(
                Intent(this, TipoCadastroActivity::class.java)
            )
        }
    }
}