package com.example.vaivan.ui.motorista.entrada.documentos

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.vaivan.R
import com.example.vaivan.ui.motorista.entrada.EntradaMotoristaActivity

class AnaliseDocumentosActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // LIGA ESTA ACTIVITY AO XML DA TELA FINAL DE ANÁLISE
        setContentView(R.layout.activity_analise_documentos)

        // "Continuar" consulta de novo a situação do cadastro:
        // aprovado -> Home; rejeitado -> reenvio; ainda em análise -> volta aqui.
        findViewById<Button>(R.id.btnContinuarAnalise).setOnClickListener {
            startActivity(Intent(this, EntradaMotoristaActivity::class.java))
            finish()
        }
    }
}