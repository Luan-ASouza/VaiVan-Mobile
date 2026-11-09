package com.example.vaivan.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Compressão de imagem antes de subir pro Storage — uploads de foto de
 * câmera sem compressão (vários MB) deixam cadastro/edição perceptivelmente
 * lentos. */
object ImagemUtil {

    fun comprimirImagem(
        context: Context,
        uri: Uri,
        tamanhoMaximoPx: Int = 1024,
        qualidade: Int = 80
    ): Uri {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        options.inSampleSize = calcularInSampleSize(options, tamanhoMaximoPx)
        options.inJustDecodeBounds = false

        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return uri

        val arquivo = File(context.cacheDir, "foto_comprimida_${System.currentTimeMillis()}.jpg")
        FileOutputStream(arquivo).use { saida ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, qualidade, saida)
        }
        bitmap.recycle()

        return Uri.fromFile(arquivo)
    }

    private fun calcularInSampleSize(options: BitmapFactory.Options, tamanhoMaximoPx: Int): Int {
        val altura = options.outHeight
        val largura = options.outWidth
        var inSampleSize = 1

        if (altura > tamanhoMaximoPx || largura > tamanhoMaximoPx) {
            val alturaMeia = altura / 2
            val larguraMeia = largura / 2

            while ((alturaMeia / inSampleSize) >= tamanhoMaximoPx &&
                (larguraMeia / inSampleSize) >= tamanhoMaximoPx
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
