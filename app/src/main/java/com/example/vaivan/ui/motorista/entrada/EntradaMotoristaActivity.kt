package com.example.vaivan.ui.motorista.entrada

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vaivan.ui.inicio.LoginActivity
import com.example.vaivan.ui.motorista.HomeMotoristaActivity
import com.example.vaivan.ui.motorista.entrada.documentos.DocumentosMotoristaActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EntradaMotoristaActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        verificarCadastroMotorista()
    }

    private fun verificarCadastroMotorista() {

        val uid = auth.currentUser?.uid

        if (uid == null) {
            abrirTela(LoginActivity::class.java)
            return
        }

        firestore.collection("usuarios")
            .document(uid)
            .get()
            .addOnSuccessListener { usuario ->

                if (!usuario.exists()) {
                    abrirTela(LoginActivity::class.java)
                    return@addOnSuccessListener
                }

                when (usuario.getString("statusMotorista")) {

                    "ATIVO" -> {
                        abrirTela(HomeMotoristaActivity::class.java)
                    }

                    null, "PENDENTE", "EM_ANALISE" -> {
                        abrirTela(DocumentosMotoristaActivity::class.java)
                    }

                    "SUSPENSO", "INATIVO" -> {
                        Toast.makeText(
                            this,
                            "Seu cadastro de motorista está indisponível.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }

                    else -> {
                        Toast.makeText(
                            this,
                            "Status do motorista desconhecido.",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Não foi possível verificar seu cadastro.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
    }

    private fun abrirTela(tela: Class<*>) {

        val intent = Intent(this, tela).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }
}