package com.example.trabalhograua

import android.content.Intent
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

            if (email == "admin@gmail.com" && senha == "123456") {

                Toast.makeText(
                    this,
                    "Login realizado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(
                    Intent(this, PerfilActivity::class.java)
                )

            } else {

                Toast.makeText(
                    this,
                    "Email ou senha inválidos",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Cadastrar
        btnCadastrar.setOnClickListener {

            startActivity(
                Intent(this, TipoCadastroActivity::class.java)
            )
        }
    }
}