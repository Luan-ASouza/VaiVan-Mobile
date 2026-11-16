package com.example.vaivan.ui.motorista.entrada.documentos

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.notificacao.PermissaoNotificacao
import com.example.vaivan.ui.inicio.EscolhaTipoPerfilActivity
import com.example.vaivan.ui.motorista.HomeMotoristaActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Tela de espera: os documentos já foram enviados e estão em análise.
 * Acompanha o cadastro em tempo real: aprovado -> Home; rejeitado -> volta
 * para a tela de documentos. Permite sair da conta.
 */
class AnaliseDocumentosActivity : BaseActivity() {

    private var observador: ListenerRegistration? = null

    // O motorista fica aguardando aqui: o push avisa quando o painel aprovar/recusar.
    private val permissaoNotificacao = PermissaoNotificacao(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_analise_documentos)
        permissaoNotificacao.solicitarSeNecessario()

        findViewById<View>(R.id.btnSair).setOnClickListener {
            voltarAoInicio()
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

    /**
     * "Voltar ao início": só volta para a escolha de perfil, SEM encerrar a sessão
     * (a tela diz que o motorista pode fechar o app e voltar depois). Desconectar
     * fica no "Desconectar" do perfil.
     */
    private fun voltarAoInicio() {

        startActivity(
            Intent(this, EscolhaTipoPerfilActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
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