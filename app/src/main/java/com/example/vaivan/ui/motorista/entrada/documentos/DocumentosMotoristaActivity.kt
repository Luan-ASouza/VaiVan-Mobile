package com.example.vaivan.ui.motorista.entrada.documentos

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vaivan.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot

class DocumentosMotoristaActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var itemCnh: LinearLayout
    private lateinit var itemComprovante: LinearLayout
    private lateinit var itemFoto: LinearLayout

    private lateinit var txtStatusCnh: TextView
    private lateinit var txtStatusComprovante: TextView
    private lateinit var txtStatusFoto: TextView

    private lateinit var iconeCnh: ImageView
    private lateinit var iconeComprovante: ImageView
    private lateinit var iconeFoto: ImageView

    private lateinit var progressDocumentos: ProgressBar

    private var listenerDocumentos: ListenerRegistration? = null

    private enum class StatusDocumento {
        PENDENTE,
        EM_ANALISE,
        APROVADO,
        REJEITADO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_documentos_motorista)

        inicializarViews()
        configurarCliques()
    }

    override fun onStart() {
        super.onStart()
        observarDocumentos()
    }

    override fun onStop() {
        listenerDocumentos?.remove()
        listenerDocumentos = null

        super.onStop()
    }

    private fun inicializarViews() {

        itemCnh = findViewById(R.id.itemCnh)
        itemComprovante = findViewById(R.id.itemComprovante)
        itemFoto = findViewById(R.id.itemFoto)

        txtStatusCnh = findViewById(R.id.txtStatusCnh)
        txtStatusComprovante = findViewById(R.id.txtStatusComprovante)
        txtStatusFoto = findViewById(R.id.txtStatusFoto)

        iconeCnh = findViewById(R.id.iconeCnh)
        iconeComprovante = findViewById(R.id.iconeComprovante)
        iconeFoto = findViewById(R.id.iconeFoto)

        progressDocumentos = findViewById(R.id.progressDocumentos)
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
                Intent(this, VerificacaoFacialActivity::class.java)
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

        listenerDocumentos?.remove()

        progressDocumentos.visibility = View.VISIBLE

        listenerDocumentos = firestore
            .collection("documentos")
            .whereEqualTo("motoristaId", motoristaId)
            .addSnapshotListener { snapshot, erro ->

                progressDocumentos.visibility = View.GONE

                if (erro != null) {
                    Toast.makeText(
                        this,
                        "Não foi possível consultar os documentos.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    atualizarDocumentos(snapshot)
                }
            }
    }

    private fun atualizarDocumentos(snapshot: QuerySnapshot) {

        val documentos = snapshot.documents

        // CNH
        val frente = documentos.find {
            it.getString("tipo") == "CNH_FRENTE"
        }

        val verso = documentos.find {
            it.getString("tipo") == "CNH_VERSO"
        }

        val digital = documentos.find {
            it.getString("tipo") == "CNH_DIGITAL_PDF"
        }

        val statusCnh = calcularStatusCnh(
            frente,
            verso,
            digital
        )

        atualizarStatusVisual(
            txtStatusCnh,
            iconeCnh,
            statusCnh
        )

        // Comprovante de residência
        val documentosComprovante = documentos.filter {
            it.getString("tipo") == "COMPROVANTE_RESIDENCIA"
        }

        val statusComprovante = calcularStatusGrupo(documentosComprovante)

        atualizarStatusVisual(
            txtStatusComprovante,
            iconeComprovante,
            statusComprovante
        )

        // FOTO / VERIFICAÇÃO FACIAL
        val documentosFoto = documentos.filter {
            it.getString("tipo") in listOf(
                "FOTO_MOTORISTA",
                "FOTO",
                "FACIAL",
                "VERIFICACAO_FACIAL"
            )
        }

        val statusFoto = calcularStatusGrupo(documentosFoto)

        atualizarStatusVisual(
            txtStatusFoto,
            iconeFoto,
            statusFoto
        )
    }

    private fun calcularStatusCnh(
        frente: DocumentSnapshot?,
        verso: DocumentSnapshot?,
        digital: DocumentSnapshot?
    ): StatusDocumento {

        // CNH digital em PDF
        if (digital != null) {
            return lerStatus(digital)
        }

        // Nenhum documento enviado
        if (frente == null && verso == null) {
            return StatusDocumento.PENDENTE
        }

        // Apenas um lado enviado
        if (frente == null || verso == null) {
            return StatusDocumento.PENDENTE
        }

        val statusFrente = lerStatus(frente)
        val statusVerso = lerStatus(verso)

        if (
            statusFrente == StatusDocumento.REJEITADO ||
            statusVerso == StatusDocumento.REJEITADO
        ) {
            return StatusDocumento.REJEITADO
        }

        if (
            statusFrente == StatusDocumento.APROVADO &&
            statusVerso == StatusDocumento.APROVADO
        ) {
            return StatusDocumento.APROVADO
        }

        if (
            statusFrente == StatusDocumento.PENDENTE ||
            statusVerso == StatusDocumento.PENDENTE
        ) {
            return StatusDocumento.PENDENTE
        }

        return StatusDocumento.EM_ANALISE
    }

    private fun calcularStatusGrupo(
        documentos: List<DocumentSnapshot>
    ): StatusDocumento {

        if (documentos.isEmpty()) {
            return StatusDocumento.PENDENTE
        }

        val status = documentos.map { lerStatus(it) }

        return when {
            status.any {
                it == StatusDocumento.REJEITADO
            } -> StatusDocumento.REJEITADO

            status.all {
                it == StatusDocumento.APROVADO
            } -> StatusDocumento.APROVADO

            status.any {
                it == StatusDocumento.EM_ANALISE
            } -> StatusDocumento.EM_ANALISE

            else -> StatusDocumento.PENDENTE
        }
    }

    private fun lerStatus(
        documento: DocumentSnapshot
    ): StatusDocumento {

        val valor = (
                documento.getString("statusValidacao")
                    ?: documento.getString("status")
                    ?: "PENDENTE"
                ).uppercase()

        return when (valor) {

            "APROVADO", "APROVADA", "ATIVO" ->
                StatusDocumento.APROVADO

            "EM_ANALISE", "ANALISE", "ENVIADO" ->
                StatusDocumento.EM_ANALISE

            "REJEITADO", "REPROVADO", "REPROVADA" ->
                StatusDocumento.REJEITADO

            else ->
                StatusDocumento.PENDENTE
        }
    }

    private fun atualizarStatusVisual(
        texto: TextView,
        icone: ImageView,
        status: StatusDocumento
    ) {

        when (status) {

            StatusDocumento.PENDENTE -> {

            }

            StatusDocumento.EM_ANALISE -> {
                texto.text = "Em análise"
                texto.visibility = View.VISIBLE
                texto.setTextColor(Color.parseColor("#E5A000"))

                // Drawable
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