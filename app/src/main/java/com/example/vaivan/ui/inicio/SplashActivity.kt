package com.example.vaivan.ui.inicio

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val usuario = FirebaseAuth.getInstance().currentUser

        val destino = if (usuario == null) {
            LoginActivity::class.java
        } else {
            EscolhaTipoPerfilActivity::class.java
        }

        startActivity(Intent(this, destino))
        finish()
    }
}