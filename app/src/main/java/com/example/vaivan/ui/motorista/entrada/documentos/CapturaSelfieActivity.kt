package com.example.vaivan.ui.motorista.entrada.documentos

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.DocumentoRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CapturaSelfieActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var documentoRepository: DocumentoRepository

    private lateinit var previewSelfie: PreviewView
    private lateinit var imagemCapturada: ImageView
    private lateinit var overlaySelfie: View
    private lateinit var btnVoltarSelfie: ImageButton
    private lateinit var btnCapturarSelfie: ImageButton
    private lateinit var grupoConfirmacaoSelfie: View
    private lateinit var btnTirarNovamente: View
    private lateinit var btnUsarFoto: View
    private lateinit var progressSelfie: ProgressBar
    private lateinit var txtInstrucaoSelfie: TextView
    private lateinit var txtStatusSelfie: TextView

    private var imageCapture: ImageCapture? = null
    private var arquivoCapturado: File? = null
    private var enviando = false

    private val solicitarPermissaoCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
            if (concedida) {
                iniciarCamera()
            } else {
                mostrarStatus("Permissão de câmera negada. Não é possível continuar.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_captura_selfie)

        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        documentoRepository = DocumentoRepository(VaivanDatabase.getInstance(this).documentoDao())

        inicializarViews()
        configurarCliques()

        if (temPermissaoCamera()) {
            iniciarCamera()
        } else {
            solicitarPermissaoCamera.launch(Manifest.permission.CAMERA)
        }

        applyTopAndBottomGaps(findViewById(android.R.id.content))
    }

    private fun inicializarViews() {
        previewSelfie = findViewById(R.id.previewSelfie)
        imagemCapturada = findViewById(R.id.imagemCapturada)
        overlaySelfie = findViewById(R.id.overlaySelfie)
        btnVoltarSelfie = findViewById(R.id.btnVoltarSelfie)
        btnCapturarSelfie = findViewById(R.id.btnCapturarSelfie)
        grupoConfirmacaoSelfie = findViewById(R.id.grupoConfirmacaoSelfie)
        btnTirarNovamente = findViewById(R.id.btnTirarNovamente)
        btnUsarFoto = findViewById(R.id.btnUsarFoto)
        progressSelfie = findViewById(R.id.progressSelfie)
        txtInstrucaoSelfie = findViewById(R.id.txtInstrucaoSelfie)
        txtStatusSelfie = findViewById(R.id.txtStatusSelfie)
    }

    private fun configurarCliques() {
        btnVoltarSelfie.setOnClickListener { finish() }
        btnCapturarSelfie.setOnClickListener { capturarFoto() }
        btnTirarNovamente.setOnClickListener { voltarParaCamera() }
        btnUsarFoto.setOnClickListener { confirmarEEnviar() }
    }

    private fun temPermissaoCamera(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun iniciarCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewSelfie.surfaceProvider)
            }

            val captura = ImageCapture.Builder().build()
            imageCapture = captura

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    captura
                )
            } catch (erro: Exception) {
                mostrarStatus("Não foi possível abrir a câmera frontal.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturarFoto() {
        val captura = imageCapture ?: return

        val arquivo = File.createTempFile("selfie_", ".jpg", cacheDir)
        val opcoes = ImageCapture.OutputFileOptions.Builder(arquivo).build()

        btnCapturarSelfie.isEnabled = false

        captura.takePicture(
            opcoes,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    btnCapturarSelfie.isEnabled = true
                    arquivoCapturado = arquivo
                    exibirConfirmacao(arquivo)
                }

                override fun onError(exception: ImageCaptureException) {
                    btnCapturarSelfie.isEnabled = true
                    mostrarStatus("Falha ao capturar a foto. Tente novamente.")
                }
            }
        )
    }

    private fun exibirConfirmacao(arquivo: File) {
        imagemCapturada.setImageBitmap(carregarBitmapCorrigido(arquivo))

        imagemCapturada.visibility = View.VISIBLE
        previewSelfie.visibility = View.GONE
        overlaySelfie.visibility = View.GONE
        txtInstrucaoSelfie.visibility = View.GONE
        btnCapturarSelfie.visibility = View.GONE
        btnVoltarSelfie.visibility = View.GONE
        grupoConfirmacaoSelfie.visibility = View.VISIBLE
        txtStatusSelfie.visibility = View.GONE
    }

    private fun voltarParaCamera() {
        arquivoCapturado?.delete()
        arquivoCapturado = null

        imagemCapturada.setImageBitmap(null)
        imagemCapturada.visibility = View.GONE
        previewSelfie.visibility = View.VISIBLE
        overlaySelfie.visibility = View.VISIBLE
        txtInstrucaoSelfie.visibility = View.VISIBLE
        btnCapturarSelfie.visibility = View.VISIBLE
        btnVoltarSelfie.visibility = View.VISIBLE
        grupoConfirmacaoSelfie.visibility = View.GONE
    }

    private fun confirmarEEnviar() {
        if (enviando) return

        val arquivo = arquivoCapturado ?: return
        val userId = auth.currentUser?.uid

        if (userId == null) {
            mostrarStatus("Entre novamente para continuar.")
            return
        }

        enviando = true
        progressSelfie.visibility = View.VISIBLE
        btnTirarNovamente.isEnabled = false
        btnUsarFoto.isEnabled = false
        txtStatusSelfie.visibility = View.GONE

        val uri = Uri.fromFile(arquivo)
        val upload = documentoRepository.iniciarUploadSelfie(userId, uri, "image/jpeg")

        upload.tarefa.continueWithTask { resultado ->
            if (!resultado.isSuccessful) {
                throw resultado.exception ?: Exception("Falha no upload")
            }
            upload.referencia.downloadUrl
        }.addOnSuccessListener { url ->
            lifecycleScope.launch {
                try {
                    val dataEnvio = SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        Locale("pt", "BR")
                    ).format(Date())

                    documentoRepository.salvarSelfie(userId, url.toString(), dataEnvio)

                    arquivoCapturado?.delete()
                    arquivoCapturado = null

                    VerificarProgressoDocumentos(this@CapturaSelfieActivity).verificar()
                    finish()
                } catch (erro: Exception) {
                    falhaNoEnvio()
                }
            }
        }.addOnFailureListener {
            falhaNoEnvio()
        }
    }

    private fun falhaNoEnvio() {
        enviando = false
        progressSelfie.visibility = View.GONE
        btnTirarNovamente.isEnabled = true
        btnUsarFoto.isEnabled = true
        mostrarStatus("Não foi possível enviar a selfie. Tente novamente.")
    }

    private fun mostrarStatus(mensagem: String) {
        txtStatusSelfie.text = mensagem
        txtStatusSelfie.visibility = View.VISIBLE
    }

    /** Decodifica o JPEG aplicando a rotação indicada no EXIF, para a pré-visualização ficar em pé. */
    private fun carregarBitmapCorrigido(arquivo: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(arquivo.absolutePath)

        val orientacao = ExifInterface(arquivo.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val matriz = Matrix()
        when (orientacao) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matriz.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matriz.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matriz.postRotate(270f)
        }

        return if (matriz.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!enviando) {
            arquivoCapturado?.delete()
        }
    }
}
