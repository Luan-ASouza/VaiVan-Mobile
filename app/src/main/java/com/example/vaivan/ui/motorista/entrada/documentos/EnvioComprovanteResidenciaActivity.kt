package com.example.vaivan.ui.motorista.entrada.documentos

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.DocumentoRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EnvioComprovanteResidenciaActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var documentoRepository: DocumentoRepository
    private lateinit var btnSelecionar: View
    private lateinit var itemArquivo: View
    private lateinit var btnContinuar: MaterialButton
    private lateinit var txtStatus: TextView

    private var uriArquivo: Uri? = null
    private var nomeArquivo = ""
    private var mimeType = ""
    private var urlArquivo: String? = null
    private var progresso = 0
    private var enviando = false
    private var erroUpload = false
    private var salvando = false
    private var uploadAtual: UploadTask? = null
    private var fotoPendente: Uri? = null
    private var arquivoCameraPendente: File? = null

    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
        val uri = fotoPendente
        if (sucesso && uri != null) {
            prepararArquivo(uri, "image/jpeg", "comprovante_${System.currentTimeMillis()}.jpg")
        } else {
            arquivoCameraPendente?.delete()
            txtStatus.text = "Captura cancelada."
        }
        fotoPendente = null
        arquivoCameraPendente = null
    }

    private val galeria = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { prepararArquivo(it, contentType(it), nomeDoArquivo(it)) }
    }

    private val seletorPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { prepararArquivo(it, contentType(it), nomeDoArquivo(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_envio_comprovante_residencia)

        auth = FirebaseAuth.getInstance()
        documentoRepository = DocumentoRepository(VaivanDatabase.getInstance(this).documentoDao())

        btnSelecionar = findViewById(R.id.btnSelecionarComprovante)
        itemArquivo = findViewById(R.id.itemComprovante)
        btnContinuar = findViewById(R.id.btnEnviarContinuarComprovante)
        txtStatus = findViewById(R.id.txtStatusGeral)

        btnSelecionar.setOnClickListener { mostrarOpcoesArquivo() }
        btnContinuar.setOnClickListener { salvarDadosNoFirestore() }

        val scroll = findViewById<View>(R.id.scrollComprovanteResidencia)
        val paddingTopOriginal = scroll.paddingTop
        val paddingBottomOriginal = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, paddingTopOriginal + barras.top,
                view.paddingRight, paddingBottomOriginal + barras.bottom)
            insets
        }

        desenharArquivo()
        atualizarContinuar()
    }

    private fun mostrarOpcoesArquivo() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enviar comprovante")
            .setItems(arrayOf("Tirar foto com a câmera", "Selecionar da galeria", "Selecionar PDF")) { _, opcao ->
                when (opcao) {
                    0 -> abrirCamera()
                    1 -> galeria.launch("image/*")
                    2 -> seletorPdf.launch(arrayOf("application/pdf"))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirCamera() {
        try {
            val arquivo = File.createTempFile("comprovante_", ".jpg", cacheDir)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", arquivo)
            arquivoCameraPendente = arquivo
            fotoPendente = uri
            camera.launch(uri)
        } catch (e: Exception) {
            Log.e("EnvioComprovante", "Erro ao abrir câmera", e)
            txtStatus.text = "Não foi possível abrir a câmera."
        }
    }

    private fun prepararArquivo(uri: Uri, mime: String, nome: String) {
        if (!mime.startsWith("image/") && mime != "application/pdf") {
            txtStatus.text = "Selecione uma imagem ou um PDF válido."
            return
        }
        if (salvando) return

        // Cancela o upload anterior. O caminho do novo arquivo é único para evitar
        // que um upload cancelado apague ou sobrescreva o novo documento.
        uploadAtual?.cancel()
        uploadAtual = null
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
        val userId = auth.currentUser?.uid
        if (userId == null) {
            enviando = false
            erroUpload = true
            txtStatus.text = "Entre novamente para enviar seu documento."
            desenharArquivo()
            atualizarContinuar()
            return
        }

        val upload = documentoRepository.iniciarUploadComprovante(userId, uri, mimeType)
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
            progresso = 100
            enviando = false
            erroUpload = false
            uploadAtual = null
            txtStatus.text = "Arquivo enviado com sucesso."
            desenharArquivo()
            atualizarContinuar()
        }.addOnFailureListener { erro ->
            if (uploadAtual !== tarefa) return@addOnFailureListener
            Log.e("EnvioComprovante", "Falha ao enviar arquivo", erro)
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
            else -> "Envie o comprovante"
        }
    }

    private fun confirmarExclusao() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir arquivo?")
            .setMessage("Deseja remover $nomeArquivo e cancelar o envio, se estiver em andamento?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ -> excluirArquivo() }
            .show()
    }

    private fun excluirArquivo() {
        if (salvando) return
        val urlAnterior = urlArquivo
        val mimeTypeAnterior = mimeType
        val tarefaAnterior = uploadAtual
        uploadAtual = null
        tarefaAnterior?.cancel()

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

        // Exclui somente o objeto enviado nesta seleção. Não remove o registro
        // do Firestore porque ele só é confirmado ao tocar em Continuar.
        val userId = auth.currentUser?.uid
        if (urlAnterior != null && userId != null) {
            lifecycleScope.launch {
                try {
                    documentoRepository.excluirArquivoComprovante(userId, mimeTypeAnterior)
                } catch (erro: Exception) {
                    Log.e("EnvioComprovante", "Falha ao excluir do Storage", erro)
                    txtStatus.text = "Arquivo removido da tela, mas falhou a exclusão no Storage."
                }
            }
        }
    }
    private fun salvarDadosNoFirestore() {
        if (salvando || enviando) return

        val userId = auth.currentUser?.uid ?: run {
            txtStatus.text = "Entre novamente para continuar."
            return
        }

        val url = urlArquivo ?: run {
            txtStatus.text = "Aguarde o envio do comprovante."
            return
        }

        salvando = true
        desenharArquivo()
        atualizarContinuar()

        val dataEnvio = SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            Locale("pt", "BR")
        ).format(Date())

        lifecycleScope.launch {
            try {
                documentoRepository.salvarComprovante(userId, url, dataEnvio)

                txtStatus.text = "Comprovante registrado com sucesso."

                VerificarProgressoDocumentos(this@EnvioComprovanteResidenciaActivity).verificar()

                finish()
            } catch (erro: Exception) {

                Log.e(
                    "EnvioComprovante",
                    "Falha ao salvar comprovante no Firestore",
                    erro
                )

                salvando = false

                txtStatus.text =
                    "Não foi possível registrar o comprovante: ${erro.localizedMessage}"

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
