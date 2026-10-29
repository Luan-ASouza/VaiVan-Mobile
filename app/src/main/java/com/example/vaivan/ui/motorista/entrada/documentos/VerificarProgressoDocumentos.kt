package com.example.vaivan.ui.motorista.entrada.documentos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.vaivan.ui.motorista.HomeMotoristaActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class VerificarProgressoDocumentos(
    private val context: Context
) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        const val TIPO_CNH_FRENTE = "CNH_FRENTE"
        const val TIPO_CNH_VERSO = "CNH_VERSO"
        const val TIPO_CNH_DIGITAL = "CNH_DIGITAL_PDF"
        const val TIPO_COMPROVANTE = "COMPROVANTE_RESIDENCIA"

        const val ANALISE = "EM_ANALISE"
        const val APROVADO = "APROVADO"
        const val REJEITADO = "REJEITADO"

        fun idDoDocumento(
            uid: String,
            tipo: String
        ) = "${uid}_$tipo"
    }

    fun verificar() {

        val uid = auth.currentUser?.uid

        if (uid == null) {
            mostrarErro("Usuário não autenticado.")
            return
        }

        db.collection("usuarios")
            .document(uid)
            .get()
            .addOnSuccessListener { usuario ->

                if (!usuario.exists()) {
                    mostrarErro("Cadastro não encontrado.")
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
                        mostrarErro("Seu cadastro de motorista está indisponível.")
                    }

                    else -> {
                        mostrarErro("Status do motorista desconhecido.")
                    }
                }
            }
            .addOnFailureListener {
                mostrarErro("Não foi possível verificar seu cadastro.")
            }
    }

    private fun abrirTela(tela: Class<*>) {

        val intent = Intent(context, tela)

        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)

        if (context is Activity) {
            context.finish()
        }
    }

    private fun mostrarErro(mensagem: String) {
        Toast.makeText(
            context,
            mensagem,
            Toast.LENGTH_SHORT
        ).show()
    }
}