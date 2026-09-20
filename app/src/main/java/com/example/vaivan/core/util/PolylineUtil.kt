package com.example.vaivan.core.util

import com.google.android.gms.maps.model.LatLng

object PolylineUtil {

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