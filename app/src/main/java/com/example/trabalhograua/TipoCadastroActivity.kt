package com.example.trabalhograua

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import kotlin.jvm.java

class TipoCadastroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tipo_cadastro)

        val btnMotorista = findViewById<Button>(R.id.btnMotorista)
        val btnResponsavel = findViewById<Button>(R.id.btnResponsavel)

        btnMotorista.setOnClickListener {

            val intent = Intent(this, CadastroActivity::class.java)
            intent.putExtra("tipo", "Motorista")
            startActivity(intent)
        }

        btnResponsavel.setOnClickListener {

            val intent = Intent(this, DadosDeAcessoResponsavel::class.java)
            intent.putExtra("tipo", "Responsável")
            startActivity(intent)
        }

    }
}