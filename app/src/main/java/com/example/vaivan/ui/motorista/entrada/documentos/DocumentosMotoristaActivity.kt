package com.example.vaivan.ui.motorista.entrada.documentos

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

    private lateinit var itemCnh: LinearLayout
    private lateinit var itemComprovante: LinearLayout
    private lateinit var itemFoto: LinearLayout

    private lateinit var txtStatusCnh: TextView
    private lateinit var txtStatusComprovante: TextView
    private lateinit var txtStatusFoto: TextView

    private lateinit var txtMotivoCnh: TextView
    private lateinit var txtMotivoComprovante: TextView
    private lateinit var txtMotivoFoto: TextView

    private lateinit var txtProgresso: TextView

    private lateinit var iconeCnh: ImageView
    private lateinit var iconeComprovante: ImageView
    private lateinit var iconeFoto: ImageView

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

        iconeCnh = findViewById(R.id.iconeCnh)
        iconeComprovante = findViewById(R.id.iconeComprovante)
        iconeFoto = findViewById(R.id.iconeFoto)
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

        val statusCnh = statusDe(cnh)
        val statusComprovante = statusDe(comprovante)
        val statusSelfie = statusDe(selfie)

        atualizarStatusVisual(txtStatusCnh, iconeCnh, txtMotivoCnh, cnh, statusCnh)
        atualizarStatusVisual(txtStatusComprovante, iconeComprovante, txtMotivoComprovante, comprovante, statusComprovante)
        atualizarStatusVisual(txtStatusFoto, iconeFoto, txtMotivoFoto, selfie, statusSelfie)

        // Progresso: conta os documentos já enviados (em análise ou aprovados)
        val enviados = listOf(statusCnh, statusComprovante, statusSelfie).count {
            it == StatusDocumento.EM_ANALISE || it == StatusDocumento.APROVADO
        }

        txtProgresso.text = if (enviados == 3) {
            "Todos os documentos foram enviados"
        } else {
            "$enviados de 3 documentos enviados"
        }
    }

    private fun statusDe(documento: DocumentoEntity?): StatusDocumento =
        documento?.let { StatusDocumento.fromString(it.statusValidacao) }
            ?: StatusDocumento.NAO_ENVIADO

    private fun atualizarStatusVisual(
        texto: TextView,
        icone: ImageView,
        motivo: TextView,
        documento: DocumentoEntity?,
        status: StatusDocumento
    ) {

        // O motivo só aparece quando o documento foi rejeitado
        val motivoRejeicao = documento?.motivoRejeicao
        if (status == StatusDocumento.REJEITADO && !motivoRejeicao.isNullOrBlank()) {
            motivo.text = "Motivo: $motivoRejeicao"
            motivo.visibility = View.VISIBLE
        } else {
            motivo.visibility = View.GONE
        }

        when (status) {

            StatusDocumento.NAO_ENVIADO, StatusDocumento.PENDENTE -> {
                texto.visibility = View.GONE

                icone.setImageResource(R.drawable.ic_enviar_arquivo)
                icone.clearColorFilter()
            }

            StatusDocumento.EM_ANALISE -> {
                texto.text = "Em análise"
                texto.visibility = View.VISIBLE
                texto.setTextColor(Color.parseColor("#E5A000"))

                icone.setImageResource(R.drawable.ic_analise)
                icone.setColorFilter(Color.parseColor("#E5A000"))
            }

            StatusDocumento.APROVADO -> {
                texto.text = "Aprovado"
                texto.visibility = View.VISIBLE
                texto.setTextColor(Color.parseColor("#2E9D62"))

                icone.setImageResource(R.drawable.ic_aprovado)
                icone.setColorFilter(Color.parseColor("#2E9D62"))
            }

            StatusDocumento.REJEITADO -> {
                texto.text = "Rejeitado - reenviar documento"
                texto.visibility = View.VISIBLE
                texto.setTextColor(Color.parseColor("#D64545"))

                icone.setImageResource(R.drawable.ic_rejeitado)
                icone.setColorFilter(Color.parseColor("#D64545"))
            }
        }
    }
}