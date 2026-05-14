package com.example.trabalhograua

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PerfilActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_perfil)

        val btnMotorista = findViewById<Button>(R.id.btnMotorista)
        val btnResponsavel = findViewById<Button>(R.id.btnResponsavel)
        val btnEstudante = findViewById<Button>(R.id.btnEstudante)
        val btnVoltar = findViewById<ImageView>(R.id.btnVoltar)

        btnMotorista.setOnClickListener {
            startActivity(Intent(this, MotoristaActivity::class.java))
        }

        btnResponsavel.setOnClickListener {
            Toast.makeText(
                this,
                "Responsável selecionado",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnEstudante.setOnClickListener {
            startActivity(Intent(this, EstudanteActivity::class.java))
        }

        btnVoltar.setOnClickListener {
            finish()
        }
    }
}