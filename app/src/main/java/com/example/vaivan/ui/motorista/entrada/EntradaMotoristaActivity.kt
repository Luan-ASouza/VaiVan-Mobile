package com.example.vaivan.ui.motorista.entrada

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.vaivan.ui.inicio.LoginActivity
import com.example.vaivan.ui.inicio.cadastro.CadastroActivity
import com.example.vaivan.ui.motorista.HomeMotoristaActivity
import com.example.vaivan.ui.motorista.entrada.documentos.AnaliseDocumentosActivity
import com.example.vaivan.ui.motorista.entrada.documentos.EnvioCnhActivity
import com.example.vaivan.ui.motorista.entrada.documentos.StatusDocumentosVeiculoActivity
import com.example.vaivan.ui.motorista.entrada.documentos.VerificarProgressoDocumentos
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EntradaMotoristaActivity : AppCompatActivity() {

    private val auth =
        FirebaseAuth.getInstance()

    private val firestore =
        FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        verificarCadastroMotorista()
    }

    private fun verificarCadastroMotorista() {

        val usuarioId =
            auth.currentUser?.uid

        if (usuarioId == null) {
            abrirLogin()
            return
        }

        firestore
            .collection("usuarios")
            .document(usuarioId)
            .get()
            .addOnSuccessListener { documento ->

                if (!documento.exists()) {
                    abrirLogin()
                    return@addOnSuccessListener
                }

                val statusMotorista =
                    documento.getString("statusMotorista")

                when (statusMotorista) {

                    null -> {
                        // Usuário ainda não iniciou
                        // o cadastro de motorista.
                        abrirCadastroMotorista()
                    }

                    "PENDENTE" -> {
                        // Faltam documentos ou informações.
                        abrirCadastroPendente()
                    }

                    "EM_ANALISE" -> {
                        // Documentos enviados,
                        // aguardando aprovação.
                        abrirEmAnalise()
                    }

                    "ATIVO" -> {
                        // Motorista aprovado.
                        abrirHomeMotorista()
                    }

                    "SUSPENSO" -> {
                        // Motorista teve o acesso suspenso.
                        abrirMotoristaSuspenso()
                    }

                    else -> {
                        // Status desconhecido.
                        abrirCadastroMotorista()
                    }
                }
            }
            .addOnFailureListener {
                // Se não conseguiu consultar o Firebase,
                // não libera o acesso à Home.
                finish()
            }
    }

    private fun abrirCadastroMotorista() {

        startActivity(
            Intent(
                this,
                EnvioCnhActivity::class.java
            )
        )

        finish()
    }

    private fun abrirCadastroPendente() {

        val verificarProgressoDocumentos = VerificarProgressoDocumentos(this)

        verificarProgressoDocumentos.verificar()

        finish()
    }

    private fun abrirEmAnalise() {

        startActivity(
            Intent(
                this,
                StatusDocumentosVeiculoActivity::class.java
            )
        )

        finish()
    }

    private fun abrirHomeMotorista() {

        startActivity(
            Intent(
                this,
                HomeMotoristaActivity::class.java
            )
        )

        finish()
    }

    private fun abrirMotoristaSuspenso() {

        // Crie essa Activity quando implementar
        // a tela de motorista suspenso.

        /*
        startActivity(
            Intent(
                this,
                StatusMotoristaActivity::class.java
            )
        )

        finish()
        */

        finish()
    }

    private fun abrirLogin() {

        val intent =
            Intent(
                this,
                LoginActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        startActivity(intent)
        finish()
    }
}