package com.example.vaivan.ui.motorista.entrada.documentos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.vaivan.data.models.StatusDocumento
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.ui.motorista.HomeMotoristaActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Decide a próxima tela do cadastro do motorista, chamada depois de cada envio:
 *  - ATIVO (aprovado pelo painel)                      -> Home;
 *  - os 3 documentos enviados (CNH, comprovante, foto) -> EM_ANALISE e tela de espera;
 *  - falta algum, ou algum foi rejeitado               -> PENDENTE e tela de documentos.
 *
 * O status ATIVO só é gravado pelo painel administrativo.
 */
class VerificarProgressoDocumentos(
    private val context: Context
) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val obrigatorios = listOf(
        DocumentoRepository.TIPO_CNH,
        DocumentoRepository.TIPO_COMPROVANTE_RESIDENCIA,
        DocumentoRepository.TIPO_SELFIE
    )

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

                val statusAtual = usuario.getString("statusMotorista")

                when (statusAtual) {

                    "ATIVO" -> abrirTela(HomeMotoristaActivity::class.java, limparPilha = true)

                    "SUSPENSO", "INATIVO" ->
                        mostrarErro("Seu cadastro de motorista está indisponível.")

                    else -> avaliarDocumentos(uid, statusAtual)
                }
            }
            .addOnFailureListener {
                mostrarErro("Não foi possível verificar seu cadastro.")
            }
    }

    private fun avaliarDocumentos(uid: String, statusAtual: String?) {

        db.collection("documentos")
            .whereEqualTo("motoristaId", uid)
            .get()
            .addOnSuccessListener { resultado ->

                // tipo -> status (ignora os documentos de veículo)
                val status = resultado.documents
                    .mapNotNull { documento ->
                        val tipo = documento.getString("tipo") ?: return@mapNotNull null
                        tipo to StatusDocumento.fromString(documento.getString("statusValidacao"))
                    }
                    .toMap()

                val tudoEnviado = obrigatorios.all { tipo ->
                    val atual = status[tipo]
                    atual == StatusDocumento.EM_ANALISE || atual == StatusDocumento.APROVADO
                }

                val novoStatus = if (tudoEnviado) "EM_ANALISE" else "PENDENTE"

                val tela = if (tudoEnviado) {
                    AnaliseDocumentosActivity::class.java
                } else {
                    DocumentosMotoristaActivity::class.java
                }

                if (novoStatus == statusAtual) {
                    abrirTela(tela, limparPilha = tudoEnviado)
                } else {
                    db.collection("usuarios")
                        .document(uid)
                        .update("statusMotorista", novoStatus)
                        .addOnCompleteListener {
                            abrirTela(tela, limparPilha = tudoEnviado)
                        }
                }
            }
            .addOnFailureListener {
                mostrarErro("Não foi possível verificar seus documentos.")
            }
    }

    /**
     * limparPilha = true  : tela de destino vira a única do aplicativo (Home e tela de espera);
     * limparPilha = false : reaproveita a tela de documentos que já está aberta, em vez de
     *                       empilhar uma cópia por cima da outra.
     */
    private fun abrirTela(tela: Class<*>, limparPilha: Boolean) {

        val intent = Intent(context, tela)

        if (limparPilha) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
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