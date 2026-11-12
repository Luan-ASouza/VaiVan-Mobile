package com.example.vaivan.ui.comum.recorte

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.ImagemUtil
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/** Recorte (crop) de uma imagem antes do upload. Devolve a Uri de um JPEG
 * já recortado E comprimido (ver [ImagemUtil.Compressao]) — quem chama sobe
 * o resultado direto, sem comprimir de novo. Usar via [RecortarImagem]. */
class RecorteImagemActivity : BaseActivity() {

    private lateinit var recorteView: RecorteImagemView
    private lateinit var progress: ProgressBar
    private lateinit var btnConfirmar: MaterialButton
    private lateinit var btnGirar: MaterialButton

    private var bitmap: Bitmap? = null
    private var rotacao = 0
    private var processando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorte_imagem)
        aplicarGapsDeSistema()

        val uri = IntentCompat.getParcelableExtra(intent, EXTRA_URI, Uri::class.java)
        if (uri == null) {
            finish()
            return
        }
        val proporcao = intent.getFloatExtra(EXTRA_PROPORCAO, 0f).takeIf { it > 0f }
        val compressao = ImagemUtil.Compressao(
            ladoMaximoPx = intent.getIntExtra(EXTRA_LADO_MAXIMO, ImagemUtil.PERFIL.ladoMaximoPx),
            qualidade = intent.getIntExtra(EXTRA_QUALIDADE, ImagemUtil.PERFIL.qualidade)
        )
        rotacao = savedInstanceState?.getInt(ESTADO_ROTACAO) ?: 0

        recorteView = findViewById(R.id.recorteImagem)
        progress = findViewById(R.id.progressRecorte)
        btnConfirmar = findViewById(R.id.btnConfirmarRecorte)
        btnGirar = findViewById(R.id.btnGirarRecorte)
        intent.getStringExtra(EXTRA_TITULO)?.let {
            findViewById<TextView>(R.id.txtTituloRecorte).text = it
        }

        recorteView.proporcao = proporcao
        recorteView.circular = intent.getBooleanExtra(EXTRA_CIRCULAR, false)

        findViewById<MaterialButton>(R.id.btnCancelarRecorte).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        btnGirar.isEnabled = false
        btnGirar.setOnClickListener { girar() }
        btnConfirmar.setOnClickListener { confirmar(compressao) }

        carregar(uri, compressao)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(ESTADO_ROTACAO, rotacao)
    }

    private fun carregar(uri: Uri, compressao: ImagemUtil.Compressao) {
        lifecycleScope.launch {
            // Decodifica com folga acima do tamanho final, pra um recorte
            // pequeno ainda sair com resolução boa.
            val ladoDecodificacao = min(compressao.ladoMaximoPx * 2, LADO_MAXIMO_DECODIFICACAO)
            val carregado = withContext(Dispatchers.IO) {
                runCatching {
                    ImagemUtil.decodificar(this@RecorteImagemActivity, uri, ladoDecodificacao)
                        ?.let { rotacionar(it, rotacao) }
                }.getOrNull()
            }
            progress.visibility = View.GONE
            if (carregado == null) {
                Toast.makeText(this@RecorteImagemActivity, "Não foi possível abrir a imagem.", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
                return@launch
            }
            bitmap = carregado
            recorteView.definirImagem(carregado)
            btnConfirmar.isEnabled = true
            btnGirar.isEnabled = true
        }
    }

    private fun girar() {
        val atual = bitmap ?: return
        if (processando) return
        val girado = rotacionar(atual, 90)
        if (girado !== atual) atual.recycle()
        rotacao = (rotacao + 90) % 360
        bitmap = girado
        recorteView.definirImagem(girado)
    }

    private fun confirmar(compressao: ImagemUtil.Compressao) {
        val atual = bitmap ?: return
        val recorte = recorteView.recorteNoBitmap() ?: return
        if (processando) return
        processando = true
        btnConfirmar.isEnabled = false
        btnGirar.isEnabled = false
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val resultado = withContext(Dispatchers.Default) {
                runCatching {
                    val (largura, altura) = ImagemUtil.dimensoesFinais(
                        recorte.width(), recorte.height(), compressao.ladoMaximoPx
                    )
                    val matriz = Matrix().apply {
                        postScale(largura.toFloat() / recorte.width(), altura.toFloat() / recorte.height())
                    }
                    val recortado = Bitmap.createBitmap(
                        atual, recorte.left, recorte.top, recorte.width(), recorte.height(), matriz, true
                    )
                    val arquivo = withContext(Dispatchers.IO) {
                        ImagemUtil.salvarJpeg(this@RecorteImagemActivity, recortado, compressao.qualidade)
                    }
                    if (recortado !== atual) recortado.recycle()
                    Uri.fromFile(arquivo)
                }.getOrNull()
            }

            if (resultado == null) {
                processando = false
                btnConfirmar.isEnabled = true
                btnGirar.isEnabled = true
                progress.visibility = View.GONE
                Toast.makeText(this@RecorteImagemActivity, "Não foi possível recortar a imagem.", Toast.LENGTH_LONG).show()
                return@launch
            }
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_URI, resultado))
            finish()
        }
    }

    private fun rotacionar(origem: Bitmap, graus: Int): Bitmap {
        if (graus % 360 == 0) return origem
        val matriz = Matrix().apply { postRotate(graus.toFloat()) }
        return Bitmap.createBitmap(origem, 0, 0, origem.width, origem.height, matriz, true)
    }

    companion object {
        const val EXTRA_URI = "recorte_uri"
        const val EXTRA_PROPORCAO = "recorte_proporcao"
        const val EXTRA_CIRCULAR = "recorte_circular"
        const val EXTRA_LADO_MAXIMO = "recorte_lado_maximo"
        const val EXTRA_QUALIDADE = "recorte_qualidade"
        const val EXTRA_TITULO = "recorte_titulo"
        private const val ESTADO_ROTACAO = "rotacao"
        private const val LADO_MAXIMO_DECODIFICACAO = 2560
    }
}

