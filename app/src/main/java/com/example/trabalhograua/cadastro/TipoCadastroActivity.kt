package com.example.trabalhograua.cadastro

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.trabalhograua.cadastro.motorista.DadosDeAcessoMotorista
import com.example.trabalhograua.cadastro.responsavel.DadosDeAcessoResponsavel
import com.example.trabalhograua.R

class TipoCadastroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // LIGA ESTA ACTIVITY AO XML DA TELA DE ESCOLHA DO TIPO DE CADASTRO
        setContentView(R.layout.activity_tipo_cadastro)

        // BOTÃO MOTORISTA
        val btnMotorista = findViewById<Button>(R.id.btnMotorista)

        // BOTÃO RESPONSÁVEL
        val btnResponsavel = findViewById<Button>(R.id.btnResponsavel)

        // AO CLICAR EM MOTORISTA, ABRE O FLUXO DO MOTORISTA
        btnMotorista.setOnClickListener {
            startActivity(Intent(this, DadosDeAcessoMotorista::class.java))
        }

        // AO CLICAR EM RESPONSÁVEL, ABRE O FLUXO DO RESPONSÁVEL
        btnResponsavel.setOnClickListener {
            startActivity(Intent(this, DadosDeAcessoResponsavel::class.java))
        }
    }
}