package com.example.trabalhograua

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ValidacaoEmailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // LIGA ESTA ACTIVITY AO XML DE VALIDAÇÃO DE EMAIL
        setContentView(R.layout.activity_validacao_email)

        // BOTÃO CONTINUAR
        val btnContinuar = findViewById<Button>(R.id.btnContinuarValidacao)

        // AO CLICAR, ABRE A TELA DE ENDEREÇO DO MOTORISTA
        btnContinuar.setOnClickListener {
            startActivity(Intent(this, EnderecoMotoristaActivity::class.java))
        }
    }
}