/** Contrato pra abrir o recorte: `registerForActivityResult(RecortarImagem()) { uri -> }`.
 * Devolve null se o usuário cancelar. */
class RecortarImagem : ActivityResultContract<RecortarImagem.Pedido, Uri?>() {

    /** [proporcao] = largura/altura fixa (1f pra quadrado); null = livre. */
    data class Pedido(
        val uri: Uri,
        val proporcao: Float? = null,
        val circular: Boolean = false,
        val compressao: ImagemUtil.Compressao = ImagemUtil.PERFIL,
        val titulo: String? = null
    ) {
        companion object {
            /** Avatar (perfil/passageiro): quadrado com prévia circular. */
            fun avatar(uri: Uri) = Pedido(uri, proporcao = 1f, circular = true, compressao = ImagemUtil.PERFIL)

            /** Documento: recorte livre pra tirar fundo/bordas, resolução legível. */
            fun documento(uri: Uri) = Pedido(
                uri, proporcao = null, compressao = ImagemUtil.DOCUMENTO,
                titulo = "Recorte o documento"
            )
        }
    }

    override fun createIntent(context: Context, input: Pedido): Intent =
        Intent(context, RecorteImagemActivity::class.java)
            .putExtra(RecorteImagemActivity.EXTRA_URI, input.uri)
            .putExtra(RecorteImagemActivity.EXTRA_PROPORCAO, input.proporcao ?: 0f)
            .putExtra(RecorteImagemActivity.EXTRA_CIRCULAR, input.circular)
            .putExtra(RecorteImagemActivity.EXTRA_LADO_MAXIMO, input.compressao.ladoMaximoPx)
            .putExtra(RecorteImagemActivity.EXTRA_QUALIDADE, input.compressao.qualidade)
            .putExtra(RecorteImagemActivity.EXTRA_TITULO, input.titulo)
            // O picker só concede leitura da Uri pra quem a recebeu; repassa
            // a permissão pra tela de recorte.
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK && intent != null) {
            IntentCompat.getParcelableExtra(intent, RecorteImagemActivity.EXTRA_URI, Uri::class.java)
        } else {
            null
        }
}
