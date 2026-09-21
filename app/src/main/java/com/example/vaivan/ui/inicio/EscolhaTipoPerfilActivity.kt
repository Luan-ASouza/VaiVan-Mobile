package com.example.vaivan.ui.inicio

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.dao.UsuarioDao
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.ui.motorista.cadastro.documentos.VerificarProgressoDocumentos
import com.example.vaivan.ui.responsavel.HomeResponsavelActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

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