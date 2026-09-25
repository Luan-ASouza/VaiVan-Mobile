package com.example.vaivan.ui.inicio

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.vaivan.R
import com.example.vaivan.ui.motorista.entrada.EntradaMotoristaActivity
import com.example.vaivan.ui.motorista.entrada.documentos.VerificarProgressoDocumentos
import com.example.vaivan.ui.responsavel.HomeResponsavelActivity

class EscolhaTipoPerfilActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_perfil)

        val btnMotorista = findViewById<Button>(R.id.btnMotorista)
        val btnResponsavel = findViewById<Button>(R.id.btnResponsavel)

        btnMotorista.setOnClickListener {
            val intent = Intent(this, EntradaMotoristaActivity::class.java)
            startActivity(intent)
        }

        // ALTERADO AQUI: Agora direciona para a sua tela de passageiros!
        btnResponsavel.setOnClickListener {
            val intent = Intent(this, HomeResponsavelActivity::class.java)
            startActivity(intent)
        }
    }
}