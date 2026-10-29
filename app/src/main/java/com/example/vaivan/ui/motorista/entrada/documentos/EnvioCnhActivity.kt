package com.example.vaivan.ui.motorista.entrada.documentos

import android.util.Log
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.vaivan.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EnvioCnhActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var grupoTipo: RadioGroup
    private lateinit var secaoFisica: View
    private lateinit var secaoDigital: View
    private lateinit var btnContinuar: MaterialButton
    private lateinit var txtStatusGeral: TextView

    private lateinit var btnUploadPdf: View
    private lateinit var itemPdfDigital: View


    private val arquivos = mutableMapOf<String, ArquivoCnh>()
    private val uploads = mutableMapOf<String, UploadTask>()

    private var tipoSelecionado = TIPO_DIGITAL
    private var salvando = false
    private var fotoPendente: Uri? = null
    private var slotCamera: String? = null

    private data class ArquivoCnh(
        val slot: String,
        val tipoDocumento: String,
        val view: View,
        var uri: Uri? = null,
        var nome: String = "",
        var mimeType: String = "",
        var url: String? = null,
        var progresso: Int = 0,
        var enviando: Boolean = false,
        var erro: Boolean = false
    )

    private val capturaCamera =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
            val slot = slotCamera
            val uri = fotoPendente

            if (slot != null && uri != null) {
                if (sucesso) {
                    prepararArquivo(
                        slot = slot,
                        uri = uri,
                        mimeType = "image/jpeg",
                        nome = "CNH_${slot}_${System.currentTimeMillis()}.jpg"
                    )
                } else {
                    File(uri.path ?: "").delete()
                    txtStatusGeral.text = "Captura cancelada."
                }
            }

            fotoPendente = null
            slotCamera = null
        }

    private val seletorImagemFrente =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                prepararArquivo(
                    "FRENTE_FISICA",
                    it,
                    contentType(it),
                    nomeArquivo(it)
                )
            }
        }

    private val seletorImagemVerso =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                prepararArquivo(
                    "VERSO_FISICA",
                    it,
                    contentType(it),
                    nomeArquivo(it)
                )
            }
        }

    private val seletorPdfOuImagem =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                val mime = contentType(it)
                val nome = nomeArquivo(it)

                if (mime == "application/pdf" ||
                    mime.startsWith("image/")
                ) {
                    if (mime == "application/pdf") {
                        prepararArquivo(
                            "PDF_DIGITAL",
                            it,
                            mime,
                            nome
                        )
                    } else {
                        txtStatusGeral.text =
                            "Imagem selecionada. Escolha frente ou verso."
                        // O seletor geral aceita PDF e imagem.
                        // Para imagens, use os seletores de frente e verso.
                    }
                } else {
                    txtStatusGeral.text =
                        "Formato não suportado. Selecione PDF ou imagem."
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_valida_cnh)


        btnUploadPdf = findViewById(R.id.btnSelecionarPdf)
        itemPdfDigital = findViewById(R.id.itemPdfDigital)

        btnUploadPdf.setOnClickListener {
            seletorPdfOuImagem.launch(arrayOf("application/pdf"))
        }


        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        grupoTipo = findViewById(R.id.grupoTipoCnh)
        secaoFisica = findViewById(R.id.secaoCnhFisica)
        secaoDigital = findViewById(R.id.secaoCnhDigital)
        btnContinuar = findViewById(R.id.btnEnviarContinuarCnh)
        txtStatusGeral = findViewById(R.id.txtStatusGeral)

        configurarArquivo(
            "FRENTE_FISICA",
            R.id.itemFrenteFisica,
            VerificarProgressoDocumentos.TIPO_CNH_FRENTE
        )
        configurarArquivo(
            "VERSO_FISICA",
            R.id.itemVersoFisica,
            VerificarProgressoDocumentos.TIPO_CNH_VERSO
        )
        configurarArquivo(
            "PDF_DIGITAL",
            R.id.itemPdfDigital,
            TIPO_CNH_DIGITAL_PDF
        )

        findViewById<LinearLayout>(R.id.btnEnviarFrente)
            .setOnClickListener {
                mostrarOpcoesArquivo("FRENTE_FISICA")
            }

        findViewById<LinearLayout>(R.id.btnEnviarVerso)
            .setOnClickListener {
                mostrarOpcoesArquivo("VERSO_FISICA")
            }

        grupoTipo.setOnCheckedChangeListener { _, checkedId ->
            tipoSelecionado = if (checkedId == R.id.radioCnhDigital) {
                TIPO_DIGITAL
            } else {
                TIPO_FISICA
            }

            atualizarSecoes()
            atualizarContinuar()
        }

        btnContinuar.setOnClickListener {
            salvarDocumentos()
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.scrollValidacaoEmail)
        ) { view, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            view.setPadding(
                view.paddingLeft,
                barras.top,
                view.paddingRight,
                barras.bottom
            )
            insets
        }

        atualizarSecoes()
        atualizarContinuar()
    }

    private fun configurarArquivo(
        slot: String,
        idView: Int,
        tipoDocumento: String
    ) {
        val item = findViewById<View>(idView)
        arquivos[slot] = ArquivoCnh(
            slot = slot,
            tipoDocumento = tipoDocumento,
            view = item
        )
    }

    private fun atualizarSecoes() {
        secaoFisica.visibility =
            if (tipoSelecionado == TIPO_FISICA) View.VISIBLE else View.GONE

        secaoDigital.visibility =
            if (tipoSelecionado == TIPO_DIGITAL) View.VISIBLE else View.GONE
    }

    private fun abrirCamera(slot: String) {
        val arquivo = File.createTempFile(
            "cnh_${slot.lowercase(Locale.ROOT)}_",
            ".jpg",
            cacheDir
        )

        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            arquivo
        )

        fotoPendente = uri
        slotCamera = slot
        capturaCamera.launch(uri)
    }

    private fun prepararArquivo(
        slot: String,
        uri: Uri,
        mimeType: String,
        nome: String
    ) {
        if (!mimeType.startsWith("image/") &&
            mimeType != "application/pdf"
        ) {
            txtStatusGeral.text = "Selecione um PDF ou uma imagem válida."
            return
        }

        val arquivo = arquivos[slot] ?: return

        uploads[slot]?.cancel()

        arquivo.uri = uri
        arquivo.nome = nome
        arquivo.mimeType = mimeType
        arquivo.url = null
        arquivo.progresso = 0
        arquivo.erro = false
        arquivo.enviando = true

        desenharArquivo(arquivo)
        atualizarContinuar()
        enviarParaStorage(arquivo)
    }

    private fun enviarParaStorage(arquivo: ArquivoCnh) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            arquivo.enviando = false
            arquivo.erro = true
            desenharArquivo(arquivo)
            txtStatusGeral.text = "Entre novamente para enviar seus documentos."
            atualizarContinuar()
            return
        }

        val extensao = when (arquivo.mimeType) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

        val nomeStorage = when (arquivo.slot) {
            "FRENTE_FISICA", "FRENTE_DIGITAL" -> "frente"
            "VERSO_FISICA", "VERSO_DIGITAL" -> "verso"
            else -> "digital"
        } + ".$extensao"

        val referencia = referenciaStorage(arquivo, userId)

        val metadata = StorageMetadata.Builder()
            .setContentType(arquivo.mimeType)
            .build()

        val tarefa = referencia.putFile(arquivo.uri!!, metadata)
        uploads[arquivo.slot] = tarefa

        tarefa.addOnProgressListener { snapshot ->
            if (uploads[arquivo.slot] !== tarefa) return@addOnProgressListener

            arquivo.progresso =
                if (snapshot.totalByteCount > 0) {
                    ((snapshot.bytesTransferred * 100) /
                            snapshot.totalByteCount).toInt()
                } else {
                    0
                }

            desenharArquivo(arquivo)
        }.continueWithTask { resultado ->
            if (!resultado.isSuccessful) {
                throw resultado.exception
                    ?: Exception("Falha no upload")
            }
            referencia.downloadUrl
        }.addOnSuccessListener { downloadUri ->
            if (uploads[arquivo.slot] !== tarefa) return@addOnSuccessListener

            arquivo.url = downloadUri.toString()
            arquivo.progresso = 100
            arquivo.enviando = false
            arquivo.erro = false
            uploads.remove(arquivo.slot)

            desenharArquivo(arquivo)
            txtStatusGeral.text = "Arquivo enviado com sucesso."
            atualizarContinuar()
        }.addOnFailureListener { erro ->
                if (uploads[arquivo.slot] !== tarefa) {
                    return@addOnFailureListener
                }

                Log.e(
                    "EnvioCNH",
                    "Falha no upload. Slot=${arquivo.slot}, " +
                            "tipo=${arquivo.mimeType}, nome=${arquivo.nome}",
                    erro
                )

                arquivo.url = null
                arquivo.enviando = false
                arquivo.erro = true
                uploads.remove(arquivo.slot)

                desenharArquivo(arquivo)

                txtStatusGeral.text = when {
                    erro is com.google.firebase.storage.StorageException -> {
                        "Erro no Firebase Storage: código ${erro.errorCode}"
                    }
                    else -> {
                        "Falha: ${erro.localizedMessage ?: "erro desconhecido"}"
                    }
                }

                atualizarContinuar()
            }

    }


    private fun desenharArquivo(arquivo: ArquivoCnh) {
        val view = arquivo.view

        when (arquivo.slot) {
            "PDF_DIGITAL" -> {
                btnUploadPdf.visibility =
                    if (arquivo.uri != null) View.GONE else View.VISIBLE

                itemPdfDigital.visibility =
                    if (arquivo.uri != null) View.VISIBLE else View.GONE
            }

            "FRENTE_FISICA" -> {
                findViewById<View>(R.id.btnEnviarFrente).visibility =
                    if (arquivo.uri != null) View.GONE else View.VISIBLE

                arquivo.view.visibility =
                    if (arquivo.uri != null) View.VISIBLE else View.GONE
            }

            "VERSO_FISICA" -> {
                findViewById<View>(R.id.btnEnviarVerso).visibility =
                    if (arquivo.uri != null) View.GONE else View.VISIBLE

                arquivo.view.visibility =
                    if (arquivo.uri != null) View.VISIBLE else View.GONE
            }
        }

        val icone = view.findViewById<TextView>(R.id.txtIconeArquivo)
        val nome = view.findViewById<TextView>(R.id.txtNomeArquivo)
        val estado = view.findViewById<TextView>(R.id.txtEstadoArquivo)
        val barra = view.findViewById<ProgressBar>(R.id.progressoArquivo)
        val porcentagem =
            view.findViewById<TextView>(R.id.txtProgressoArquivo)
        val btnExcluir =
            view.findViewById<ImageButton>(R.id.btnExcluirArquivo)

        icone.text = when (arquivo.mimeType) {
            "application/pdf" -> "PDF"
            else -> "IMG"
        }

        nome.text = arquivo.nome.ifBlank {
            "Nenhum arquivo selecionado"
        }

        estado.text = when {
            arquivo.enviando -> "Enviando arquivo..."
            arquivo.url != null -> "Enviado com sucesso"
            arquivo.erro -> "Falha no envio"
            arquivo.uri != null -> "Arquivo selecionado"
            else -> "Aguardando arquivo"
        }

        barra.isIndeterminate = false
        barra.progress = arquivo.progresso

        porcentagem.text = "${arquivo.progresso}%"
        porcentagem.visibility =
            if (arquivo.enviando || arquivo.url != null) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnExcluir.visibility =
            if (arquivo.uri != null) View.VISIBLE else View.GONE

        btnExcluir.setOnClickListener {
            confirmarExclusaoArquivo(arquivo)
        }
    }


    private fun atualizarContinuar() {
        val slotsNecessarios = if (tipoSelecionado == TIPO_FISICA) {
            listOf("FRENTE_FISICA", "VERSO_FISICA")
        } else {
            listOf("PDF_DIGITAL")
        }

        val prontos = slotsNecessarios.all {
            arquivos[it]?.url != null
        }

        val enviando = slotsNecessarios.any {
            arquivos[it]?.enviando == true
        }

        btnContinuar.isEnabled = prontos && !enviando && !salvando

        btnContinuar.text = when {
            salvando -> "Salvando documentos..."
            enviando -> "Aguarde o envio..."
            prontos -> "Continuar"
            else -> "Envie os documentos"
        }
    }

    private fun salvarDocumentos() {
        if (salvando) return

        val userId = auth.currentUser?.uid ?: run {
            txtStatusGeral.text = "Entre novamente para continuar."
            return
        }

        val slots = if (tipoSelecionado == TIPO_FISICA) {
            listOf("FRENTE_FISICA", "VERSO_FISICA")
        } else {
            listOf("PDF_DIGITAL")
        }

        val selecionados = slots.mapNotNull { arquivos[it] }

        if (selecionados.size != slots.size ||
            selecionados.any { it.url == null || it.enviando }
        ) {
            txtStatusGeral.text = "Aguarde o envio de todos os arquivos."
            return
        }

        salvando = true
        atualizarContinuar()

        val batch = db.batch()
        val dataEnvio = SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            Locale("pt", "BR")
        ).format(Date())

        selecionados.forEach { arquivo ->
            val referencia = db.collection("documentos")
                .document(
                    if (arquivo.slot == "PDF_DIGITAL") {
                        "${userId}_CNH_DIGITAL_PDF"
                    } else {
                        VerificarProgressoDocumentos.idDoDocumento(
                            userId,
                            arquivo.tipoDocumento
                        )
                    }
                )

            batch.set(
                referencia,
                mapOf(
                    "tipo" to arquivo.tipoDocumento,
                    "arquivo" to arquivo.url,
                    "nomeArquivo" to arquivo.nome,
                    "contentType" to arquivo.mimeType,
                    "dataEnvio" to dataEnvio,
                    "statusValidacao" to VerificarProgressoDocumentos.ANALISE,
                    "motoristaId" to userId,
                    "veiculoId" to "",
                    "modalidadeCnh" to tipoSelecionado
                )
            )
        }

        batch.commit()
            .addOnSuccessListener {
                VerificarProgressoDocumentos(this).verificar()
                finish()
            }
            .addOnFailureListener {
                salvando = false
                txtStatusGeral.text =
                    "Não foi possível registrar os documentos. Tente novamente."
                atualizarContinuar()
            }
    }

    private fun configurarBotaoExcluir(arquivo: ArquivoCnh) {
        val btnExcluir = arquivo.view.findViewById<ImageButton>(
            R.id.btnExcluirArquivo
        )

        btnExcluir.visibility = View.VISIBLE

        btnExcluir.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Excluir arquivo?")
                .setMessage("Deseja remover este arquivo da CNH?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir") { _, _ ->
                    excluirArquivo(arquivo)
                }
                .show()
        }
    }

    private fun confirmarExclusaoArquivo(arquivo: ArquivoCnh) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir arquivo?")
            .setMessage(
                "Deseja remover ${arquivo.nome} e cancelar seu envio, se estiver em andamento?"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                excluirArquivo(arquivo)
            }
            .show()
    }

    private fun excluirArquivo(arquivo: ArquivoCnh) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            txtStatusGeral.text = "Entre novamente para excluir o arquivo."
            return
        }

        // Monta o caminho antes de limpar os dados.
        val referencia = referenciaStorage(arquivo, userId)
        val urlAnterior = arquivo.url
        val tarefa = uploads.remove(arquivo.slot)

        // Cancela eventual upload em andamento.
        tarefa?.cancel()

        arquivo.uri = null
        arquivo.nome = ""
        arquivo.mimeType = ""
        arquivo.url = null
        arquivo.progresso = 0
        arquivo.enviando = false
        arquivo.erro = false

        desenharArquivo(arquivo)
        atualizarContinuar()

        referencia.delete()
            .addOnSuccessListener {
                if (urlAnterior != null) {
                    excluirRegistroFirestore(arquivo, userId) {
                        txtStatusGeral.text = "Arquivo excluído com sucesso."
                    }
                } else {
                    txtStatusGeral.text = "Arquivo removido."
                }
            }
            .addOnFailureListener { erro ->
                Log.e(
                    "EnvioCNH",
                    "Erro ao excluir arquivo no Storage: ${erro.message}",
                    erro
                )

                txtStatusGeral.text = when ((erro as? StorageException)?.errorCode) {
                    StorageException.ERROR_OBJECT_NOT_FOUND ->
                        "O arquivo não foi encontrado no Storage. Verifique o caminho."

                    StorageException.ERROR_NOT_AUTHENTICATED ->
                        "Sua sessão expirou. Entre novamente."

                    StorageException.ERROR_NOT_AUTHORIZED ->
                        "O Firebase não autorizou a exclusão. Verifique as regras do Storage."

                    else ->
                        "Não foi possível excluir o arquivo. Consulte o Logcat para ver o erro."
                }
            }
    }

    private fun referenciaStorage(
        arquivo: ArquivoCnh,
        userId: String
    ) = storage.reference.child(
        "documentos/CNH/$userId/${arquivo.slot}/" +
                nomeStorage(arquivo)
    )

    private fun nomeStorage(arquivo: ArquivoCnh): String {
        val extensao = when (arquivo.mimeType) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

        val nomeBase = when (arquivo.slot) {
            "FRENTE_FISICA", "FRENTE_DIGITAL" -> "frente"
            "VERSO_FISICA", "VERSO_DIGITAL" -> "verso"
            else -> "digital"
        }

        return "$nomeBase.$extensao"
    }

    private fun excluirRegistroFirestore(
        arquivo: ArquivoCnh,
        userId: String,
        aoConcluir: () -> Unit
    ) {
        val idDocumento = if (arquivo.slot == "PDF_DIGITAL") {
            "${userId}_CNH_DIGITAL_PDF"
        } else {
            VerificarProgressoDocumentos.idDoDocumento(
                userId,
                arquivo.tipoDocumento
            )
        }

        db.collection("documentos")
            .document(idDocumento)
            .delete()
            .addOnSuccessListener {
                aoConcluir()
            }
            .addOnFailureListener { erro ->
                Log.e(
                    "EnvioCNH",
                    "Arquivo removido do Storage, mas falhou ao excluir o registro.",
                    erro
                )

                txtStatusGeral.text =
                    "Arquivo removido, mas não foi possível atualizar o registro no Firestore."
            }
    }

    private fun mostrarOpcoesArquivo(slot: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enviar arquivo")
            .setItems(
                arrayOf(
                    "Tirar foto com a câmera",
                    "Selecionar da galeria"
                )
            ) { _, opcao ->
                when (opcao) {
                    0 -> abrirCamera(slot)

                    1 -> {
                        when (slot) {
                            "FRENTE_FISICA" ->
                                seletorImagemFrente.launch("image/*")

                            "VERSO_FISICA" ->
                                seletorImagemVerso.launch("image/*")
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun nomeArquivo(uri: Uri): String {
        var nome = "arquivo"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val coluna = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (coluna >= 0 && cursor.moveToFirst()) {
                nome = cursor.getString(coluna) ?: nome
            }
        }

        return nome
    }

    private fun contentType(uri: Uri): String =
        contentResolver.getType(uri) ?: "application/octet-stream"

    override fun onDestroy() {
        super.onDestroy()
        // Não cancela uploads automaticamente: o Storage pode terminar
        // mesmo que a tela seja recriada.
    }

    companion object {
        private const val TIPO_FISICA = "FISICA"
        private const val TIPO_DIGITAL = "DIGITAL"
        private const val TIPO_CNH_DIGITAL_PDF = "CNH_DIGITAL_PDF"
    }
}
