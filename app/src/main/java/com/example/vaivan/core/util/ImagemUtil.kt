package com.example.vaivan.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Compressão de imagem antes de subir pro Storage — foto de câmera sem
 * compressão (4–12 MB) deixa cadastro/edição perceptivelmente lentos; depois
 * de redimensionada e re-encodada em JPEG ela fica na faixa de 100–400 KB.
 *
 * A decodificação respeita a orientação do EXIF (senão a foto da câmera sai
 * deitada depois de re-encodada, já que o JPEG novo não carrega o EXIF). */
object ImagemUtil {

    /** Lado maior (px) e qualidade JPEG do arquivo final. */
    data class Compressao(val ladoMaximoPx: Int, val qualidade: Int)

    /** Avatar (perfil, passageiro): exibido pequeno e em círculo. */
    val PERFIL = Compressao(ladoMaximoPx = 720, qualidade = 80)

    /** Documentos (CNH, comprovante, selfie, CRLV): precisa continuar legível
     * pro administrador no painel. */
    val DOCUMENTO = Compressao(ladoMaximoPx = 1920, qualidade = 85)

    /** Redimensiona + comprime e devolve a Uri de um JPEG novo no cacheDir.
     * Roda fora da main thread. Se a imagem não puder ser lida, devolve a
     * própria [uri] (o upload segue com o original em vez de falhar). */
    suspend fun comprimir(
        context: Context,
        uri: Uri,
        compressao: Compressao = PERFIL
    ): Uri = withContext(Dispatchers.IO) {
        val bitmap = decodificar(context, uri, compressao.ladoMaximoPx)
            ?: return@withContext uri
        val arquivo = salvarJpeg(context, bitmap, compressao.qualidade)
        bitmap.recycle()
        Uri.fromFile(arquivo)
    }

    /** Decodifica a imagem já com lado maior ≤ [ladoMaximoPx] e na orientação
     * correta. Bloqueante — chamar fora da main thread. */
    fun decodificar(context: Context, uri: Uri, ladoMaximoPx: Int): Bitmap? {
        val resolver = context.contentResolver

        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = calcularInSampleSize(limites.outWidth, limites.outHeight, ladoMaximoPx)
        }
        val amostrado = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opcoes)
        } ?: return null

        val rotacao = runCatching {
            resolver.openInputStream(uri)?.use { rotacaoExif(ExifInterface(it)) }
        }.getOrNull() ?: 0

        // inSampleSize só reduz em potências de 2; o ajuste fino até o lado
        // máximo exato (e a rotação do EXIF) vão numa única Matrix.
        val (largura, altura) = dimensoesFinais(amostrado.width, amostrado.height, ladoMaximoPx)
        val matriz = Matrix().apply {
            postScale(largura.toFloat() / amostrado.width, altura.toFloat() / amostrado.height)
            if (rotacao != 0) postRotate(rotacao.toFloat())
        }
        if (matriz.isIdentity) return amostrado

        val final = Bitmap.createBitmap(amostrado, 0, 0, amostrado.width, amostrado.height, matriz, true)
        if (final !== amostrado) amostrado.recycle()
        return final
    }

    /** Grava o bitmap como JPEG num arquivo novo do cacheDir. Bloqueante. */
    fun salvarJpeg(context: Context, bitmap: Bitmap, qualidade: Int): File {
        val arquivo = File(context.cacheDir, "foto_comprimida_${System.currentTimeMillis()}.jpg")
        FileOutputStream(arquivo).use { saida ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, qualidade, saida)
        }
        return arquivo
    }

    /** Maior potência de 2 que ainda deixa o lado maior ≥ [ladoMaximoPx]
     * (o resto da redução é feito com escala exata, sem perder nitidez). */
    fun calcularInSampleSize(largura: Int, altura: Int, ladoMaximoPx: Int): Int {
        val ladoMaior = max(largura, altura)
        var inSampleSize = 1
        while (ladoMaior / (inSampleSize * 2) >= ladoMaximoPx) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /** Dimensões mantendo a proporção, com lado maior ≤ [ladoMaximoPx]
     * (imagem menor que o limite não é ampliada). */
    fun dimensoesFinais(largura: Int, altura: Int, ladoMaximoPx: Int): Pair<Int, Int> {
        val ladoMaior = max(largura, altura)
        if (ladoMaior <= ladoMaximoPx) return largura to altura
        val escala = ladoMaximoPx.toFloat() / ladoMaior
        return max(1, (largura * escala).roundToInt()) to max(1, (altura * escala).roundToInt())
    }

    private fun rotacaoExif(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
