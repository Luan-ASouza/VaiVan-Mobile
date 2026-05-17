package com.example.trabalhograua

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CadastroActivity : AppCompatActivity() {

    companion object {

        var emailCadastrado = ""
        var senhaCadastrada = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_cadastro)

        val edtNome = findViewById<EditText>(R.id.edtNome)
        val edtCpf = findViewById<EditText>(R.id.edtCpf)
        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)
        val btnCadastrar = findViewById<Button>(R.id.btnCadastrar)

        btnCadastrar.setOnClickListener {

            val nome = edtNome.text.toString()
            val cpf = edtCpf.text.toString()
            val email = edtEmail.text.toString()
            val senha = edtSenha.text.toString()

            if (
                nome.isNotEmpty() &&
                cpf.isNotEmpty() &&
                email.isNotEmpty() &&
                senha.isNotEmpty()
            ) {

                emailCadastrado = email
                senhaCadastrada = senha

                Toast.makeText(
                    this,
                    "Usuário cadastrado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(
                    Intent(this, LoginActivity::class.java)
                )

            } else {

                Toast.makeText(
                    this,
                    "Preencha todos os campos",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}