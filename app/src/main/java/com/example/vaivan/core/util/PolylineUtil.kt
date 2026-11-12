package com.example.vaivan.core.util

import com.google.android.gms.maps.model.LatLng

object PolylineUtil {

    /** Inverso de [decode] (Encoded Polyline Algorithm, precisão 1e-5 — o
     * mesmo formato que a Routes API devolve). */
    fun encode(pontos: List<LatLng>): String {
        val saida = StringBuilder()
        var latitudeAnterior = 0
        var longitudeAnterior = 0

        for (ponto in pontos) {
            val latitude = Math.round(ponto.latitude * 100000.0).toInt()
            val longitude = Math.round(ponto.longitude * 100000.0).toInt()
            codificarValor(latitude - latitudeAnterior, saida)
            codificarValor(longitude - longitudeAnterior, saida)
            latitudeAnterior = latitude
            longitudeAnterior = longitude
        }

        return saida.toString()
    }

    private fun codificarValor(valor: Int, saida: StringBuilder) {
        var resto = if (valor < 0) (valor shl 1).inv() else valor shl 1
        while (resto >= 0x20) {
            saida.append(((0x20 or (resto and 0x1F)) + 63).toChar())
            resto = resto shr 5
        }
        saida.append((resto + 63).toChar())
    }

    fun decode(encoded: String): List<LatLng> {

        val pontos = mutableListOf<LatLng>()

        var index = 0
        var latitude = 0
        var longitude = 0

        while (index < encoded.length) {

            var resultado = 0
            var shift = 0
            var byte: Int

            do {
                byte = encoded[index++].code - 63
                resultado = resultado or ((byte and 0x1F) shl shift)
                shift += 5
            } while (byte >= 0x20)

            val deltaLatitude =
                if ((resultado and 1) != 0) (resultado shr 1).inv() else resultado shr 1
            latitude += deltaLatitude

            resultado = 0
            shift = 0

            do {
                byte = encoded[index++].code - 63
                resultado = resultado or ((byte and 0x1F) shl shift)
                shift += 5
            } while (byte >= 0x20)

            val deltaLongitude =
                if ((resultado and 1) != 0) (resultado shr 1).inv() else resultado shr 1
            longitude += deltaLongitude

            pontos.add(LatLng(latitude / 100000.0, longitude / 100000.0))
        }

        return pontos
    }
}