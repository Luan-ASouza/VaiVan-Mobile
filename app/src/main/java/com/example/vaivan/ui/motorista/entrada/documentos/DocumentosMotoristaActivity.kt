package com.example.vaivan.ui.motorista.entrada.documentos

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.DocumentoEntity
import com.example.vaivan.data.models.StatusDocumento
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.ui.inicio.EscolhaTipoPerfilActivity
import com.example.vaivan.ui.motorista.HomeMotoristaActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch


/**
 * Documentos do motorista. Dois usos:
 *  - cadastro (EXTRA_CADASTRO): motorista ainda não aprovado. Mostra o status de cada
 *    documento, permite reenviar e, quando o painel aprova (statusMotorista = ATIVO),
 *    segue sozinho para a Home;
 *  - "Meus documentos" (aberto pela Home): só consulta/troca de documentos.
 */
class DocumentosMotoristaActivity : BaseActivity() {

    companion object {
        const val EXTRA_CADASTRO = "documentos_cadastro"
    }

    private val auth = FirebaseAuth.getInstance()
    private var observadorCadastro: ListenerRegistration? = null
    private val modoCadastro by lazy { intent.getBooleanExtra(EXTRA_CADASTRO, false) }
    private lateinit var documentoRepository: DocumentoRepository

    private lateinit var itemCnh: View
    private lateinit var itemComprovante: View
    private lateinit var itemFoto: View

    private lateinit var txtStatusCnh: TextView
    private lateinit var txtStatusComprovante: TextView
    private lateinit var txtStatusFoto: TextView

    private lateinit var txtMotivoCnh: TextView
    private lateinit var txtMotivoComprovante: TextView
    private lateinit var txtMotivoFoto: TextView

    private lateinit var txtProgresso: TextView
    private lateinit var barraProgresso: ProgressBar

    private lateinit var iconeCnh: ImageView
    private lateinit var iconeComprovante: ImageView
    private lateinit var iconeFoto: ImageView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_documentos_motorista)

        documentoRepository = DocumentoRepository(VaivanDatabase.getInstance(this).documentoDao())

        inicializarViews()
        configurarCliques()
        observarDocumentos()

        aplicarGapsDeSistema()
    }

    override fun onStart() {
        super.onStart()
        if (modoCadastro) observarCadastro()
    }

    override fun onStop() {
        observadorCadastro?.remove()
        observadorCadastro = null
        documentoRepository.pararSincronizacao()
        super.onStop()
    }

    /** Cadastro: acompanha o statusMotorista para seguir à Home quando o painel aprovar. */
    private fun observarCadastro() {
        val uid = auth.currentUser?.uid ?: return
        observadorCadastro = FirebaseFirestore.getInstance()
            .collection("usuarios")
            .document(uid)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                if (snapshot.getString("statusMotorista") == "ATIVO") {
                    startActivity(
                        Intent(this, HomeMotoristaActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    finish()
                }
            }
    }

    private fun inicializarViews() {

        itemCnh = findViewById(R.id.itemCnh)
        itemComprovante = findViewById(R.id.itemComprovante)
        itemFoto = findViewById(R.id.itemFoto)

        txtStatusCnh = findViewById(R.id.txtStatusCnh)
        txtStatusComprovante = findViewById(R.id.txtStatusComprovante)
        txtStatusFoto = findViewById(R.id.txtStatusFoto)

        txtMotivoCnh = findViewById(R.id.txtMotivoCnh)
        txtMotivoComprovante = findViewById(R.id.txtMotivoComprovante)
        txtMotivoFoto = findViewById(R.id.txtMotivoFoto)

        txtProgresso = findViewById(R.id.txtProgressoDocumentos)
        barraProgresso = findViewById(R.id.barraProgressoDocumentos)

        iconeCnh = findViewById(R.id.iconeCnh)
        iconeComprovante = findViewById(R.id.iconeComprovante)
        iconeFoto = findViewById(R.id.iconeFoto)

        swipeRefresh = findViewById(R.id.swipeRefreshDocumentosMotorista)
        swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                try {
                    documentoRepository.sincronizarUmaVez()
                } finally {
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun configurarCliques() {

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            if (modoCadastro && isTaskRoot) {
                // No cadastro esta é a única tela da pilha: volta à escolha de perfil
                // (sem encerrar a sessão), como na tela de análise.
                startActivity(
                    Intent(this, EscolhaTipoPerfilActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
            finish()
        }

        findViewById<View>(R.id.btnAjuda).setOnClickListener {
            Toast.makeText(
                this,
                "Envie os documentos para concluir seu cadastro.",
                Toast.LENGTH_LONG
            ).show()
        }

        itemCnh.setOnClickListener {
            startActivity(
                Intent(this, EnvioCnhActivity::class.java)
            )
        }

        itemComprovante.setOnClickListener {
            startActivity(
                Intent(this, EnvioComprovanteResidenciaActivity::class.java)
            )
        }

        itemFoto.setOnClickListener {
            startActivity(
                Intent(this, CapturaSelfieActivity::class.java)
            )
        }
    }

    private fun observarDocumentos() {

        val motoristaId = auth.currentUser?.uid

        if (motoristaId == null) {
            Toast.makeText(
                this,
                "Usuário não autenticado.",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        documentoRepository.iniciarSincronizacao()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                documentoRepository.observarPorMotorista(motoristaId).collect { documentos ->
                    atualizarDocumentos(documentos)
                }
            }
        }
    }

    private fun atualizarDocumentos(documentos: List<DocumentoEntity>) {

        val cnh = documentos.find { it.tipo == DocumentoRepository.TIPO_CNH }
        val comprovante = documentos.find { it.tipo == DocumentoRepository.TIPO_COMPROVANTE_RESIDENCIA }
        val selfie = documentos.find { it.tipo == DocumentoRepository.TIPO_SELFIE }

        val statusCnh = pintarStatusDocumento(iconeCnh, txtStatusCnh, txtMotivoCnh, cnh)
        val statusComprovante =
            pintarStatusDocumento(iconeComprovante, txtStatusComprovante, txtMotivoComprovante, comprovante)
        val statusSelfie = pintarStatusDocumento(iconeFoto, txtStatusFoto, txtMotivoFoto, selfie)

        // Progresso: conta os documentos já enviados (em análise ou aprovados)
        val enviados = listOf(statusCnh, statusComprovante, statusSelfie).count {
            it == StatusDocumento.EM_ANALISE || it == StatusDocumento.APROVADO
        }

        barraProgresso.progress = enviados
        val aprovados = listOf(statusCnh, statusComprovante, statusSelfie).count {
            it == StatusDocumento.APROVADO
        }

        txtProgresso.text = if (enviados == 3 && modoCadastro && aprovados < 3) {
            "Documentos em análise"
        } else if (enviados == 3) {
            "Todos os documentos foram enviados"
        } else {
            "$enviados de 3 documentos enviados"
        }
    }

}