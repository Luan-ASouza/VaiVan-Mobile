package com.example.trabalhograua.ui.responsavel.rotas

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.trabalhograua.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AcompanharViagemFragment : Fragment(), OnMapReadyCallback {

    // =========================================================
    // MAPA
    // =========================================================

    private lateinit var mMap: GoogleMap

    // =========================================================
    // FIRESTORE
    // =========================================================

    private val firestore =
        FirebaseFirestore.getInstance()

    // =========================================================
    // THREAD PARA ROUTES API
    // =========================================================

    private val executor =
        Executors.newSingleThreadExecutor()

    // =========================================================
    // SIMULADOR DO MOTORISTA
    // =========================================================

    private val simuladorHandler =
        Handler(Looper.getMainLooper())

    private var marcadorMotorista: Marker? = null

    private var rotaMotorista =
        emptyList<LatLng>()

    private var indiceRotaAtual = 0

    private var simulacaoAtiva = false

    /*
     * Tempo aproximado que o motorista leva
     * para passar de um ponto da polyline
     * para outro.
     *
     * Quanto menor:
     * mais rápido será o motorista.
     */
    private val duracaoTrecho = 150L

    /*
     * Intervalo entre cada atualização visual
     * do marcador.
     */
    private val intervaloAnimacao = 30L

    // =========================================================
    // RUNNABLE DO SIMULADOR
    // =========================================================

    private val simuladorRunnable =
        object : Runnable {

            override fun run() {

                if (!simulacaoAtiva) {
                    return
                }

                moverParaProximoPonto()
            }
        }

    // =========================================================
    // ON CREATE VIEW
    // =========================================================

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

    // =========================================================
    // ON VIEW CREATED
    // =========================================================

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )

        val mapFragment =
            childFragmentManager
                .findFragmentById(
                    R.id.map
                ) as SupportMapFragment?

        mapFragment?.getMapAsync(this)
    }

    // =========================================================
    // MAP READY
    // =========================================================

    override fun onMapReady(
        googleMap: GoogleMap
    ) {

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

                val locais =
                    resultado.documents
                        .mapNotNull { documento ->

                            val latitude =
                                documento.getDouble(
                                    "latitude"
                                )

                            val longitude =
                                documento.getDouble(
                                    "longitude"
                                )

                            if (
                                latitude == null ||
                                longitude == null
                            ) {
                                return@mapNotNull null
                            }

                            LocalMapa(

                                id = documento.id,

                                nome =
                                    documento.getString(
                                        "nome"
                                    ) ?: "Local",

                                endereco =
                                    documento.getString(
                                        "endereco"
                                    ) ?: "",

                                latitude = latitude,

                                longitude = longitude,

                                criadoEm =
                                    documento.getTimestamp(
                                        "criadoEm"
                                    )
                            )
                        }
                        .sortedWith(
                            compareBy(
                                nullsLast()
                            ) {
                                it.criadoEm
                            }
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
                val pontos =
                    locais.map { local ->

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
    // MARKERS DOS LOCAIS
    // =========================================================

    private fun adicionarMarcadores(
        locais: List<LocalMapa>
    ) {

        locais.forEachIndexed { index, local ->

            val ponto =
                LatLng(
                    local.latitude,
                    local.longitude
                )

            val cor =
                when {

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
                        BitmapDescriptorFactory
                            .defaultMarker(cor)
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

                val rotaCompleta =
                    mutableListOf<LatLng>()

                /*
                 * Divide a rota em trechos caso existam
                 * muitos pontos.
                 */
                val tamanhoMaximoTrecho = 27

                var inicio = 0

                while (
                    inicio < pontos.lastIndex
                ) {

                    val fim =
                        minOf(
                            inicio +
                                    tamanhoMaximoTrecho -
                                    1,
                            pontos.lastIndex
                        )

                    val trecho =
                        pontos.subList(
                            inicio,
                            fim + 1
                        )

                    val rota =
                        requisitarRota(trecho)

                    if (
                        rotaCompleta.isEmpty()
                    ) {

                        rotaCompleta.addAll(
                            rota
                        )

                    } else {

                        /*
                         * Evita duplicar o ponto
                         * de conexão.
                         */
                        rotaCompleta.addAll(
                            rota.drop(1)
                        )
                    }

                    inicio = fim
                }

                requireActivity()
                    .runOnUiThread {

                        desenharRota(
                            rotaCompleta
                        )
                    }

            } catch (erro: Exception) {

                requireActivity()
                    .runOnUiThread {

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

        val origem =
            pontos.first()

        val destino =
            pontos.last()

        val intermediarios =
            pontos
                .drop(1)
                .dropLast(1)

        val intermediariosJson =
            StringBuilder()

        intermediarios.forEachIndexed {
                index,
                ponto ->

            if (index > 0) {

                intermediariosJson.append(
                    ","
                )
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

        val body =
            buildString {

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

                append(
                    intermediariosJson
                )

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

        val url =
            URL(
                "https://routes.googleapis.com/directions/v2:computeRoutes"
            )

        val connection =
            url.openConnection()
                    as HttpURLConnection

        try {

            connection.requestMethod =
                "POST"

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

            connection.outputStream.use {
                    output ->

                output.write(
                    body.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            val responseCode =
                connection.responseCode

            if (
                responseCode !in 200..299
            ) {

                val erro =
                    try {

                        connection
                            .errorStream
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }

                    } catch (
                        _: Exception
                    ) {

                        null
                    }

                throw Exception(

                    "Routes API HTTP $responseCode" +
                            (
                                    erro?.let {
                                        ": $it"
                                    } ?: ""
                                    )
                )
            }

            val resposta =
                connection
                    .inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            val json =
                JSONObject(
                    resposta
                )

            val routes =
                json.optJSONArray(
                    "routes"
                )
                    ?: throw Exception(
                        "Nenhuma rota retornada pelo Google."
                    )

            if (
                routes.length() == 0
            ) {

                throw Exception(
                    "Google não encontrou uma rota."
                )
            }

            val route =
                routes.getJSONObject(0)

            val polyline =
                route
                    .getJSONObject(
                        "polyline"
                    )
                    .getString(
                        "encodedPolyline"
                    )

            return decodificarPolyline(
                polyline
            )

        } finally {

            connection.disconnect()
        }
    }

    // =========================================================
    // OBTER API KEY
    // =========================================================

    private fun obterApiKey(): String {

        val applicationInfo =
            requireContext()
                .packageManager
                .getApplicationInfo(
                    requireContext().packageName,
                    PackageManager.GET_META_DATA
                )

        return applicationInfo
            .metaData
            ?.getString(
                "com.google.android.geo.API_KEY"
            )
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

        val pontos =
            mutableListOf<LatLng>()

        var index = 0

        var latitude = 0

        var longitude = 0

        while (
            index < encoded.length
        ) {

            var resultado = 0

            var shift = 0

            var byte: Int

            // Latitude
            do {

                byte =
                    encoded[index++].code - 63

                resultado =
                    resultado or
                            (
                                    (byte and 0x1F)
                                            shl shift
                                    )

                shift += 5

            } while (
                byte >= 0x20
            )

            val deltaLatitude =
                if (
                    (resultado and 1) != 0
                ) {

                    (resultado shr 1)
                        .inv()

                } else {

                    resultado shr 1
                }

            latitude +=
                deltaLatitude

            // Longitude
            resultado = 0

            shift = 0

            do {

                byte =
                    encoded[index++].code - 63

                resultado =
                    resultado or
                            (
                                    (byte and 0x1F)
                                            shl shift
                                    )

                shift += 5

            } while (
                byte >= 0x20
            )

            val deltaLongitude =
                if (
                    (resultado and 1) != 0
                ) {

                    (resultado shr 1)
                        .inv()

                } else {

                    resultado shr 1
                }

            longitude +=
                deltaLongitude

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

        /*
         * Guarda a rota para o simulador.
         */
        rotaMotorista =
            pontos

        /*
         * Desenha a rota no mapa.
         */
        mMap.addPolyline(

            PolylineOptions()
                .addAll(pontos)
                .width(12f)
                .color(
                    Color.parseColor(
                        "#F6B12F"
                    )
                )
                .geodesic(false)
        )

        ajustarCamera(
            pontos
        )

        /*
         * Depois que a rota estiver desenhada,
         * inicia a simulação do motorista.
         */
        iniciarSimulacaoMotorista()
    }

    // =========================================================
    // CRIAR ÍCONE DA VAN COM TINT
    // =========================================================

    private fun criarIconeVan(
        cor: Int
    ): BitmapDescriptor {

        /*
         * Carrega o drawable:
         *
         * res/drawable/ic_van.xml
         */
        val drawable =
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_van
            )
                ?: throw IllegalStateException(
                    "Não foi possível carregar o ic_van."
                )

        /*
         * Aplica a cor ao drawable.
         */
        drawable.setTint(cor)

        /*
         * Verifica o tamanho original
         * do drawable.
         *
         * Caso não tenha tamanho definido,
         * usamos 96x96 como padrão.
         */
        val largura =
            if (
                drawable.intrinsicWidth > 0
            ) {

                drawable.intrinsicWidth

            } else {

                96
            }

        val altura =
            if (
                drawable.intrinsicHeight > 0
            ) {

                drawable.intrinsicHeight

            } else {

                96
            }

        /*
         * Cria um Bitmap real.
         *
         * É justamente isso que o Google Maps
         * precisa receber.
         */
        val bitmap =
            Bitmap.createBitmap(
                largura,
                altura,
                Bitmap.Config.ARGB_8888
            )

        /*
         * Cria o Canvas em cima do Bitmap.
         */
        val canvas =
            Canvas(bitmap)

        /*
         * Define onde o drawable será desenhado.
         */
        drawable.setBounds(
            0,
            0,
            canvas.width,
            canvas.height
        )

        /*
         * Desenha o drawable dentro do Bitmap.
         */
        drawable.draw(canvas)

        /*
         * Agora sim transformamos o Bitmap
         * em BitmapDescriptor para o Google Maps.
         */
        return BitmapDescriptorFactory
            .fromBitmap(bitmap)
    }

    // =========================================================
    // INICIAR SIMULAÇÃO
    // =========================================================

    private fun iniciarSimulacaoMotorista() {

        if (
            rotaMotorista.isEmpty()
        ) {
            return
        }

        /*
         * Se já estiver rodando, não cria
         * outra simulação.
         */
        if (simulacaoAtiva) {
            return
        }

        simulacaoAtiva = true

        indiceRotaAtual = 0

        /*
         * Remove marcador anterior caso exista.
         */
        marcadorMotorista?.remove()

        /*
         * Primeiro ponto da rota.
         */
        val primeiraPosicao =
            rotaMotorista.first()

        /*
         * Cria o marcador da van.
         */
        marcadorMotorista =
            mMap.addMarker(

                MarkerOptions()
                    .position(
                        primeiraPosicao
                    )
                    .title(
                        "Van do motorista"
                    )
                    .snippet(
                        "Viagem em andamento"
                    )
                    .icon(
                        criarIconeVan(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.dark_gray
                            )
                        )
                    )
                    .anchor(
                        0.5f,
                        0.5f
                    )
            )

        /*
         * Começa a movimentação.
         */
        simuladorHandler.post(
            simuladorRunnable
        )
    }

    // =========================================================
    // MOVER MOTORISTA
    // =========================================================

    private fun moverParaProximoPonto() {

        if (
            !simulacaoAtiva
        ) {
            return
        }

        /*
         * Verifica se chegou ao final.
         */
        if (
            indiceRotaAtual >=
            rotaMotorista.lastIndex
        ) {

            simulacaoAtiva = false

            /*
             * Garante que a van fique
             * exatamente no destino.
             */
            marcadorMotorista
                ?.position =
                rotaMotorista.last()

            Toast.makeText(
                requireContext(),
                "Motorista chegou ao destino.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val inicio =
            rotaMotorista[
                indiceRotaAtual
            ]

        val destino =
            rotaMotorista[
                indiceRotaAtual + 1
            ]

        /*
         * Quantos frames teremos neste trecho.
         */
        val quantidadeFrames =
            (
                    duracaoTrecho /
                            intervaloAnimacao
                    ).coerceAtLeast(1)

        var frameAtual = 0

        val animacao =
            object : Runnable {

                override fun run() {

                    if (
                        !simulacaoAtiva
                    ) {
                        return
                    }

                    /*
                     * Calcula o progresso:
                     *
                     * 0.0 = começo
                     * 1.0 = final
                     */
                    val progresso =
                        (
                                frameAtual.toFloat() /
                                        quantidadeFrames.toFloat()
                                )
                            .coerceIn(
                                0f,
                                1f
                            )

                    /*
                     * Interpola a posição
                     * entre início e destino.
                     */
                    val novaPosicao =
                        interpolarPosicao(
                            inicio,
                            destino,
                            progresso
                        )

                    /*
                     * Move o marcador.
                     */
                    marcadorMotorista
                        ?.position =
                        novaPosicao

                    frameAtual++

                    if (
                        frameAtual <=
                        quantidadeFrames
                    ) {

                        simuladorHandler.postDelayed(
                            this,
                            intervaloAnimacao
                        )

                    } else {

                        /*
                         * Terminou este trecho.
                         *
                         * Vai para o próximo.
                         */
                        indiceRotaAtual++

                        simuladorHandler.post(
                            simuladorRunnable
                        )
                    }
                }
            }

        simuladorHandler.post(
            animacao
        )
    }

    // =========================================================
    // INTERPOLAR POSIÇÃO
    // =========================================================

    private fun interpolarPosicao(
        inicio: LatLng,
        destino: LatLng,
        progresso: Float
    ): LatLng {

        val latitude =
            inicio.latitude +
                    (
                            destino.latitude -
                                    inicio.latitude
                            ) *
                    progresso

        val longitude =
            inicio.longitude +
                    (
                            destino.longitude -
                                    inicio.longitude
                            ) *
                    progresso

        return LatLng(
            latitude,
            longitude
        )
    }

    // =========================================================
    // PARAR SIMULAÇÃO
    // =========================================================

    private fun pararSimulacaoMotorista() {

        simulacaoAtiva = false

        simuladorHandler.removeCallbacksAndMessages(
            null
        )
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

                CameraUpdateFactory
                    .newLatLngZoom(
                        pontos.first(),
                        15f
                    )
            )

            return
        }

        val boundsBuilder =
            LatLngBounds.Builder()

        pontos.forEach { ponto ->

            boundsBuilder.include(
                ponto
            )
        }

        val bounds =
            boundsBuilder.build()

        mMap.animateCamera(

            CameraUpdateFactory
                .newLatLngBounds(
                    bounds,
                    120
                )
        )
    }

    // =========================================================
    // LIMPEZA
    // =========================================================

    override fun onDestroyView() {

        /*
         * Para a simulação antes de destruir
         * o Fragment.
         */
        pararSimulacaoMotorista()

        /*
         * Remove o marcador.
         */
        marcadorMotorista?.remove()

        marcadorMotorista = null

        /*
         * Encerra a thread usada pela Routes API.
         */
        executor.shutdown()

        super.onDestroyView()
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