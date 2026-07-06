package com.example.trabalhograua.cadastro.motorista

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.trabalhograua.R

class AutorizacaoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // LIGA ESTA ACTIVITY AO XML DE AUTORIZAÇÃO
        setContentView(R.layout.activity_autorizacao)

        // BOTÃO CONTINUAR
        val btnContinuar = findViewById<Button>(R.id.btnContinuarAutorizacao)

        // AO CLICAR, ABRE A TELA FINAL DE ANÁLISE DOS DOCUMENTOS
        btnContinuar.setOnClickListener {
            startActivity(Intent(this, AnaliseDocumentosActivity::class.java))
        }
    }
}