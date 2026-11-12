package com.example.vaivan.ui.comum.recorte

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Mostra a imagem inteira (ajustada à tela) com uma moldura de recorte por
 * cima: arrastar dentro move a moldura, arrastar um canto redimensiona.
 * Com [proporcao] definida (largura/altura) os cantos mantêm a proporção;
 * com [circular] o escurecimento de fora vira um círculo (prévia de avatar). */
class RecorteImagemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var proporcao: Float? = null
    var circular: Boolean = false

    private var bitmap: Bitmap? = null

    /** Onde o bitmap está desenhado, em coordenadas da View. */
    private val areaImagem = RectF()

    /** Moldura de recorte, em coordenadas da View (sempre dentro de areaImagem). */
    private val moldura = RectF()

    private val densidade = resources.displayMetrics.density
    private val margem = 24 * densidade
    private val tamanhoMinimo = 64 * densidade
    private val raioToque = 32 * densidade

    private val paintImagem = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paintFora = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val paintBorda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2 * densidade
    }
    private val paintGrade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(110, 255, 255, 255)
        strokeWidth = 1 * densidade
    }
    private val paintCanto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 4 * densidade
        strokeCap = Paint.Cap.ROUND
    }
    private val caminhoFora = Path()

    private enum class Arrasto { NENHUM, MOVER, SUP_ESQ, SUP_DIR, INF_ESQ, INF_DIR }

    private var arrasto = Arrasto.NENHUM
    private var ultimoX = 0f
    private var ultimoY = 0f

    fun definirImagem(novo: Bitmap) {
        bitmap = novo
        recalcularAreaImagem()
        reiniciarMoldura()
        invalidate()
    }

    /** Retângulo da moldura convertido pra pixels do bitmap. */
    fun recorteNoBitmap(): Rect? {
        val bmp = bitmap ?: return null
        if (areaImagem.width() <= 0f) return null
        val escala = bmp.width / areaImagem.width()
        val esquerda = ((moldura.left - areaImagem.left) * escala).roundToInt().coerceIn(0, bmp.width - 1)
        val topo = ((moldura.top - areaImagem.top) * escala).roundToInt().coerceIn(0, bmp.height - 1)
        val direita = ((moldura.right - areaImagem.left) * escala).roundToInt().coerceIn(esquerda + 1, bmp.width)
        val base = ((moldura.bottom - areaImagem.top) * escala).roundToInt().coerceIn(topo + 1, bmp.height)
        return Rect(esquerda, topo, direita, base)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcularAreaImagem()
        reiniciarMoldura()
    }

    private fun recalcularAreaImagem() {
        val bmp = bitmap ?: return
        val larguraDisp = width - 2 * margem
        val alturaDisp = height - 2 * margem
        if (larguraDisp <= 0f || alturaDisp <= 0f) return
        val escala = min(larguraDisp / bmp.width, alturaDisp / bmp.height)
        val largura = bmp.width * escala
        val altura = bmp.height * escala
        val esquerda = (width - largura) / 2f
        val topo = (height - altura) / 2f
        areaImagem.set(esquerda, topo, esquerda + largura, topo + altura)
    }

    /** Moldura inicial: a maior possível (na proporção pedida), centralizada.
     * Sem proporção, cobre a imagem inteira — confirmar direto = sem recorte. */
    private fun reiniciarMoldura() {
        if (areaImagem.isEmpty) return
        val prop = proporcao
        if (prop == null) {
            moldura.set(areaImagem)
            return
        }
        var largura = areaImagem.width()
        var altura = largura / prop
        if (altura > areaImagem.height()) {
            altura = areaImagem.height()
            largura = altura * prop
        }
        val cx = areaImagem.centerX()
        val cy = areaImagem.centerY()
        moldura.set(cx - largura / 2, cy - altura / 2, cx + largura / 2, cy + altura / 2)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, null, areaImagem, paintImagem)

        caminhoFora.reset()
        caminhoFora.fillType = Path.FillType.EVEN_ODD
        caminhoFora.addRect(areaImagem, Path.Direction.CW)
        if (circular) {
            caminhoFora.addOval(moldura, Path.Direction.CW)
        } else {
            caminhoFora.addRect(moldura, Path.Direction.CW)
        }
        canvas.drawPath(caminhoFora, paintFora)

        // Grade dos terços
        val tercoX = moldura.width() / 3
        val tercoY = moldura.height() / 3
        for (i in 1..2) {
            canvas.drawLine(moldura.left + tercoX * i, moldura.top, moldura.left + tercoX * i, moldura.bottom, paintGrade)
            canvas.drawLine(moldura.left, moldura.top + tercoY * i, moldura.right, moldura.top + tercoY * i, paintGrade)
        }

        if (circular) canvas.drawOval(moldura, paintBorda)
        canvas.drawRect(moldura, if (circular) paintGrade else paintBorda)
        desenharCantos(canvas)
    }

    private fun desenharCantos(canvas: Canvas) {
        val t = min(20 * densidade, min(moldura.width(), moldura.height()) / 3)
        val l = moldura.left
        val r = moldura.right
        val tp = moldura.top
        val b = moldura.bottom
        canvas.drawLine(l, tp, l + t, tp, paintCanto); canvas.drawLine(l, tp, l, tp + t, paintCanto)
        canvas.drawLine(r, tp, r - t, tp, paintCanto); canvas.drawLine(r, tp, r, tp + t, paintCanto)
        canvas.drawLine(l, b, l + t, b, paintCanto); canvas.drawLine(l, b, l, b - t, paintCanto)
        canvas.drawLine(r, b, r - t, b, paintCanto); canvas.drawLine(r, b, r, b - t, paintCanto)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                arrasto = detectarArrasto(event.x, event.y)
                ultimoX = event.x
                ultimoY = event.y
                if (arrasto != Arrasto.NENHUM) parent?.requestDisallowInterceptTouchEvent(true)
                return arrasto != Arrasto.NENHUM
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - ultimoX
                val dy = event.y - ultimoY
                ultimoX = event.x
                ultimoY = event.y
                when (arrasto) {
                    Arrasto.MOVER -> mover(dx, dy)
                    Arrasto.NENHUM -> return false
                    else -> redimensionar(event.x, event.y)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                arrasto = Arrasto.NENHUM
                return true
            }
        }
        return false
    }

    private fun detectarArrasto(x: Float, y: Float): Arrasto {
        val cantos = listOf(
            Arrasto.SUP_ESQ to (moldura.left to moldura.top),
            Arrasto.SUP_DIR to (moldura.right to moldura.top),
            Arrasto.INF_ESQ to (moldura.left to moldura.bottom),
            Arrasto.INF_DIR to (moldura.right to moldura.bottom)
        )
        val maisProximo = cantos.minBy { (_, p) -> hypot(x - p.first, y - p.second) }
        if (hypot(x - maisProximo.second.first, y - maisProximo.second.second) <= raioToque) {
            return maisProximo.first
        }
        return if (moldura.contains(x, y)) Arrasto.MOVER else Arrasto.NENHUM
    }

    private fun mover(dx: Float, dy: Float) {
        val deslocX = dx.coerceIn(areaImagem.left - moldura.left, areaImagem.right - moldura.right)
        val deslocY = dy.coerceIn(areaImagem.top - moldura.top, areaImagem.bottom - moldura.bottom)
        moldura.offset(deslocX, deslocY)
    }

    /** O canto arrastado segue o dedo; o canto oposto fica fixo. */
    private fun redimensionar(x: Float, y: Float) {
        val fixoX = if (arrasto == Arrasto.SUP_ESQ || arrasto == Arrasto.INF_ESQ) moldura.right else moldura.left
        val fixoY = if (arrasto == Arrasto.SUP_ESQ || arrasto == Arrasto.SUP_DIR) moldura.bottom else moldura.top
        val sinalX = if (fixoX == moldura.right) -1f else 1f
        val sinalY = if (fixoY == moldura.bottom) -1f else 1f

        // Espaço máximo disponível a partir do canto fixo, na direção do arrasto
        val maxLargura = if (sinalX > 0) areaImagem.right - fixoX else fixoX - areaImagem.left
        val maxAltura = if (sinalY > 0) areaImagem.bottom - fixoY else fixoY - areaImagem.top

        var largura = (abs(x - fixoX)).coerceIn(min(tamanhoMinimo, maxLargura), maxLargura)
        var altura = (abs(y - fixoY)).coerceIn(min(tamanhoMinimo, maxAltura), maxAltura)

        proporcao?.let { prop ->
            // Usa a dimensão que o dedo puxou mais e deriva a outra
            if (largura / prop > altura) altura = largura / prop else largura = altura * prop
            if (altura > maxAltura) { altura = maxAltura; largura = altura * prop }
            if (largura > maxLargura) { largura = maxLargura; altura = largura / prop }
            // Moldura ficaria pequena demais pra pegar no canto: ignora o passo
            if (min(largura, altura) < min(tamanhoMinimo, min(maxLargura, maxAltura))) return
        }

        val novaX = fixoX + sinalX * largura
        val novaY = fixoY + sinalY * altura
        moldura.set(min(fixoX, novaX), min(fixoY, novaY), max(fixoX, novaX), max(fixoY, novaY))
    }
}
