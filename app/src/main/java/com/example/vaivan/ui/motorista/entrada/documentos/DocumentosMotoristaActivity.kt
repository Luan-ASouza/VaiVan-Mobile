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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch


class DocumentosMotoristaActivity : BaseActivity() {

    private val auth = FirebaseAuth.getInstance()
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

    override fun onStop() {
        documentoRepository.pararSincronizacao()
        super.onStop()
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
        txtProgresso.text = if (enviados == 3) {
            "Todos os documentos foram enviados"
        } else {
            "$enviados de 3 documentos enviados"
        }
    }

}