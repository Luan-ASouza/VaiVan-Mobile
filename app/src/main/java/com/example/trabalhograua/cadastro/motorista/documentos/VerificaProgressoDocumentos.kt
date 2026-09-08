package com.example.trabalhograua.cadastro.motorista.documentos

import android.content.Context
import android.content.Intent
import com.example.trabalhograua.ui.motorista.HomeMotoristaActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class VerificarProgressoDocumentos(
    private val context: Context
) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun verificar() {

        val usuario = auth.currentUser

        if (usuario == null) {
            return
        }

        val uid = usuario.uid

        // Busca a CNH
        db.collection("responsaveis")
            .document(uid)
            .collection("documentos")
            .document("cnh")
            .get()
            .addOnSuccessListener { cnhDocument ->

                val cnh = cnhDocument.getString("statusValidacao") ?: "pendente"

                // Busca o comprovante
                db.collection("responsaveis")
                    .document(uid)
                    .collection("documentos")
                    .document("comprovanteResidencia")
                    .get()
                    .addOnSuccessListener { comprovanteDocument ->

                        val comprovante =
                            comprovanteDocument.getString("statusValidacao") ?: "pendente"

                        // Busca a verificação facial
                        db.collection("responsaveis")
                            .document(uid)
                            .collection("documentos")
                            .document("facial")
                            .get()
                            .addOnSuccessListener { facialDocument ->

                                val facial =
                                    facialDocument.getString("statusValidacao") ?: "pendente"

                                determinarProximaTela(
                                    cnh,
                                    comprovante,
                                    facial
                                )
                            }
                    }
            }
            .addOnFailureListener {
                // Erro ao consultar o Firebase
            }
    }

    private fun determinarProximaTela(
        cnh: String,
        comprovante: String,
        facial: String
    ) {

        when {

            cnh == "pendente" || cnh == "reprovado" -> {
                abrirTela(EnvioCnhActivity::class.java)
            }

            comprovante == "pendente" || comprovante == "reprovado" -> {
                abrirTela(EnvioComprovanteResidenciaActivity::class.java)
            }

//            facial == "pendente" || facial == "reprovado" -> {
//                abrirTela(VerificacaoFacialActivity::class.java)
//            }

            else -> {
                abrirTela(HomeMotoristaActivity::class.java)
            }
        }
    }

    private fun abrirTela(activity: Class<*>) {
        val intent = Intent(context, activity)
        context.startActivity(intent)
    }
}