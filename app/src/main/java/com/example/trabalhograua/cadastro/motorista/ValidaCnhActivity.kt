package com.example.trabalhograua.cadastro.motorista

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.trabalhograua.R

class ValidaCnhActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // LIGA ESTA ACTIVITY AO XML DE VALIDAÇÃO DA CNH
        setContentView(R.layout.activity_valida_cnh)

        // BOTÃO ENVIAR E CONTINUAR
        val btnContinuar = findViewById<Button>(R.id.btnEnviarContinuarCnh)

        // AO CLICAR, ABRE A TELA DE AUTORIZAÇÃO
        btnContinuar.setOnClickListener {
            startActivity(Intent(this, AutorizacaoActivity::class.java))
        }
    }
}