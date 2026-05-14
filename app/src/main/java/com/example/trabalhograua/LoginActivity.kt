package com.example.trabalhograua

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)

        btnEntrar.setOnClickListener {

            val email = edtEmail.text.toString().trim()
            val senha = edtSenha.text.toString().trim()

            // Login exemplo
            if (email == "admin@gmail.com" && senha == "123456") {

                Toast.makeText(
                    this,
                    "Login realizado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                // Ir para próxima tela
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

    }
}