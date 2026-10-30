package com.example.vaivan.ui.motorista.entrada.documentos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View

/**
 * Escurece a tela da câmera fora de uma moldura oval central, usada apenas
 * como orientação visual para o posicionamento do rosto (não há detecção).
 */
class OvalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paintEscurecido = Paint().apply {
        color = Color.parseColor("#B3000000")
    }

    private val paintFuro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val paintBorda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    init {
        // Necessário para o PorterDuff.Mode.CLEAR funcionar corretamente.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val largura = width.toFloat()
        val altura = height.toFloat()

        if (largura <= 0f || altura <= 0f) return

        val raioX = largura * 0.36f
        val raioY = altura * 0.22f
        val centroX = largura / 2f
        val centroY = altura * 0.42f

        canvas.drawRect(0f, 0f, largura, altura, paintEscurecido)

        canvas.drawOval(
            centroX - raioX,
            centroY - raioY,
            centroX + raioX,
            centroY + raioY,
            paintFuro
        )

        canvas.drawOval(
            centroX - raioX,
            centroY - raioY,
            centroX + raioX,
            centroY + raioY,
            paintBorda
        )
    }
}
