package com.example.vaivan.ui.motorista.veiculos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.core.util.PermissaoCameraHelper
import com.example.vaivan.ui.comum.tema.OrigemArquivo
import com.example.vaivan.ui.comum.tema.escolherOrigemArquivo
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.ui.comum.recorte.RecortarImagem
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Envio de um documento do veículo (CRLV ou autorização de transporte escolar),
 * no mesmo fluxo do envio do comprovante de residência: escolher (câmera,
 * galeria ou PDF) → upload com barra de progresso → Continuar grava o
 * documento "em análise". Reenviar substitui o documento anterior.
 *
 * Extras: [EXTRA_VEICULO_ID] e [EXTRA_TIPO] (DocumentoRepository.TIPO_CRLV / TIPO_AUTORIZACAO).
 */
class EnvioDocumentoVeiculoActivity : BaseActivity() {

    companion object {
        const val EXTRA_VEICULO_ID = "veiculoId"
        const val EXTRA_TIPO = "tipo"

        private const val TAG = "EnvioDocumentoVeiculo"

        fun intent(context: Context, veiculoId: String, tipo: String): Intent =
            Intent(context, EnvioDocumentoVeiculoActivity::class.java)
                .putExtra(EXTRA_VEICULO_ID, veiculoId)
                .putExtra(EXTRA_TIPO, tipo)
    }

    /** Textos de cada tipo de documento. */
    private data class Textos(
        val titulo: String,
        val nomeCurto: String,
        val descricao: String,
        val requisitos: String
    )

    private val textosPorTipo = mapOf(
        DocumentoRepository.TIPO_CRLV to Textos(
            titulo = "CRLV",
            nomeCurto = "CRLV",
            descricao = "Envie o Certificado de Registro e Licenciamento do Veículo (CRLV).",
            requisitos = "O documento deve ser do veículo cadastrado (mesma placa) e estar com o licenciamento em dia."
        ),
        DocumentoRepository.TIPO_AUTORIZACAO to Textos(
            titulo = "Autorização de transporte escolar",
            nomeCurto = "autorização",
            descricao = "Envie o documento que libera o veículo para o transporte escolar.",
            requisitos = "A autorização deve estar dentro da validade e corresponder ao veículo cadastrado."
        )
    )

    private lateinit var auth: FirebaseAuth
    private lateinit var documentoRepository: DocumentoRepository
    private lateinit var btnSelecionar: View
    private lateinit var itemArquivo: View
    private lateinit var btnContinuar: MaterialButton
    private lateinit var txtStatus: TextView

    private lateinit var veiculoId: String
    private lateinit var tipo: String
    private lateinit var textos: Textos

    private var uriArquivo: Uri? = null
    private var nomeArquivo = ""
    private var mimeType = ""
    private var urlArquivo: String? = null
    private var referenciaEnviada: StorageReference? = null
    private var progresso = 0
    private var enviando = false
    private var erroUpload = false
    private var salvando = false
    private var uploadAtual: UploadTask? = null
    private var fotoPendente: Uri? = null
    private var arquivoCameraPendente: File? = null

