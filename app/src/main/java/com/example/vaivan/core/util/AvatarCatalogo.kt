package com.example.vaivan.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.ContextThemeWrapper
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.example.vaivan.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Avatares prontos (drawable/avatar_*.xml) e as cores que o usuário pode
 * escolher pra eles.
 *
 * Os vetores pintam com `?attr/avatarCor` / `?attr/avatarCorClara`; cada cor
 * daqui é um ThemeOverlay.VaiVan.Avatar.* (themes.xml) que define esses dois
 * atributos. Trocar a cor = carregar o mesmo drawable com outro overlay.
 */
object AvatarCatalogo {

    data class Cor(
        val nome: String,
        @StyleRes val overlay: Int,
        @ColorRes val cor: Int,
        @ColorRes val corClara: Int
    )

    val AVATARES: List<Int> = listOf(
        R.drawable.avatar_01, R.drawable.avatar_03, R.drawable.avatar_04, R.drawable.avatar_05,
        R.drawable.avatar_06, R.drawable.avatar_07, R.drawable.avatar_08, R.drawable.avatar_09,
        R.drawable.avatar_10, R.drawable.avatar_11, R.drawable.avatar_12, R.drawable.avatar_13,
        R.drawable.avatar_14, R.drawable.avatar_15, R.drawable.avatar_16, R.drawable.avatar_17,
        R.drawable.avatar_18, R.drawable.avatar_19, R.drawable.avatar_20, R.drawable.avatar_21
    )

    val CORES: List<Cor> = listOf(
        Cor("Rosa", R.style.ThemeOverlay_VaiVan_Avatar_Rosa, R.color.avatar_rosa, R.color.avatar_rosa_claro),
        Cor("Pêssego", R.style.ThemeOverlay_VaiVan_Avatar_Pessego, R.color.avatar_pessego, R.color.avatar_pessego_claro),
        Cor("Amarelo", R.style.ThemeOverlay_VaiVan_Avatar_Amarelo, R.color.avatar_amarelo, R.color.avatar_amarelo_claro),
        Cor("Verde", R.style.ThemeOverlay_VaiVan_Avatar_Verde, R.color.avatar_verde, R.color.avatar_verde_claro),
        Cor("Azul", R.style.ThemeOverlay_VaiVan_Avatar_Azul, R.color.avatar_azul, R.color.avatar_azul_claro),
        Cor("Lilás", R.style.ThemeOverlay_VaiVan_Avatar_Lilas, R.color.avatar_lilas, R.color.avatar_lilas_claro),
        Cor("Magenta", R.style.ThemeOverlay_VaiVan_Avatar_Magenta, R.color.avatar_magenta, R.color.avatar_magenta_claro)
    )

    /** O avatar [avatarRes] pintado na [cor]. Cada chamada devolve um
     * Drawable novo (não compartilha estado com os outros). */
    fun desenhar(context: Context, @DrawableRes avatarRes: Int, cor: Cor): Drawable {
        val comCor = ContextThemeWrapper(context, cor.overlay)
        return requireNotNull(AppCompatResources.getDrawable(comCor, avatarRes)).mutate()
    }

    /**
     * Renderiza o avatar num JPEG quadrado (lado de [ImagemUtil.PERFIL]) com o
     * fundo no tom claro da cor — vira uma foto comum, enviada pelo mesmo fluxo
     * de foto de perfil (e exibida com `circleCrop`, como qualquer foto).
     */
    suspend fun renderizar(context: Context, @DrawableRes avatarRes: Int, cor: Cor): Uri =
        withContext(Dispatchers.Default) {
            val lado = ImagemUtil.PERFIL.ladoMaximoPx
            val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            canvas.drawColor(ContextCompat.getColor(context, cor.corClara))
            desenhar(context, avatarRes, cor).apply {
                setBounds(0, 0, lado, lado)
                draw(canvas)
            }

            val arquivo = withContext(Dispatchers.IO) {
                ImagemUtil.salvarJpeg(context, bitmap, ImagemUtil.PERFIL.qualidade)
            }
            bitmap.recycle()
            Uri.fromFile(arquivo)
        }
}
