package com.example.vaivan.ui.comum.avatar

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/** ImageView sempre quadrada (altura = largura) — células da grade de avatares,
 * que se adaptam à largura da coluna. */
class QuadradoImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
