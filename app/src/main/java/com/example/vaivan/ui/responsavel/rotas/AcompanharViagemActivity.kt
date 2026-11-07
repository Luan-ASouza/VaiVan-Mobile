package com.example.vaivan.ui.responsavel.rotas

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.example.vaivan.core.base.BaseActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.util.PolylineUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.remote.localizacao.FirestoreLocalizacaoViagemRemoteSource
import com.example.vaivan.data.repository.LocalizacaoViagemRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.domain.StatusPassageiro
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tela "Acompanhar Viagem" do responsável: mostra o status real (não
 * simulado) do filho numa viagem em andamento — embarcou/na van/desceu — e,
 * se o motorista estiver publicando a posição, a van de verdade se movendo
 * no mapa, com uma UX inspirada em apps de transporte (rastro que desaparece,
 * ícone suave e rotacionando, mapa escuro). Todo o acesso a dados passa pelo
 * [AcompanharViagemViewModel] (Repository + Room), nunca fala com o Firestore
 * diretamente.
 *
 * Tela própria (não embutida na HomeResponsavelActivity) pra ter um ciclo de
 * vida de mapa limpo e previsível — fechar aqui sempre destrói tudo de vez.
 */
class AcompanharViagemActivity : BaseActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_ROTA_ID = "rotaId"
        const val EXTRA_PASSAGEIRO_ID = "passageiroId"
        const val EXTRA_ALUNO_NOME = "alunoNome"
        const val EXTRA_ROTA_POLYLINE = "rotaPolyline"
        const val EXTRA_ROTA_NOME = "rotaNome"
        const val EXTRA_MOTORISTA_NOME = "motoristaNome"
        const val EXTRA_VEICULO_PLACA = "veiculoPlaca"

        /** Proporção vertical (0=topo, 1=base) do centro da foto dentro do
         * ícone composto foto+nome — ver [criarIconeComFotoENome]. */
        private const val ANCHOR_Y_FOTO_NOME = 0.32f
    }

    private lateinit var mMap: GoogleMap

    private lateinit var fotoAluno: ShapeableImageView
    private lateinit var txtNomeAluno: TextView
    private lateinit var txtEndereco: TextView
    private lateinit var txtStatusAluno: TextView
    private lateinit var txtStatusViagem: TextView
    private lateinit var txtEta: TextView
    private lateinit var txtNomeRota: TextView
    private lateinit var txtMotorista: TextView

    private var marcadoresDestinoDesenhados = false
    private val marcadoresOutrasParadas = mutableMapOf<Pair<Int, String>, Marker>()
    private var marcadorMotorista: Marker? = null
    private var polylineRestante: Polyline? = null
    private var indiceProgressoRota = 0
    private var animacaoMarcador: ValueAnimator? = null

    private val rotaId: String by lazy { intent.getStringExtra(EXTRA_ROTA_ID).orEmpty() }
    private val passageiroId: String by lazy { intent.getStringExtra(EXTRA_PASSAGEIRO_ID).orEmpty() }
    private val alunoNome: String by lazy { intent.getStringExtra(EXTRA_ALUNO_NOME).orEmpty() }
    private val rotaNome: String by lazy { intent.getStringExtra(EXTRA_ROTA_NOME).orEmpty() }
    private val motoristaNome: String by lazy { intent.getStringExtra(EXTRA_MOTORISTA_NOME).orEmpty() }
    private val veiculoPlaca: String by lazy { intent.getStringExtra(EXTRA_VEICULO_PLACA).orEmpty() }

    /** Rota pré-cadastrada (não é a rota recalculada pela navegação — evita
     * uma chamada nova à Routes API; o polyline já foi calculado uma vez no
     * cadastro da rota e fica guardado em RotaEntity.polylineEncoded). */
    private val pontosRotaPredeterminada: List<LatLng> by lazy {
        val encoded = intent.getStringExtra(EXTRA_ROTA_POLYLINE).orEmpty()
        if (encoded.isBlank()) emptyList() else PolylineUtil.decode(encoded)
    }

    private val viewModel: AcompanharViagemViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(this@AcompanharViagemActivity)

                AcompanharViagemViewModel(
                    rotaId,
                    passageiroId,
                    ViagemRepository(
                        db.viagemDao(),
                        db.paradaViagemDao(),
                        db.eventoViagemDao()
                    ),
                    LocalizacaoViagemRepository(
                        db.localizacaoViagemDao(),
                        FirestoreLocalizacaoViagemRemoteSource()
                    )
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_acompanhar_viagem)

        fotoAluno = findViewById(R.id.fotoAlunoAcompanhar)
        txtNomeAluno = findViewById(R.id.txtNomeAlunoAcompanhar)
        txtEndereco = findViewById(R.id.txtEnderecoAcompanhar)
        txtStatusAluno = findViewById(R.id.txtStatusAlunoAcompanhar)
        txtStatusViagem = findViewById(R.id.txtStatusViagemAcompanhar)
        txtEta = findViewById(R.id.txtEtaAcompanhar)
        txtNomeRota = findViewById(R.id.txtNomeRotaAcompanhar)
        txtMotorista = findViewById(R.id.txtMotoristaAcompanhar)

        txtNomeAluno.text = alunoNome.ifBlank { "Aluno" }
        txtStatusViagem.text = "Carregando viagem..."
        txtNomeRota.text = rotaNome.ifBlank { "Viagem" }
        txtMotorista.text = listOfNotNull(
            motoristaNome.ifBlank { null },
            veiculoPlaca.ifBlank { null }
        ).joinToString(" · ").ifBlank { "Motorista não informado" }

        findViewById<View>(R.id.btnFecharAcompanhar).setOnClickListener { finish() }
        findViewById<View>(R.id.btnCentralizarVanAcompanhar).setOnClickListener { centralizarNaVan() }
        findViewById<View>(R.id.btnCentralizarPassageiroAcompanhar).setOnClickListener { centralizarNoPassageiro() }

        aplicarGapsDeSistema()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        observarEstado()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = false
        mMap.uiSettings.isCompassEnabled = true
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.estilo_mapa_escuro))

        desenharRotaPredeterminada()
        desenharMarcadoresDestinoSePossivel(viewModel.paradasDoFilho.value)
        atualizarOutrasParadas(viewModel.todasParadas.value)
    }

    /** Fallback sem custo: a rota já cadastrada (não a recalculada pela
     * navegação) — vai sendo "comida" por trás conforme a van avança, igual
     * Uber/99. */
    private fun desenharRotaPredeterminada() {
        if (pontosRotaPredeterminada.isEmpty()) return

        polylineRestante = mMap.addPolyline(
            PolylineOptions()
                .addAll(pontosRotaPredeterminada)
                .width(18f)
                .color(ContextCompat.getColor(this, R.color.color_primary))
                .geodesic(false)
        )
    }

    /** Corta o trecho já percorrido: acha o ponto da rota pré-determinada
     * mais próximo da posição atual e redesenha a polyline só a partir dali.
     * Nunca volta pra trás (ruído de GPS não "devolve" caminho já comido). */
    private fun atualizarProgressoRota(posicaoAtual: LatLng) {
        if (pontosRotaPredeterminada.isEmpty()) return

        var melhorIndice = indiceProgressoRota
        var melhorDistancia = Double.MAX_VALUE

        for (i in indiceProgressoRota until pontosRotaPredeterminada.size) {
            val ponto = pontosRotaPredeterminada[i]
            val dLat = ponto.latitude - posicaoAtual.latitude
            val dLng = ponto.longitude - posicaoAtual.longitude
            val distancia = dLat * dLat + dLng * dLng
            if (distancia < melhorDistancia) {
                melhorDistancia = distancia
                melhorIndice = i
            }
        }

        if (melhorIndice <= indiceProgressoRota) return
        indiceProgressoRota = melhorIndice

        polylineRestante?.points =
            pontosRotaPredeterminada.subList(indiceProgressoRota, pontosRotaPredeterminada.size)
    }

    private fun observarEstado() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.viagem.collect { atualizarStatusViagem(it) }
                }
                launch {
                    viewModel.paradasDoFilho.collect { paradas ->
                        atualizarEndereco(paradas)
                        atualizarEta(paradas)
                        desenharMarcadoresDestinoSePossivel(paradas)
                    }
                }
                launch {
                    viewModel.todasParadas.collect { paradas ->
                        atualizarOutrasParadas(paradas)
                    }
                }
                launch {
                    viewModel.statusAtual.collect { atualizarStatusAluno(it) }
                }
                launch {
                    viewModel.localizacaoMotorista.collect { atualizarMarcadorMotorista(it) }
                }
            }
        }
    }

    private fun atualizarStatusViagem(viagem: ViagemEntity?) {
        txtStatusViagem.text = when (viagem?.status) {
            null -> "Nenhuma viagem em andamento agora."
            ViagemRepository.STATUS_EM_ANDAMENTO -> "Viagem em andamento"
            ViagemRepository.STATUS_PAUSADA -> "Viagem pausada"
            ViagemRepository.STATUS_FINALIZADA -> "Viagem finalizada"
            else -> "Viagem cancelada"
        }
    }

    private fun atualizarStatusAluno(status: StatusPassageiro?) {
        if (status == null) {
            txtStatusAluno.text = "Aguardando"
            txtStatusAluno.setTextColor(ContextCompat.getColor(this, R.color.light_gray))
            return
        }
        txtStatusAluno.text = status.texto
        txtStatusAluno.setTextColor(ContextCompat.getColor(this, status.cor))
    }

    private fun paradaPendente(paradas: List<ParadaViagemEntity>): ParadaViagemEntity? {
        val embarque = paradas.firstOrNull { it.tipo == ViagemRepository.TIPO_EMBARQUE }
        val desembarque = paradas.firstOrNull { it.tipo == ViagemRepository.TIPO_DESEMBARQUE }
        return when {
            embarque != null && embarque.status == ViagemRepository.PARADA_AGUARDANDO -> embarque
            desembarque != null -> desembarque
            else -> embarque
        }
    }

    private fun atualizarEndereco(paradas: List<ParadaViagemEntity>) {
        val pendente = paradaPendente(paradas) ?: return

        txtEndereco.text = pendente.endereco.ifBlank { pendente.nomeLocal }

        Glide.with(fotoAluno)
            .load(pendente.fotoPassageiroUrl)
            .placeholder(R.drawable.ic_perfil)
            .error(R.drawable.ic_perfil)
            .circleCrop()
            .into(fotoAluno)
    }

    /** Estimativa simples e sem custo novo: usa o minuto estimado que já foi
     * calculado uma vez quando a viagem começou (ViagemRepository.iniciarViagem
     * grava horarioEstimadoMinutos em cada parada) — não é recalculada com
     * trânsito ao vivo, é só uma referência. */
    private fun atualizarEta(paradas: List<ParadaViagemEntity>) {
        val pendente = paradaPendente(paradas)
        val viagem = viewModel.viagem.value

        if (pendente == null || viagem == null || viagem.iniciadaEm <= 0L) {
            txtEta.visibility = View.GONE
            return
        }

        val etaMillis = viagem.iniciadaEm + pendente.horarioEstimadoMinutos * 60_000L
        val formato = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

        txtEta.text = "Chegada estimada: ${formato.format(etaMillis)}"
        txtEta.visibility = View.VISIBLE
    }

    // =========================================================
    // MARCADORES DOS PONTOS DE EMBARQUE/DESEMBARQUE DO FILHO
    // =========================================================

    private fun desenharMarcadoresDestinoSePossivel(paradas: List<ParadaViagemEntity>) {
        if (!::mMap.isInitialized || marcadoresDestinoDesenhados) return

        val embarque = paradas.firstOrNull { it.tipo == ViagemRepository.TIPO_EMBARQUE }
        val desembarque = paradas.firstOrNull { it.tipo == ViagemRepository.TIPO_DESEMBARQUE }
        if (embarque == null || desembarque == null) return

        marcadoresDestinoDesenhados = true

        val pontoEmbarque = LatLng(embarque.latitude, embarque.longitude)
        val pontoDesembarque = LatLng(desembarque.latitude, desembarque.longitude)

        desenharMarcadorFilho(pontoEmbarque, embarque)
        desenharMarcadorFilho(pontoDesembarque, desembarque)

        val boundsBuilder = LatLngBounds.Builder()
            .include(pontoEmbarque)
            .include(pontoDesembarque)
        pontosRotaPredeterminada.forEach { boundsBuilder.include(it) }

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
    }

    /** A escola é um ponto institucional, compartilhado por vários alunos —
     * nunca mostra a foto do filho ali, só um ícone genérico. Nos demais
     * pontos (a casa dele), mostra foto + nome, sempre visíveis (sem
     * depender de showInfoWindow, que só abre uma janela por vez e só some
     * no clique — ver [criarIconeComFotoENome]). */
    private fun desenharMarcadorFilho(ponto: LatLng, parada: ParadaViagemEntity) {
        if (parada.localId == "escola") {
            mMap.addMarker(
                MarkerOptions()
                    .position(ponto)
                    .title("Escola")
                    .icon(iconeDeVetor(R.drawable.ic_escola_generica, 40))
                    .anchor(0.5f, 0.5f)
            )
            return
        }

        lifecycleScope.launch {
            val bitmapFoto = carregarBitmapFoto(parada.fotoPassageiroUrl)
            val icone = criarIconeComFotoENome(bitmapFoto, parada.nomePassageiro)

            if (!::mMap.isInitialized) return@launch
            mMap.addMarker(
                MarkerOptions()
                    .position(ponto)
                    .icon(icone)
                    .anchor(0.5f, ANCHOR_Y_FOTO_NOME)
            )
        }
    }

    private suspend fun carregarBitmapFoto(fotoUrl: String?): Bitmap = withContext(Dispatchers.IO) {
        if (fotoUrl.isNullOrBlank()) {
            bitmapDoDrawable(R.drawable.ic_perfil, 96, 96)
        } else {
            try {
                Glide.with(this@AcompanharViagemActivity)
                    .asBitmap()
                    .load(fotoUrl)
                    .circleCrop()
                    .submit(96, 96)
                    .get()
            } catch (_: Exception) {
                bitmapDoDrawable(R.drawable.ic_perfil, 96, 96)
            }
        }
    }

    /** Monta um ícone com a foto circular por cima e o nome sempre visível
     * num rótulo por baixo — assim o nome não depende de showInfoWindow
     * (que só mantém uma janela aberta por vez entre vários marcadores). */
    private fun criarIconeComFotoENome(bitmapFoto: Bitmap, nome: String): BitmapDescriptor {
        val densidade = resources.displayMetrics.density
        val diametroFoto = (56 * densidade).toInt()
        val espacamento = (4 * densidade).toInt()
        val alturaRotulo = (28 * densidade).toInt()

        val paintTexto = Paint().apply {
            color = Color.WHITE
            textSize = 13f * densidade
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val larguraTexto = paintTexto.measureText(nome)
        val largura = maxOf(diametroFoto, (larguraTexto + 24 * densidade).toInt())
        val altura = diametroFoto + espacamento + alturaRotulo

        val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paintFundoRotulo = Paint().apply {
            color = Color.argb(220, 33, 33, 33)
            isAntiAlias = true
        }
        val topoRotulo = (diametroFoto + espacamento).toFloat()
        canvas.drawRoundRect(
            RectF(0f, topoRotulo, largura.toFloat(), altura.toFloat()),
            alturaRotulo / 2f,
            alturaRotulo / 2f,
            paintFundoRotulo
        )
        canvas.drawText(
            nome,
            largura / 2f,
            altura - alturaRotulo / 2f + paintTexto.textSize / 3f,
            paintTexto
        )

        val fotoEscalada = Bitmap.createScaledBitmap(bitmapFoto, diametroFoto, diametroFoto, true)
        canvas.drawBitmap(fotoEscalada, (largura - diametroFoto) / 2f, 0f, null)

        val paintBorda = Paint().apply {
            color = ContextCompat.getColor(this@AcompanharViagemActivity, R.color.color_primary)
            style = Paint.Style.STROKE
            strokeWidth = 3f * densidade
            isAntiAlias = true
        }
        canvas.drawCircle(largura / 2f, diametroFoto / 2f, diametroFoto / 2f - 2f, paintBorda)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    /** BitmapDescriptorFactory.fromResource() só decodifica imagens raster
     * (PNG/JPG) — um vector drawable (XML) faz o Maps SDK derrubar o app com
     * "Failed to decode image. The provided image must be a Bitmap.". Por
     * isso os ícones genéricos (vetor) precisam ser rasterizados manualmente
     * antes, igual [bitmapDoDrawable] já faz. */
    private fun iconeDeVetor(resId: Int, tamanhoDp: Int): BitmapDescriptor {
        val tamanhoPx = (tamanhoDp * resources.displayMetrics.density).toInt()
        return BitmapDescriptorFactory.fromBitmap(bitmapDoDrawable(resId, tamanhoPx, tamanhoPx))
    }

    private fun bitmapDoDrawable(resId: Int, largura: Int, altura: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, resId)
            ?: throw IllegalStateException("Drawable não encontrado: $resId")

        val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // =========================================================
    // OUTRAS PARADAS DA ROTA (anônimas — sem dados de outros passageiros)
    // =========================================================

    /** Reage a cada atualização: some com o pino de uma parada assim que ela
     * é resolvida (todo mundo daquele grupo já embarcou/desembarcou/não
     * embarcou) e só adiciona pino pras que ainda estão pendentes. */
    private fun atualizarOutrasParadas(todasParadas: List<ParadaViagemEntity>) {
        if (!::mMap.isInitialized || todasParadas.isEmpty()) return

        val gruposPendentes = todasParadas
            .filter { it.passageiroId != passageiroId }
            .groupBy { it.ordem to it.tipo }
            .filterValues { grupo ->
                grupo.any {
                    it.status == ViagemRepository.PARADA_AGUARDANDO ||
                        it.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE
                }
            }

        val chavesParaRemover = marcadoresOutrasParadas.keys - gruposPendentes.keys
        chavesParaRemover.forEach { chave -> marcadoresOutrasParadas.remove(chave)?.remove() }

        gruposPendentes.forEach { (chave, grupo) ->
            if (marcadoresOutrasParadas.containsKey(chave)) return@forEach

            val parada = grupo.first()
            marcadoresOutrasParadas[chave] = mMap.addMarker(
                MarkerOptions()
                    .position(LatLng(parada.latitude, parada.longitude))
                    .icon(iconeDeVetor(R.drawable.ic_parada_generica, 20))
                    .anchor(0.5f, 0.5f)
            ) ?: return@forEach
        }
    }

    // =========================================================
    // POSIÇÃO AO VIVO DO MOTORISTA
    // =========================================================

    private fun atualizarMarcadorMotorista(localizacao: LocalizacaoViagemEntity?) {
        if (!::mMap.isInitialized || localizacao == null) return

        val novaPosicao = LatLng(localizacao.latitude, localizacao.longitude)
        atualizarProgressoRota(novaPosicao)

        val marcadorExistente = marcadorMotorista
        if (marcadorExistente == null) {
            marcadorMotorista = mMap.addMarker(
                MarkerOptions()
                    .position(novaPosicao)
                    .title("Van do motorista")
                    .icon(criarIconeVan(ContextCompat.getColor(this, R.color.color_primary)))
                    .anchor(0.5f, 0.5f)
                    .flat(true)
            )
            return
        }

        val posicaoAnterior = marcadorExistente.position
        if (posicaoAnterior.latitude == novaPosicao.latitude &&
            posicaoAnterior.longitude == novaPosicao.longitude
        ) {
            return
        }

        marcadorExistente.rotation = calcularBearing(posicaoAnterior, novaPosicao)

        animacaoMarcador?.cancel()
        animacaoMarcador = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4_000L
            addUpdateListener { animador ->
                val fracao = animador.animatedValue as Float
                marcadorExistente.position = LatLng(
                    posicaoAnterior.latitude + (novaPosicao.latitude - posicaoAnterior.latitude) * fracao,
                    posicaoAnterior.longitude + (novaPosicao.longitude - posicaoAnterior.longitude) * fracao
                )
            }
            start()
        }
    }

    private fun calcularBearing(inicio: LatLng, fim: LatLng): Float {
        val locInicio = Location("").apply {
            latitude = inicio.latitude
            longitude = inicio.longitude
        }
        val locFim = Location("").apply {
            latitude = fim.latitude
            longitude = fim.longitude
        }
        return locInicio.bearingTo(locFim)
    }

    private fun criarIconeVan(cor: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_van)
            ?: throw IllegalStateException("Não foi possível carregar o ic_van.")
        drawable.setTint(cor)

        val largura = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val altura = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96

        val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // =========================================================
    // BOTÕES DE CENTRALIZAR
    // =========================================================

    private fun centralizarNaVan() {
        val posicao = marcadorMotorista?.position
        if (posicao == null) {
            Toast.makeText(this, "A van ainda não está publicando localização.", Toast.LENGTH_SHORT).show()
            return
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(posicao, 17.5f))
    }

    /** Centraliza no ponto que importa agora pro filho (onde ele vai ser
     * buscado/deixado, a mesma lógica de [paradaPendente] usada no card de
     * status) — não é a localização do próprio responsável. */
    private fun centralizarNoPassageiro() {
        val pendente = paradaPendente(viewModel.paradasDoFilho.value)
        if (pendente == null) {
            Toast.makeText(this, "Localização do passageiro ainda não disponível.", Toast.LENGTH_SHORT).show()
            return
        }
        mMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(pendente.latitude, pendente.longitude), 17.5f)
        )
    }

    override fun onDestroy() {
        animacaoMarcador?.cancel()
        animacaoMarcador = null
        marcadorMotorista?.remove()
        marcadorMotorista = null
        super.onDestroy()
    }
}
