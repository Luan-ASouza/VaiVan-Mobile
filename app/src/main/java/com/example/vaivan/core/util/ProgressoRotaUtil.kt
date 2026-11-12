package com.example.vaivan.core.util

import kotlin.math.min

/**
 * Onde a van está sobre a rota desenhada pro responsável. Kotlin puro (pares
 * lat/lng, igual [GeoUtil]) pra poder ser testado sem Android.
 *
 * Substitui o "ponto mais próximo em qualquer lugar do resto da rota", que
 * quebrava num desvio: longe da linha, o ponto mais próximo podia ser lá na
 * frente (onde o desvio volta pra rota) e a polyline era "comida" de uma vez.
 */
object ProgressoRotaUtil {

    /** Acima disso, a van é considerada fora da rota desenhada. */
    const val LIMITE_FORA_DA_ROTA_METROS = 40.0

    /** Quantos segmentos à frente do progresso atual são considerados. */
    const val JANELA_SEGMENTOS = 80

    /**
     * Índice do segmento (trajeto[i] -> trajeto[i+1]) onde a van está,
     * procurando só de [indiceAtual] até [janela] segmentos à frente — a
     * rota nunca anda pra trás. Null se a van estiver a mais de
     * [limiteMetros] de todos eles (fora da rota: não recortar nada).
     */
    fun localizarNaRota(
        trajeto: List<Pair<Double, Double>>,
        indiceAtual: Int,
        latitude: Double,
        longitude: Double,
        janela: Int = JANELA_SEGMENTOS,
        limiteMetros: Double = LIMITE_FORA_DA_ROTA_METROS
    ): Int? {
        if (trajeto.isEmpty()) return null
        val inicio = indiceAtual.coerceIn(0, trajeto.size - 1)

        if (trajeto.size == 1) {
            val d = GeoUtil.distanciaMetros(latitude, longitude, trajeto[0].first, trajeto[0].second)
            return if (d <= limiteMetros) 0 else null
        }

        val fim = min(trajeto.size - 2, inicio + janela)
        var melhorIndice = -1
        var melhorDistancia = Double.MAX_VALUE

        for (i in inicio..fim) {
            val a = trajeto[i]
            val b = trajeto[i + 1]
            val d = GeoUtil.distanciaPontoSegmentoMetros(
                latitude, longitude, a.first, a.second, b.first, b.second
            )
            if (d < melhorDistancia) {
                melhorDistancia = d
                melhorIndice = i
            }
        }

        return if (melhorIndice >= 0 && melhorDistancia <= limiteMetros) melhorIndice else null
    }

    /**
     * Ponto a [fracao] (0..1) do comprimento total do [caminho] — pra animar
     * o marcador seguindo as ruas em vez de cortar o quarteirão em linha reta.
     */
    fun pontoNaFracao(caminho: List<Pair<Double, Double>>, fracao: Double): Pair<Double, Double> {
        require(caminho.isNotEmpty())
        if (caminho.size == 1 || fracao <= 0.0) return caminho.first()
        if (fracao >= 1.0) return caminho.last()

        val comprimentos = (0 until caminho.size - 1).map { i ->
            GeoUtil.distanciaMetros(
                caminho[i].first, caminho[i].second,
                caminho[i + 1].first, caminho[i + 1].second
            )
        }
        val total = comprimentos.sum()
        if (total == 0.0) return caminho.last()

        var restante = total * fracao
        for (i in comprimentos.indices) {
            val trecho = comprimentos[i]
            if (restante <= trecho) {
                val t = if (trecho == 0.0) 0.0 else restante / trecho
                val a = caminho[i]
                val b = caminho[i + 1]
                return Pair(
                    a.first + (b.first - a.first) * t,
                    a.second + (b.second - a.second) * t
                )
            }
            restante -= trecho
        }
        return caminho.last()
    }
}
