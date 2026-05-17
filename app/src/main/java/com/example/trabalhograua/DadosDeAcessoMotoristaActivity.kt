package com.example.trabalhograua

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class DadosDeAcessoMotoristaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // LIGA ESTA ACTIVITY AO XML DE DADOS DE ACESSO DO MOTORISTA
        setContentView(R.layout.activity_dados_de_acesso_motorista)

        // BOTÃO CADASTRAR
        val btnCadastrar = findViewById<Button>(R.id.btnCadastrar)

        // AO CLICAR, ABRE A TELA DE VALIDAÇÃO DE EMAIL
        btnCadastrar.setOnClickListener {
            startActivity(Intent(this, ValidacaoEmailActivity::class.java))
        }
    }
}