    private val permissaoCamera = PermissaoCameraHelper(this) {
        txtStatus.text = "Permita o acesso à câmera para tirar a foto (ou escolha da galeria)."
    }

    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
        val uri = fotoPendente
        if (sucesso && uri != null) {
            recorte.launch(RecortarImagem.Pedido.documento(uri))
        } else {
            arquivoCameraPendente?.delete()
            txtStatus.text = "Captura cancelada."
        }
        fotoPendente = null
        arquivoCameraPendente = null
    }

    // Foto (câmera ou galeria) passa pelo recorte antes do upload; o
    // resultado já vem comprimido em JPEG (ImagemUtil.DOCUMENTO).
    private val recorte = registerForActivityResult(RecortarImagem()) { uri ->
        if (uri != null) {
            prepararArquivo(uri, "image/jpeg", "${tipo.lowercase()}_${System.currentTimeMillis()}.jpg")
        } else {
            txtStatus.text = "Envio cancelado."
        }
    }

    private val galeria = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { recorte.launch(RecortarImagem.Pedido.documento(it)) }
    }

    private val seletorPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { prepararArquivo(it, contentType(it), nomeDoArquivo(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getStringExtra(EXTRA_VEICULO_ID)
        val tipoRecebido = intent.getStringExtra(EXTRA_TIPO)
        val textosDoTipo = tipoRecebido?.let { textosPorTipo[it] }
        if (id.isNullOrBlank() || tipoRecebido == null || textosDoTipo == null) {
            finish()
            return
        }
        veiculoId = id
        tipo = tipoRecebido
        textos = textosDoTipo

        setContentView(R.layout.activity_envio_documento_veiculo)

        auth = FirebaseAuth.getInstance()
        documentoRepository = DocumentoRepository(VaivanDatabase.getInstance(this).documentoDao())

        btnSelecionar = findViewById(R.id.btnSelecionarDocumentoVeiculo)
        itemArquivo = findViewById(R.id.itemArquivoVeiculo)
        btnContinuar = findViewById(R.id.btnContinuarDocumentoVeiculo)
        txtStatus = findViewById(R.id.txtStatusGeralVeiculo)

        findViewById<TextView>(R.id.txtTituloEnvioVeiculo).text = textos.titulo
        findViewById<TextView>(R.id.txtDescricaoEnvioVeiculo).text = textos.descricao
        findViewById<TextView>(R.id.txtRequisitosEnvioVeiculo).text = textos.requisitos
        findViewById<TextView>(R.id.txtRotuloUploadVeiculo).text = textos.titulo
        findViewById<TextView>(R.id.txtAcaoSelecionarVeiculo).text = "Enviar ${textos.nomeCurto}"

        findViewById<View>(R.id.btnVoltarEnvioDocumento).setOnClickListener { finish() }
        btnSelecionar.setOnClickListener { mostrarOpcoesArquivo() }
        btnContinuar.setOnClickListener { salvarDocumento() }

        // Igual ao envio do comprovante: a tela inteira rola, com folga das barras do sistema
        val scroll = findViewById<View>(R.id.scrollEnvioDocumentoVeiculo)
        val paddingTopOriginal = scroll.paddingTop
        val paddingBottomOriginal = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft, paddingTopOriginal + barras.top,
                view.paddingRight, paddingBottomOriginal + barras.bottom
            )
            insets
        }

        desenharArquivo()
        atualizarContinuar()
    }

    private fun mostrarOpcoesArquivo() {
        escolherOrigemArquivo(this, "Enviar ${textos.nomeCurto}") { origem ->
            when (origem) {
                OrigemArquivo.CAMERA -> permissaoCamera.comPermissao { abrirCamera() }
                OrigemArquivo.GALERIA -> galeria.launch("image/*")
                OrigemArquivo.PDF -> seletorPdf.launch(arrayOf("application/pdf"))
            }
        }
    }

    private fun abrirCamera() {
        try {
            val arquivo = File.createTempFile("${tipo.lowercase()}_", ".jpg", cacheDir)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", arquivo)
            arquivoCameraPendente = arquivo
            fotoPendente = uri
            camera.launch(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir câmera", e)
            txtStatus.text = "Não foi possível abrir a câmera."
        }
    }

    private fun prepararArquivo(uri: Uri, mime: String, nome: String) {
        if (!mime.startsWith("image/") && mime != "application/pdf") {
            txtStatus.text = "Selecione uma imagem ou um PDF válido."
            return
        }
        if (salvando) return

        // Cancela o upload anterior e descarta o arquivo já enviado nesta tela.
        // Cada envio usa um caminho próprio, então um cancelado nunca apaga o novo.
        uploadAtual?.cancel()
        uploadAtual = null
        descartarEnvioAnterior()

        uriArquivo = uri
        nomeArquivo = nome
        mimeType = mime
        urlArquivo = null
        progresso = 0
        enviando = true
        erroUpload = false
        desenharArquivo()
        atualizarContinuar()
        enviarParaStorage(uri)
    }

    private fun enviarParaStorage(uri: Uri) {
        if (auth.currentUser?.uid == null) {
            enviando = false
            erroUpload = true
            txtStatus.text = "Entre novamente para enviar seu documento."
            desenharArquivo()
            atualizarContinuar()
            return
        }

        val upload = documentoRepository.iniciarUploadDocumentoVeiculo(veiculoId, tipo, uri, mimeType)
        val referencia = upload.referencia
        val tarefa = upload.tarefa
        uploadAtual = tarefa

        tarefa.addOnProgressListener { snapshot ->
            if (uploadAtual !== tarefa) return@addOnProgressListener
            progresso = if (snapshot.totalByteCount > 0) {
                ((snapshot.bytesTransferred * 100) / snapshot.totalByteCount).toInt()
            } else 0
            desenharArquivo()
        }.continueWithTask { resultado ->
            if (!resultado.isSuccessful) {
                throw resultado.exception ?: Exception("Falha no upload")
            }
            referencia.downloadUrl
        }.addOnSuccessListener { url ->
            if (uploadAtual !== tarefa) return@addOnSuccessListener
            urlArquivo = url.toString()
            referenciaEnviada = referencia
            progresso = 100
            enviando = false
            erroUpload = false
            uploadAtual = null
            txtStatus.text = "Arquivo enviado com sucesso."
            desenharArquivo()
            atualizarContinuar()
        }.addOnFailureListener { erro ->
            if (uploadAtual !== tarefa) return@addOnFailureListener
            Log.e(TAG, "Falha ao enviar arquivo", erro)
            enviando = false
            erroUpload = true
            uploadAtual = null
            txtStatus.text = if (erro is StorageException) {
                "Erro no Firebase Storage: código ${erro.errorCode}"
            } else "Falha no envio: ${erro.localizedMessage ?: "erro desconhecido"}"
            desenharArquivo()
            atualizarContinuar()
        }
    }

    private fun desenharArquivo() {
        val selecionado = uriArquivo != null
        btnSelecionar.visibility = if (selecionado) View.GONE else View.VISIBLE
        itemArquivo.visibility = if (selecionado) View.VISIBLE else View.GONE
        if (!selecionado) return

        itemArquivo.findViewById<TextView>(R.id.txtIconeArquivo).text =
            if (mimeType == "application/pdf") "PDF" else "IMG"
        itemArquivo.findViewById<TextView>(R.id.txtNomeArquivo).text = nomeArquivo
        itemArquivo.findViewById<TextView>(R.id.txtEstadoArquivo).text = when {
            enviando -> "Enviando arquivo..."
            urlArquivo != null -> "Enviado com sucesso"
            erroUpload -> "Falha no envio"
            else -> "Arquivo selecionado"
        }
        itemArquivo.findViewById<ProgressBar>(R.id.progressoArquivo).apply {
            isIndeterminate = false
            progress = progresso
            visibility = if (enviando || urlArquivo != null) View.VISIBLE else View.GONE
        }
        itemArquivo.findViewById<TextView>(R.id.txtProgressoArquivo).apply {
            text = "$progresso%"
            visibility = if (enviando || urlArquivo != null) View.VISIBLE else View.GONE
        }
        itemArquivo.findViewById<ImageButton>(R.id.btnExcluirArquivo).apply {
            visibility = if (salvando) View.GONE else View.VISIBLE
            setOnClickListener { confirmarExclusao() }
        }
    }

    private fun atualizarContinuar() {
        btnContinuar.isEnabled = urlArquivo != null && !enviando && !salvando
        btnContinuar.text = when {
            salvando -> "Salvando documento..."
            enviando -> "Aguarde o envio..."
            urlArquivo != null -> "Continuar"
            erroUpload -> "Falha no envio"
            else -> "Envie o documento"
        }
    }

    private fun confirmarExclusao() {
        ConfirmacaoExclusaoHelper.confirmar(
            contexto = this,
            titulo = "Excluir arquivo?",
            mensagem = "Deseja remover $nomeArquivo e cancelar o envio, se estiver em andamento?"
        ) { excluirArquivo() }
    }

    private fun excluirArquivo() {
        if (salvando) return
        val tarefaAnterior = uploadAtual
        uploadAtual = null
        tarefaAnterior?.cancel()
        descartarEnvioAnterior()

        uriArquivo = null
        nomeArquivo = ""
        mimeType = ""
        urlArquivo = null
        progresso = 0
        enviando = false
        erroUpload = false
        desenharArquivo()
        atualizarContinuar()
        txtStatus.text = "Arquivo removido."
    }

    /** Apaga do Storage o arquivo enviado nesta seleção (ainda não registrado:
     * o documento só é gravado no Firestore ao tocar em Continuar). */
    private fun descartarEnvioAnterior() {
        val referencia = referenciaEnviada ?: return
        referenciaEnviada = null
        lifecycleScope.launch {
            try {
                documentoRepository.excluirUpload(referencia)
            } catch (erro: Exception) {
                Log.e(TAG, "Falha ao excluir do Storage", erro)
            }
        }
    }

    private fun salvarDocumento() {
        if (salvando || enviando) return

        val userId = auth.currentUser?.uid ?: run {
            txtStatus.text = "Entre novamente para continuar."
            return
        }

        val url = urlArquivo ?: run {
            txtStatus.text = "Aguarde o envio do documento."
            return
        }

        salvando = true
        desenharArquivo()
        atualizarContinuar()

        val dataEnvio = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())

        lifecycleScope.launch {
            try {
                documentoRepository.salvarDocumentoVeiculo(veiculoId, userId, tipo, url, dataEnvio)
                // Registrado: o arquivo agora pertence ao documento, não é mais descartável
                referenciaEnviada = null
                // CRLV + autorização enviados -> veículo "em análise" (aprovar é do painel)
                runCatching {
                    VeiculoRepository(VaivanDatabase.getInstance(this@EnvioDocumentoVeiculoActivity).veiculoDao())
                        .atualizarStatusPelosDocumentos(veiculoId, userId)
                }.onFailure { Log.w(TAG, "Não foi possível atualizar o status do veículo", it) }
                txtStatus.text = "Documento registrado com sucesso."
                finish()
            } catch (erro: Exception) {
                Log.e(TAG, "Falha ao salvar documento no Firestore", erro)
                salvando = false
                txtStatus.text = "Não foi possível registrar o documento: ${erro.localizedMessage}"
                desenharArquivo()
                atualizarContinuar()
            }
        }
    }

    private fun nomeDoArquivo(uri: Uri): String {
        var nome = "arquivo"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (indice >= 0 && cursor.moveToFirst()) nome = cursor.getString(indice) ?: nome
        }
        return nome
    }

    private fun contentType(uri: Uri): String =
        contentResolver.getType(uri) ?: "application/octet-stream"
}
