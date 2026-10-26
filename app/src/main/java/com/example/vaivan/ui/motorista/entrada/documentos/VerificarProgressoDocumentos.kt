package com.example.vaivan.ui.motorista.entrada.documentos

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.vaivan.ui.motorista.HomeMotoristaActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Decide qual é a próxima tela do cadastro do motorista, olhando os documentos
 * obrigatórios (coleção "documentos"): CNH (frente e verso) e comprovante de residência.
 *
 *  - falta algum documento, ou algum foi REJEITADO  -> statusMotorista = PENDENTE e abre o envio;
 *  - todos enviados e algum ainda em análise        -> statusMotorista = EM_ANALISE e abre a tela de análise;
 *  - todos APROVADOS                                -> abre a Home.
 *
 * O status ATIVO só é gravado pelo painel administrativo, quando aprova o último documento.
 */
class VerificarProgressoDocumentos(
    private val context: Context
) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        const val TIPO_CNH_FRENTE = "CNH_FRENTE"
        const val TIPO_CNH_VERSO = "CNH_VERSO"
        const val TIPO_COMPROVANTE = "COMPROVANTE_RESIDENCIA"

        const val ANALISE = "EM_ANALISE"
        const val APROVADO = "APROVADO"
        const val REJEITADO = "REJEITADO"

        /** Id do documento: um por motorista e por tipo (reenviar substitui o anterior). */
        fun idDoDocumento(uid: String, tipo: String) = "${uid}_$tipo"
    }

    fun verificar() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("documentos")
            .whereEqualTo("motoristaId", uid)
            .get()
            .addOnSuccessListener { resultado ->

                // tipo -> status (ignora documentos de veículo e outros tipos)
                val status = resultado.documents
                    .mapNotNull { d ->
                        val tipo = d.getString("tipo") ?: return@mapNotNull null
                        tipo to (d.getString("statusValidacao") ?: ANALISE)
                    }
                    .toMap()

                determinarProximaTela(uid, status)
            }
            .addOnFailureListener {
                Toast.makeText(
                    context, "Não foi possível verificar seus documentos.", Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun precisaEnviar(status: String?) = status == null || status == REJEITADO

    private fun determinarProximaTela(uid: String, status: Map<String, String>) {

        val cnhPendente = precisaEnviar(status[TIPO_CNH_FRENTE]) ||
                precisaEnviar(status[TIPO_CNH_VERSO])
        val comprovantePendente = precisaEnviar(status[TIPO_COMPROVANTE])

        val obrigatorios = listOf(TIPO_CNH_FRENTE, TIPO_CNH_VERSO, TIPO_COMPROVANTE)

        when {
            cnhPendente ->
                gravarStatusEAbrir(uid, "PENDENTE", EnvioCnhActivity::class.java)

            comprovantePendente ->
                gravarStatusEAbrir(uid, "PENDENTE", EnvioComprovanteResidenciaActivity::class.java)

            obrigatorios.all { status[it] == APROVADO } ->
                abrirTela(HomeMotoristaActivity::class.java)

            else ->
                gravarStatusEAbrir(uid, "EM_ANALISE", AnaliseDocumentosActivity::class.java)
        }
    }

    private fun gravarStatusEAbrir(uid: String, statusMotorista: String, tela: Class<*>) {
        db.collection("usuarios")
            .document(uid)
            .update("statusMotorista", statusMotorista)
            .addOnCompleteListener { abrirTela(tela) }
    }

    private fun abrirTela(activity: Class<*>) {
        context.startActivity(Intent(context, activity))
    }
}