package com.example.vaivan.data.remote.routes

import android.content.Context
import android.content.pm.PackageManager
import com.example.vaivan.core.util.PolylineUtil
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Um ponto (origem, destino ou parada) a ser enviado para a Routes API. */
data class PontoRota(
    val id: String,
    val nome: String,
    val endereco: String,
    val latitude: Double,
    val longitude: Double
)

/** Trecho entre dois pontos consecutivos da rota já calculada. */
data class PernaRota(
    val distanciaMetros: Int,
    val duracaoSegundos: Int
)

/** Resultado devolvido pela Routes API, já interpretado. */
data class RotaCalculada(
    val ordemOtimizada: List<Int>,
    val polylineEncoded: String,
    val polyline: List<LatLng>,
    val distanciaMetros: Int,
    val duracaoSegundos: Int,
    val duracaoSemTrafegoSegundos: Int,
    val pernas: List<PernaRota>
)

/**
 * Cliente da Google Routes API (routes.googleapis.com), responsável por
 * calcular a rota mais eficiente entre uma origem, um destino e uma lista
 * de paradas intermediárias — com otimização de ordem (optimizeWaypointOrder)
 * e modelagem de trânsito completa (TRAFFIC_AWARE_OPTIMAL).
 */
class GoogleRoutesClient(private val context: Context) {

    companion object {
        private const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"
        const val MAX_PARADAS_OTIMIZAVEIS = 25
    }

    suspend fun calcularMelhorRota(
        origem: PontoRota,
        destino: PontoRota,
        paradas: List<PontoRota>
    ): RotaCalculada = withContext(Dispatchers.IO) {

        require(paradas.size <= MAX_PARADAS_OTIMIZAVEIS) {
            "A Routes API otimiza no máximo $MAX_PARADAS_OTIMIZAVEIS paradas por chamada."
        }

        val corpo = montarCorpoRequisicao(origem, destino, paradas)
        val resposta = executarRequisicao(corpo)
        interpretarResposta(resposta)
    }

    private fun montarCorpoRequisicao(
        origem: PontoRota,
        destino: PontoRota,
        paradas: List<PontoRota>
    ): JSONObject {

        fun waypoint(ponto: PontoRota) = JSONObject().apply {
            put("location", JSONObject().apply {
                put("latLng", JSONObject().apply {
                    put("latitude", ponto.latitude)
                    put("longitude", ponto.longitude)
                })
            })
        }

        val intermediates = JSONArray()
        paradas.forEach { parada ->
            intermediates.put(waypoint(parada).apply { put("vehicleStopover", true) })
        }

        return JSONObject().apply {
            put("origin", waypoint(origem))
            put("destination", waypoint(destino))
            put("intermediates", intermediates)
            put("travelMode", "DRIVE")
            put("routingPreference", "TRAFFIC_AWARE_OPTIMAL")
            put("optimizeWaypointOrder", true)
            put("computeAlternativeRoutes", false)
            put("polylineQuality", "HIGH_QUALITY")
            put("polylineEncoding", "ENCODED_POLYLINE")
            put("languageCode", "pt-BR")
            put("units", "METRIC")
        }
    }

    private fun executarRequisicao(corpo: JSONObject): JSONObject {

        val url = URL(ENDPOINT)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Goog-Api-Key", obterApiKey())
            connection.setRequestProperty(
                "X-Goog-FieldMask",
                "routes.duration,routes.staticDuration,routes.distanceMeters," +
                        "routes.polyline.encodedPolyline,routes.optimizedIntermediateWaypointIndex," +
                        "routes.legs.duration,routes.legs.distanceMeters"
            )

            connection.outputStream.use { it.write(corpo.toString().toByteArray(Charsets.UTF_8)) }

            val codigo = connection.responseCode
            if (codigo !in 200..299) {
                val erro = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Routes API HTTP $codigo${erro?.let { ": $it" } ?: ""}")
            }

            val texto = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(texto)

        } finally {
            connection.disconnect()
        }
    }

    private fun interpretarResposta(resposta: JSONObject): RotaCalculada {

        val rotas = resposta.optJSONArray("routes")
            ?: throw Exception("Nenhuma rota retornada pelo Google.")

        if (rotas.length() == 0) {
            throw Exception("Google não encontrou uma rota viável.")
        }

        val rota = rotas.getJSONObject(0)

        val polylineCodificada = rota.getJSONObject("polyline").getString("encodedPolyline")
        val polyline = PolylineUtil.decode(polylineCodificada)

        val distanciaMetros = rota.optInt("distanceMeters", 0)
        val duracaoSegundos = parseDuracao(rota.optString("duration", "0s"))
        val duracaoSemTrafegoSegundos = parseDuracao(rota.optString("staticDuration", "0s"))

        val ordemOtimizada = mutableListOf<Int>()
        rota.optJSONArray("optimizedIntermediateWaypointIndex")?.let { array ->
            for (i in 0 until array.length()) {
                ordemOtimizada.add(array.getInt(i))
            }
        }

        val pernas = mutableListOf<PernaRota>()
        rota.optJSONArray("legs")?.let { array ->
            for (i in 0 until array.length()) {
                val perna = array.getJSONObject(i)
                pernas.add(
                    PernaRota(
                        distanciaMetros = perna.optInt("distanceMeters", 0),
                        duracaoSegundos = parseDuracao(perna.optString("duration", "0s"))
                    )
                )
            }
        }

        return RotaCalculada(
            ordemOtimizada = ordemOtimizada,
            polylineEncoded = polylineCodificada,
            polyline = polyline,
            distanciaMetros = distanciaMetros,
            duracaoSegundos = duracaoSegundos,
            duracaoSemTrafegoSegundos = duracaoSemTrafegoSegundos,
            pernas = pernas
        )
    }

    private fun parseDuracao(valor: String): Int {
        return valor.removeSuffix("s").toDoubleOrNull()?.toInt() ?: 0
    }

    private fun obterApiKey(): String {
        val applicationInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        return applicationInfo.metaData?.getString("com.google.android.geo.API_KEY")
            ?: throw Exception("API Key do Google Maps não encontrada no AndroidManifest.")
    }
}