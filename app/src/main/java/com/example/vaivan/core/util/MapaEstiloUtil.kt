package com.example.vaivan.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.vaivan.R
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap

/**
 * Visual padrão dos mapas de rota (mesmo do acompanhamento em tempo real):
 * mapa claro, rota laranja com contorno e pinos com rótulo/haste/anel.
 */
object MapaEstiloUtil {

    /** Pino pronto + a proporção vertical do anel da base (o ponto exato no mapa). */
    class Pino(val descriptor: BitmapDescriptor, val anchorY: Float)

    /** Estilo claro, sem bússola/toolbar/prédios 3D — chamar no onMapReady. */
    fun aplicarEstiloClaro(context: Context, mapa: GoogleMap) {
        mapa.uiSettings.isZoomControlsEnabled = false
        mapa.uiSettings.isCompassEnabled = false
        mapa.uiSettings.isMapToolbarEnabled = false
        mapa.isBuildingsEnabled = false
        mapa.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.estilo_mapa_claro))
    }

    /** Desenha a rota (traço laranja sobre um contorno mais largo e escuro). */
    fun desenharRota(context: Context, mapa: GoogleMap, pontos: List<LatLng>) {
        val d = context.resources.displayMetrics.density
        mapa.addPolyline(estiloPolyline(context, pontos, 9f * d, R.color.mapa_rota_contorno).zIndex(1f))
        mapa.addPolyline(estiloPolyline(context, pontos, 6f * d, R.color.mapa_rota).zIndex(2f))
    }

    private fun estiloPolyline(context: Context, pontos: List<LatLng>, larguraPx: Float, corRes: Int) =
        PolylineOptions()
            .addAll(pontos)
            .width(larguraPx)
            .color(ContextCompat.getColor(context, corRes))
            .startCap(RoundCap())
            .endCap(RoundCap())
            .jointType(JointType.ROUND)
            .geodesic(false)

    /**
     * Pino: rótulo opcional (pílula branca), cabeça redonda colorida (com foto,
     * ícone branco ou número dentro), haste fina e um anel na base.
     */
    fun criarPino(
        context: Context,
        corPino: Int,
        rotulo: String? = null,
        foto: Bitmap? = null,
        iconeResId: Int? = null,
        numero: String? = null
    ): Pino {
        val d = context.resources.displayMetrics.density
        val sombra = 4f * d
        val diametroCabeca = 46f * d
        val alturaHaste = 14f * d
        val raioAnel = 6f * d
        val espessura = 3f * d
        val alturaRotulo = 24f * d
        val espacoRotulo = 6f * d

        val fonte = ResourcesCompat.getFont(context, R.font.montserrat_semibold) ?: Typeface.DEFAULT_BOLD
        val paintTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.text_primary)
            textSize = 12f * d
            textAlign = Paint.Align.CENTER
            typeface = fonte
        }
        val larguraRotulo = rotulo?.let { paintTexto.measureText(it) + 20f * d } ?: 0f

        val largura = (maxOf(diametroCabeca, larguraRotulo) + 2 * sombra).toInt()
        val topoCabeca = sombra + if (rotulo != null) alturaRotulo + espacoRotulo else 0f
        val centroCabecaY = topoCabeca + diametroCabeca / 2f
        val centroAnelY = topoCabeca + diametroCabeca + alturaHaste + raioAnel
        val altura = (centroAnelY + raioAnel + espessura + sombra).toInt()
        val cx = largura / 2f

        val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val corSombra = Color.argb(70, 0, 0, 0)

        if (rotulo != null) {
            val paintFundo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                setShadowLayer(3f * d, 0f, 1f * d, corSombra)
            }
            val esquerda = cx - larguraRotulo / 2f
            canvas.drawRoundRect(
                RectF(esquerda, sombra, esquerda + larguraRotulo, sombra + alturaRotulo),
                alturaRotulo / 2f, alturaRotulo / 2f, paintFundo
            )
            val baseTexto = sombra + alturaRotulo / 2f - (paintTexto.descent() + paintTexto.ascent()) / 2f
            canvas.drawText(rotulo, cx, baseTexto, paintTexto)
        }

        val paintCor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = corPino }

        // Haste
        canvas.drawRect(cx - espessura / 2f, centroCabecaY, cx + espessura / 2f, centroAnelY, paintCor)

        // Anel da base
        canvas.drawCircle(cx, centroAnelY, raioAnel, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(2f * d, 0f, 1f * d, corSombra)
        })
        canvas.drawCircle(cx, centroAnelY, raioAnel, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = corPino
            style = Paint.Style.STROKE
            strokeWidth = espessura
        })

        // Cabeça
        canvas.drawCircle(cx, centroCabecaY, diametroCabeca / 2f, Paint(paintCor).apply {
            setShadowLayer(4f * d, 0f, 2f * d, corSombra)
        })

        if (foto != null) {
            val diametroFoto = (diametroCabeca - 2 * espessura).toInt()
            val escalada = Bitmap.createScaledBitmap(foto, diametroFoto, diametroFoto, true)
            canvas.drawBitmap(escalada, cx - diametroFoto / 2f, centroCabecaY - diametroFoto / 2f, null)
        } else if (numero != null) {
            val paintNumero = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.text_primary)
                textSize = 18f * d
                textAlign = Paint.Align.CENTER
                typeface = ResourcesCompat.getFont(context, R.font.montserrat_bold) ?: Typeface.DEFAULT_BOLD
            }
            val base = centroCabecaY - (paintNumero.descent() + paintNumero.ascent()) / 2f
            canvas.drawText(numero, cx, base, paintNumero)
        } else if (iconeResId != null) {
            val tamanho = (22f * d).toInt()
            ContextCompat.getDrawable(context, iconeResId)?.mutate()?.let { icone ->
                icone.setTint(Color.WHITE)
                val x = (cx - tamanho / 2f).toInt()
                val y = (centroCabecaY - tamanho / 2f).toInt()
                icone.setBounds(x, y, x + tamanho, y + tamanho)
                icone.draw(canvas)
            }
        }

        return Pino(BitmapDescriptorFactory.fromBitmap(bitmap), centroAnelY / altura)
    }
}
