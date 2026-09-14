package com.example.trabalhograua.ui.responsavel.rotas

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.trabalhograua.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AcompanharViagemFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    private val firestore = FirebaseFirestore.getInstance()

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_acompanhar_viagem,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?

        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap

        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true

        carregarLocais()
    }

    // =========================================================
    // FIRESTORE
    // =========================================================

    private fun carregarLocais() {

        firestore
            .collection("locais")
            .get()
            .addOnSuccessListener { resultado ->

                val locais = resultado.documents
                    .mapNotNull { documento ->

                        val latitude =
                            documento.getDouble("latitude")

                        val longitude =
                            documento.getDouble("longitude")

                        if (latitude == null || longitude == null) {
                            return@mapNotNull null
                        }

                        LocalMapa(
                            id = documento.id,

                            nome = documento.getString("nome")
                                ?: "Local",

                            endereco = documento.getString("endereco")
                                ?: "",

                            latitude = latitude,

                            longitude = longitude,

                            criadoEm = documento.getTimestamp("criadoEm")
                        )
                    }
                    .sortedWith(
                        compareBy(
                            nullsLast()
                        ) { it.criadoEm }
                    )

                if (locais.isEmpty()) {

                    Toast.makeText(
                        requireContext(),
                        "Nenhum local encontrado.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                // Adiciona os locais no mapa
                adicionarMarcadores(locais)

                // Transforma os locais em LatLng
                val pontos = locais.map { local ->

                    LatLng(
                        local.latitude,
                        local.longitude
                    )
                }

                // Calcula a rota pelas ruas
                calcularRota(pontos)
            }
            .addOnFailureListener { erro ->

                Toast.makeText(
                    requireContext(),
                    "Erro ao carregar locais: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // MARKERS
    // =========================================================

    private fun adicionarMarcadores(
        locais: List<LocalMapa>
    ) {

        locais.forEachIndexed { index, local ->

            val ponto = LatLng(
                local.latitude,
                local.longitude
            )

            val cor = when {

                // Primeiro ponto
                index == 0 ->
                    BitmapDescriptorFactory.HUE_GREEN

                // Último ponto
                index == locais.lastIndex ->
                    BitmapDescriptorFactory.HUE_RED

                // Pontos intermediários
                else ->
                    BitmapDescriptorFactory.HUE_ORANGE
            }

            mMap.addMarker(
                MarkerOptions()
                    .position(ponto)
                    .title(local.nome)
                    .snippet(local.endereco)
                    .icon(
                        BitmapDescriptorFactory.defaultMarker(cor)
                    )
            )
        }
    }

    // =========================================================
    // CALCULAR ROTA
    // =========================================================

    private fun calcularRota(
        pontos: List<LatLng>
    ) {

        if (pontos.size < 2) {

            ajustarCamera(pontos)

            return
        }

        executor.execute {

            try {

                val rotaCompleta = mutableListOf<LatLng>()

                /*
                 * Divide a rota em trechos caso existam
                 * muitos pontos.
                 */
                val tamanhoMaximoTrecho = 27

                var inicio = 0

                while (inicio < pontos.lastIndex) {

                    val fim = minOf(
                        inicio + tamanhoMaximoTrecho - 1,
                        pontos.lastIndex
                    )

                    val trecho = pontos.subList(
                        inicio,
                        fim + 1
                    )

                    val rota = requisitarRota(trecho)

                    if (rotaCompleta.isEmpty()) {

                        rotaCompleta.addAll(rota)

                    } else {

                        // Evita duplicar o ponto de conexão
                        rotaCompleta.addAll(
                            rota.drop(1)
                        )
                    }

                    inicio = fim
                }

                requireActivity().runOnUiThread {

                    desenharRota(rotaCompleta)
                }

            } catch (erro: Exception) {

                requireActivity().runOnUiThread {

                    Toast.makeText(
                        requireContext(),
                        "Erro ao calcular rota: ${erro.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // =========================================================
    // GOOGLE ROUTES API
    // =========================================================

    private fun requisitarRota(
        pontos: List<LatLng>
    ): List<LatLng> {

        val origem = pontos.first()

        val destino = pontos.last()

        val intermediarios = pontos
            .drop(1)
            .dropLast(1)

        val intermediariosJson = StringBuilder()

        intermediarios.forEachIndexed { index, ponto ->

            if (index > 0) {
                intermediariosJson.append(",")
            }

            intermediariosJson.append(
                """
                {
                    "location": {
                        "latLng": {
                            "latitude": ${ponto.latitude},
                            "longitude": ${ponto.longitude}
                        }
                    },
                    "vehicleStopover": true
                }
                """.trimIndent()
            )
        }

        val body = buildString {

            append("{")

            // ORIGEM
            append(
                """
                "origin": {
                    "location": {
                        "latLng": {
                            "latitude": ${origem.latitude},
                            "longitude": ${origem.longitude}
                        }
                    }
                },
                """.trimIndent()
            )

            // DESTINO
            append(
                """
                "destination": {
                    "location": {
                        "latLng": {
                            "latitude": ${destino.latitude},
                            "longitude": ${destino.longitude}
                        }
                    }
                },
                """.trimIndent()
            )

            // PONTOS INTERMEDIÁRIOS
            append(
                """
                "intermediates": [
                """.trimIndent()
            )

            append(intermediariosJson)

            append(
                """
                ],
                "travelMode": "DRIVE",
                "routingPreference": "TRAFFIC_AWARE",
                "computeAlternativeRoutes": false,
                "polylineQuality": "HIGH_QUALITY",
                "polylineEncoding": "ENCODED_POLYLINE",
                "languageCode": "pt-BR",
                "units": "METRIC"
                """.trimIndent()
            )

            append("}")
        }

        val url = URL(
            "https://routes.googleapis.com/directions/v2:computeRoutes"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        try {

            connection.requestMethod = "POST"

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "X-Goog-Api-Key",
                obterApiKey()
            )

            connection.setRequestProperty(
                "X-Goog-FieldMask",
                "routes.polyline.encodedPolyline"
            )

            connection.doOutput = true

            connection.outputStream.use { output ->

                output.write(
                    body.toByteArray(Charsets.UTF_8)
                )
            }

            val responseCode =
                connection.responseCode

            if (responseCode !in 200..299) {

                val erro = try {

                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }

                } catch (_: Exception) {

                    null
                }

                throw Exception(
                    "Routes API HTTP $responseCode" +
                            (erro?.let { ": $it" } ?: "")
                )
            }

            val resposta =
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

            val json =
                JSONObject(resposta)

            val routes =
                json.optJSONArray("routes")
                    ?: throw Exception(
                        "Nenhuma rota retornada pelo Google."
                    )

            if (routes.length() == 0) {

                throw Exception(
                    "Google não encontrou uma rota."
                )
            }

            val route =
                routes.getJSONObject(0)

            val polyline =
                route
                    .getJSONObject("polyline")
                    .getString("encodedPolyline")

            return decodificarPolyline(polyline)

        } finally {

            connection.disconnect()
        }
    }

    // =========================================================
    // OBTER API KEY DO ANDROID MANIFEST
    // =========================================================

    private fun obterApiKey(): String {

        val applicationInfo = requireContext()
            .packageManager
            .getApplicationInfo(
                requireContext().packageName,
                PackageManager.GET_META_DATA
            )

        return applicationInfo.metaData
            ?.getString("com.google.android.geo.API_KEY")
            ?: throw Exception(
                "API Key do Google Maps não encontrada no AndroidManifest."
            )
    }

    // =========================================================
    // DECODIFICAR POLYLINE
    // =========================================================

    private fun decodificarPolyline(
        encoded: String
    ): List<LatLng> {

        val pontos = mutableListOf<LatLng>()

        var index = 0

        var latitude = 0

        var longitude = 0

        while (index < encoded.length) {

            var resultado = 0

            var shift = 0

            var byte: Int

            // Latitude
            do {

                byte =
                    encoded[index++].code - 63

                resultado =
                    resultado or
                            ((byte and 0x1F) shl shift)

                shift += 5

            } while (byte >= 0x20)

            val deltaLatitude =
                if ((resultado and 1) != 0) {
                    (resultado shr 1).inv()
                } else {
                    resultado shr 1
                }

            latitude += deltaLatitude

            // Longitude
            resultado = 0

            shift = 0

            do {

                byte =
                    encoded[index++].code - 63

                resultado =
                    resultado or
                            ((byte and 0x1F) shl shift)

                shift += 5

            } while (byte >= 0x20)

            val deltaLongitude =
                if ((resultado and 1) != 0) {
                    (resultado shr 1).inv()
                } else {
                    resultado shr 1
                }

            longitude += deltaLongitude

            pontos.add(
                LatLng(
                    latitude / 100000.0,
                    longitude / 100000.0
                )
            )
        }

        return pontos
    }

    // =========================================================
    // DESENHAR ROTA
    // =========================================================

    private fun desenharRota(
        pontos: List<LatLng>
    ) {

        if (pontos.isEmpty()) {
            return
        }

        mMap.addPolyline(
            PolylineOptions()
                .addAll(pontos)
                .width(12f)
                .color(Color.parseColor("#F6B12F"))
                .geodesic(false)
        )

        ajustarCamera(pontos)
    }

    // =========================================================
    // AJUSTAR CÂMERA
    // =========================================================

    private fun ajustarCamera(
        pontos: List<LatLng>
    ) {

        if (pontos.isEmpty()) {
            return
        }

        // Apenas um ponto
        if (pontos.size == 1) {

            mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pontos.first(),
                    15f
                )
            )

            return
        }

        val boundsBuilder =
            LatLngBounds.Builder()

        pontos.forEach { ponto ->

            boundsBuilder.include(ponto)
        }

        val bounds =
            boundsBuilder.build()

        mMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds,
                120
            )
        )
    }

    // =========================================================
    // LIMPEZA
    // =========================================================

    override fun onDestroyView() {

        super.onDestroyView()

        executor.shutdown()
    }

    // =========================================================
    // MODELO DO LOCAL
    // =========================================================

    private data class LocalMapa(

        val id: String,

        val nome: String,

        val endereco: String,

        val latitude: Double,

        val longitude: Double,

        val criadoEm: Timestamp?
    )
}