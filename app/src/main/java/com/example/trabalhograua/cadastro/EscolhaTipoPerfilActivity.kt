package com.example.trabalhograua.cadastro

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.trabalhograua.R
import com.example.trabalhograua.cadastro.motorista.documentos.VerificarProgressoDocumentos
import com.example.trabalhograua.ui.responsavel.HomeResponsavelActivity

class EscolhaTipoPerfilActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_perfil)

        val btnMotorista = findViewById<Button>(R.id.btnMotorista)
        val btnResponsavel = findViewById<Button>(R.id.btnResponsavel)

        btnMotorista.setOnClickListener {
            VerificarProgressoDocumentos(this).verificar()
        }

        // ALTERADO AQUI: Agora direciona para a sua tela de passageiros!
        btnResponsavel.setOnClickListener {
            val intent = Intent(this, HomeResponsavelActivity::class.java)
            startActivity(intent)
        }

    }
}