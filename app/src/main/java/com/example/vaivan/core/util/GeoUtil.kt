package com.example.vaivan.core.util
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtil {

    private const val RAIO_TERRA_METROS = 6_371_000.0
    private const val METROS_POR_GRAU = RAIO_TERRA_METROS * PI / 180.0

    /** Distância em linha reta entre dois pontos (fórmula de Haversine). */
    fun distanciaMetros(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val senLat = sin(dLat / 2)
        val senLng = sin(dLng / 2)

        val a = senLat * senLat +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                senLng * senLng

        return 2 * RAIO_TERRA_METROS * asin(min(1.0, sqrt(a)))
    }

    /** Distância de um ponto P até um segmento de reta A-B. */
    private fun distanciaPontoSegmentoMetros(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double
    ): Double {
        // 1 grau de longitude "encolhe" conforme a latitude
        val metrosPorGrauLng = METROS_POR_GRAU * cos(Math.toRadians(pLat))

        // Tudo em metros, com o ponto P na origem (0,0)
        val ax = (aLng - pLng) * metrosPorGrauLng
        val ay = (aLat - pLat) * METROS_POR_GRAU
        val bx = (bLng - pLng) * metrosPorGrauLng
        val by = (bLat - pLat) * METROS_POR_GRAU

        val dx = bx - ax
        val dy = by - ay
        val comprimentoQuadrado = dx * dx + dy * dy

        // Onde P "cai" sobre a reta AB (0 = em A, 1 = em B)
        val t = if (comprimentoQuadrado == 0.0) 0.0
        else ((-ax * dx) + (-ay * dy)) / comprimentoQuadrado

        val tLimitado = t.coerceIn(0.0, 1.0)   // não sai do segmento

        val cx = ax + tLimitado * dx           // ponto mais próximo no segmento
        val cy = ay + tLimitado * dy

        return sqrt(cx * cx + cy * cy)
    }

    /** Menor distância de um ponto até o trajeto (lista de pares lat/lng). */
    fun distanciaAoTrajetoMetros(
        latitude: Double,
        longitude: Double,
        trajeto: List<Pair<Double, Double>>
    ): Double {
        if (trajeto.isEmpty()) return Double.MAX_VALUE
        if (trajeto.size == 1) {
            return distanciaMetros(latitude, longitude, trajeto[0].first, trajeto[0].second)
        }

        var menor = Double.MAX_VALUE
        for (i in 0 until trajeto.size - 1) {
            val d = distanciaPontoSegmentoMetros(
                latitude, longitude,
                trajeto[i].first, trajeto[i].second,
                trajeto[i + 1].first, trajeto[i + 1].second
            )
            if (d < menor) menor = d
        }
        return menor
    }
}

