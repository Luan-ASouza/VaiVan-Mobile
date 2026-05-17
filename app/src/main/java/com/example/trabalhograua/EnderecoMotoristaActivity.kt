package com.example.trabalhograua

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class EnderecoMotoristaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // LIGA ESTA ACTIVITY AO XML DE ENDEREÇO DO MOTORISTA
        setContentView(R.layout.activity_endereco_motorista)

        // BOTÃO CONTINUAR
        val btnContinuar = findViewById<Button>(R.id.btnContinuarEndereco)

        // AO CLICAR, ABRE A TELA DE VALIDAÇÃO DA CNH
        btnContinuar.setOnClickListener {
            startActivity(Intent(this, ValidaCnhActivity::class.java))
        }
    }
}