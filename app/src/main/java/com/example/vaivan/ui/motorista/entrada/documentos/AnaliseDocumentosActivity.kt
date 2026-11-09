package com.example.vaivan.ui.motorista.entrada.documentos

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.ui.inicio.LoginActivity
import com.example.vaivan.ui.motorista.HomeMotoristaActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.vaivan.core.notificacao.TokenDispositivo

/**
 * Tela de espera: os documentos já foram enviados e estão em análise.
 * Acompanha o cadastro em tempo real: aprovado -> Home; rejeitado -> volta
 * para a tela de documentos. Permite sair da conta.
 */
class AnaliseDocumentosActivity : BaseActivity() {

    private var observador: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_analise_documentos)

        findViewById<View>(R.id.btnSair).setOnClickListener {
            sair()
        }

        aplicarGapsDeSistema()
    }

    override fun onStart() {
        super.onStart()
        observarSituacao()
    }

    override fun onStop() {
        observador?.remove()
        observador = null
        super.onStop()
    }

    private fun observarSituacao() {

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        observador = FirebaseFirestore.getInstance()
            .collection("usuarios")
            .document(uid)
            .addSnapshotListener { snapshot, erro ->

                if (erro != null || snapshot == null || !snapshot.exists()) {
                    return@addSnapshotListener
                }

                when (snapshot.getString("statusMotorista")) {

                    "ATIVO" -> abrirTela(HomeMotoristaActivity::class.java)

                    // Algum documento foi rejeitado: o motorista precisa reenviar
                    null, "PENDENTE" -> abrirTela(DocumentosMotoristaActivity::class.java)

                    // EM_ANALISE (continua aguardando) e demais: não faz nada
                    else -> Unit
                }
            }
    }

    /** Mesmo fluxo de "Desconectar" do perfil: limpa o cache local e encerra a sessão. */
    private fun sair() {

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    VaivanDatabase.getInstance(this@AnaliseDocumentosActivity).clearAllTables()
                }

                TokenDispositivo.removerDesteDispositivo()

                FirebaseAuth.getInstance().signOut()

                startActivity(
                    Intent(this@AnaliseDocumentosActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@AnaliseDocumentosActivity,
                    "Não foi possível sair. Tente novamente.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun abrirTela(tela: Class<*>) {

        startActivity(
            Intent(this, tela).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )

        finish()
    }
}