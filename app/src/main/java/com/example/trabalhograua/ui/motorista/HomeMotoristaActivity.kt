package com.example.trabalhograua.ui.motorista

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.trabalhograua.R
import com.example.trabalhograua.chat.ChatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class HomeMotoristaActivity : AppCompatActivity() {

    // UID do responsável de teste (Firebase Authentication)
    private val UID_RESPONSAVEL_TESTE = "2ESSCaVomchf3R4K9jJ2yP6jbZw2"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_motorista)

        val fabChat = findViewById<FloatingActionButton>(R.id.fabChat)
        fabChat.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("outroUsuarioId", UID_RESPONSAVEL_TESTE)
            intent.putExtra("nomeContato", "Responsável")
            startActivity(intent)
        }
    }
}