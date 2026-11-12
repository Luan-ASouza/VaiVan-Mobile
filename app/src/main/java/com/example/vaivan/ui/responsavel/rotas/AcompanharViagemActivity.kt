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
import com.example.vaivan.core.util.EtaParadasUtil
import com.example.vaivan.core.util.PolylineUtil
import com.example.vaivan.core.util.ProgressoRotaUtil
import com.example.vaivan.data.local.entities.RotaViagemEntity
import com.example.vaivan.core.util.configurarBlocoAvisoAusencia
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.EventoViagemEntity
import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.remote.localizacao.FirestoreLocalizacaoViagemRemoteSource
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.LocalizacaoViagemRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.domain.usecase.ausencia.RegistrarAvisoAusenciaUseCase
import com.example.vaivan.domain.StatusPassageiro
import com.google.android.gms.maps.CameraUpdate
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
import android.content.Intent
import android.widget.Toast
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.ui.comum.chat.ChatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
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
        const val EXTRA_MOTORISTA_FOTO_URL = "motoristaFotoUrl"
        const val EXTRA_MOTORISTA_ID = "motoristaId"
        const val EXTRA_ROTA_IDA_E_VOLTA = "rotaIdaEVolta"

        /** Proporção vertical (0=topo, 1=base) do centro da foto dentro do
         * ícone composto foto+nome — ver [criarIconeComFotoENome]. */
        private const val ANCHOR_Y_FOTO_NOME = 0.32f

    }

    private lateinit var mMap: GoogleMap

    private lateinit var fotoAluno: ShapeableImageView
    private lateinit var fotoMotorista: ShapeableImageView
    private lateinit var txtNomeAluno: TextView
    private lateinit var txtEndereco: TextView
    private lateinit var txtStatusAluno: TextView
    private lateinit var txtStatusViagem: TextView
    private lateinit var txtEta: TextView
    private lateinit var txtNomeRota: TextView
    private lateinit var txtMotorista: TextView
    private lateinit var txtDesembarqueFinal: TextView
    private lateinit var txtEtaFinal: TextView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private var marcadoresDestinoDesenhados = false
    private val marcadoresOutrasParadas = mutableMapOf<Pair<Int, String>, Marker>()
    private var marcadorMotorista: Marker? = null
    private var marcadorEmbarqueFilho: Marker? = null
    private var polylineRestante: Polyline? = null
    private var animacaoMarcador: ValueAnimator? = null

    /** Rota desenhada agora: a publicada pelo motorista (espelho do
     * Navigation SDK) assim que existir, senão a do cadastro. */
    private var pontosRota: List<LatLng> = emptyList()
    private var trajetoRota: List<Pair<Double, Double>> = emptyList()

    /** atualizadoEm da última rota publicada aplicada (0 = rota do cadastro). */
    private var versaoRotaDesenhada = 0L

    /** Segmento de [pontosRota] onde a van está — só anda pra frente, e volta
     * a 0 quando chega uma rota nova. */
    private var indiceProgressoRota = 0

    /** Segmento da posição anterior da van, ou null se ela estava fora da rota
     * — decide se o marcador anima seguindo as ruas ou em linha reta. */
    private var indiceNaRotaDaUltimaPosicao: Int? = null

    /** Controla o texto "A caminho da escola" — ver [atualizarStatusGeral]. */
    private var aCaminhoDaEscola = false

    /** Duração da animação do marcador da van — a câmera usa o mesmo valor
     * pra se mover junto, suavemente, igual Uber/99. */
    private val duracaoAnimacaoMs = 4_000L

    private lateinit var btnRetomarRecentralizacao: View

    /** Enquanto true, a câmera reenquadra van+ponto a cada atualização de
     * localização. Pausa quando o responsável arrasta o mapa manualmente
     * (ver onCameraMoveStartedListener) — só volta ao tocar no FAB. */
    private var autoRecentralizarAtivo = true

    /** Ids de EventoViagemEntity já exibidos como notificação nesta sessão de
     * tela, pra não repetir a cada nova emissão do Flow. */
    private val eventosNotificados = mutableSetOf<String>()

    /** Eventos registrados antes de abrir a tela não geram notificação —
     * só o que acontece a partir de agora. */
    private val inicioObservacaoMs = System.currentTimeMillis()

    private val rotaId: String by lazy { intent.getStringExtra(EXTRA_ROTA_ID).orEmpty() }
    private val passageiroId: String by lazy { intent.getStringExtra(EXTRA_PASSAGEIRO_ID).orEmpty() }
    private val alunoNome: String by lazy { intent.getStringExtra(EXTRA_ALUNO_NOME).orEmpty() }
    private val rotaNome: String by lazy { intent.getStringExtra(EXTRA_ROTA_NOME).orEmpty() }
    private val motoristaNome: String by lazy { intent.getStringExtra(EXTRA_MOTORISTA_NOME).orEmpty() }
    private val veiculoPlaca: String by lazy { intent.getStringExtra(EXTRA_VEICULO_PLACA).orEmpty() }
    private val motoristaFotoUrl: String by lazy { intent.getStringExtra(EXTRA_MOTORISTA_FOTO_URL).orEmpty() }
    private val motoristaId: String by lazy { intent.getStringExtra(EXTRA_MOTORISTA_ID).orEmpty() }
    private val rotaIdaEVolta: Boolean by lazy { intent.getBooleanExtra(EXTRA_ROTA_IDA_E_VOLTA, false) }

    private val avisoAusenciaRepository by lazy {
        AvisoAusenciaRepository(VaivanDatabase.getInstance(this).avisoAusenciaDao())
    }

    private val viagemRepository: ViagemRepository by lazy {
        val db = VaivanDatabase.getInstance(this)
        ViagemRepository(db.viagemDao(), db.paradaViagemDao(), db.eventoViagemDao())
    }

    private val registrarAvisoAusenciaUseCase by lazy {
        RegistrarAvisoAusenciaUseCase(avisoAusenciaRepository, viagemRepository)
    }

    private var listenerAvisosAusencia: ListenerRegistration? = null

    /** Rota do cadastro (RotaEntity.polylineEncoded) — só o fallback enquanto
     * o motorista não publica a rota real da navegação ([aplicarRotaPublicada]).
     * Nenhum dos dois caminhos chama a Routes API neste aparelho. */
    private val pontosRotaCadastro: List<LatLng> by lazy {
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
                    FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                    viagemRepository,
                    LocalizacaoViagemRepository(
                        db.localizacaoViagemDao(),
                        db.rotaViagemDao(),
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
        fotoMotorista = findViewById(R.id.fotoMotoristaAcompanhar)
        txtNomeAluno = findViewById(R.id.txtNomeAlunoAcompanhar)
        txtEndereco = findViewById(R.id.txtEnderecoAcompanhar)
        txtStatusAluno = findViewById(R.id.txtStatusAlunoAcompanhar)
        txtStatusViagem = findViewById(R.id.txtStatusViagemAcompanhar)
        txtEta = findViewById(R.id.txtEtaAcompanhar)
        txtNomeRota = findViewById(R.id.txtNomeRotaAcompanhar)
        txtMotorista = findViewById(R.id.txtMotoristaAcompanhar)
        txtDesembarqueFinal = findViewById(R.id.txtDesembarqueFinalAcompanhar)
        txtEtaFinal = findViewById(R.id.txtEtaFinalAcompanhar)

        txtNomeAluno.text = alunoNome.ifBlank { "Aluno" }
        txtStatusViagem.text = "Carregando viagem..."
        txtNomeRota.text = rotaNome.ifBlank { "Viagem" }
        txtMotorista.text = listOfNotNull(
            motoristaNome.ifBlank { null },
            veiculoPlaca.ifBlank { null }
        ).joinToString(" · ").ifBlank { "Motorista não informado" }

        Glide.with(fotoMotorista)
            .load(motoristaFotoUrl)
            .placeholder(R.drawable.ic_perfil)
            .error(R.drawable.ic_perfil)
            .circleCrop()
            .into(fotoMotorista)

        btnRetomarRecentralizacao = findViewById(R.id.btnRetomarRecentralizacao)

        findViewById<View>(R.id.btnFecharAcompanhar).setOnClickListener { finish() }
        btnRetomarRecentralizacao.setOnClickListener {
            autoRecentralizarAtivo = true
            btnRetomarRecentralizacao.visibility = View.GONE
            marcadorMotorista?.position?.let { posicao -> recentralizarCamera(posicao) }
        }

        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.cardStatusAcompanhar))
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        findViewById<View>(R.id.btnAbrirChatAcompanhar).setOnClickListener { abrirChat() }

        FirebaseAuth.getInstance().currentUser?.uid?.let { responsavelId ->
            // Mantém o Room deste celular atualizado com o que acontece no
            // aviso depois de criado (motorista consumindo, ou cancelamento
            // em outro dispositivo) — sem isso, o Room só reflete o estado
            // do momento em que o aviso foi salvo, nunca mais.
            listenerAvisosAusencia = avisoAusenciaRepository.iniciarSincronizacaoDoResponsavel(responsavelId)

            configurarBlocoAvisoAusencia(
                container = findViewById(R.id.containerAvisoAusenciaAcompanhar),
                lifecycleOwner = this,
                repository = avisoAusenciaRepository,
                registrarAvisoUseCase = registrarAvisoAusenciaUseCase,
                viagemRepository = viagemRepository,
                rotaId = rotaId,
                passageiroId = passageiroId,
                nomePassageiro = alunoNome,
                responsavelId = responsavelId,
                motoristaId = motoristaId,
                rotaIdaEVolta = rotaIdaEVolta
            )
        }

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

        // Gesto do usuário (arrastar/pinçar) pausa a recentralização
        // automática — animateCamera (programático) não entra aqui.
        mMap.setOnCameraMoveStartedListener { motivo ->
            if (motivo == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                autoRecentralizarAtivo = false
                btnRetomarRecentralizacao.visibility = View.VISIBLE
            }
        }

        // Se a rota publicada chegou antes do mapa, ela já está em pontosRota.
        if (pontosRota.isEmpty()) definirPontosRota(pontosRotaCadastro)
        redesenharPolyline(pontosRota)
        desenharMarcadoresDestinoSePossivel(viewModel.paradasDoFilho.value)
        atualizarOutrasParadas(viewModel.todasParadas.value)
    }

    private fun definirPontosRota(pontos: List<LatLng>) {
        pontosRota = pontos
        trajetoRota = pontos.map { it.latitude to it.longitude }
        indiceProgressoRota = 0
        indiceNaRotaDaUltimaPosicao = null
    }

    /** Troca a linha pela rota que o motorista publicou (o que o Navigation
     * SDK está seguindo de fato) — chega no início da viagem e de novo a cada
     * desvio/recálculo ou parada concluída, então o desenho nunca fica preso
     * a uma rota que a van já abandonou. */
    private fun aplicarRotaPublicada(rota: RotaViagemEntity?) {
        if (rota == null || rota.atualizadoEm <= versaoRotaDesenhada) return
        val pontos = PolylineUtil.decode(rota.polylineEncoded)
        if (pontos.size < 2) return

        versaoRotaDesenhada = rota.atualizadoEm
        definirPontosRota(pontos)

        if (!::mMap.isInitialized) return // onMapReady desenha
        redesenharPolyline(pontos)
        marcadorMotorista?.position?.let { posicao ->
            indiceNaRotaDaUltimaPosicao = atualizarProgressoRota(posicao)
        }
    }

    private fun redesenharPolyline(pontos: List<LatLng>) {
        if (pontos.isEmpty()) return
        val existente = polylineRestante
        if (existente != null) {
            existente.points = pontos
            return
        }
        polylineRestante = mMap.addPolyline(
            PolylineOptions()
                .addAll(pontos)
                .width(18f)
                .color(ContextCompat.getColor(this, R.color.color_primary))
                .geodesic(false)
        )
    }

    /** Corta o trecho já percorrido ("rastro que desaparece", igual Uber/99).
     * Procura a van só numa janela à frente do progresso atual e só recorta se
     * ela estiver de fato em cima da linha ([ProgressoRotaUtil]) — fora dela
     * (desvio), não mexe em nada e espera a rota nova que o motorista publica
     * no recálculo. Retorna o segmento onde a van está, ou null se fora da rota. */
    private fun atualizarProgressoRota(posicaoAtual: LatLng): Int? {
        if (pontosRota.size < 2) return null

        val indice = ProgressoRotaUtil.localizarNaRota(
            trajetoRota, indiceProgressoRota, posicaoAtual.latitude, posicaoAtual.longitude
        ) ?: return null

        indiceProgressoRota = indice
        polylineRestante?.points =
            listOf(posicaoAtual) + pontosRota.subList(indice + 1, pontosRota.size)
        return indice
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
                        atualizarMarcadorEmbarqueFilho(paradas)
                    }
                }
                launch {
                    viewModel.todasParadas.collect { paradas ->
                        atualizarOutrasParadas(paradas)
                        atualizarDesembarqueFinal(paradas)
                        atualizarStatusGeral(paradas)
                    }
                }
                launch {
                    viewModel.statusAtual.collect { atualizarStatusAluno(it) }
                }
                launch {
                    viewModel.localizacaoMotorista.collect { localizacao ->
                        atualizarMarcadorMotorista(localizacao)
                        // ETA ao vivo vem junto da posição — reaplica a cada 5 s.
                        atualizarEta(viewModel.paradasDoFilho.value)
                        atualizarDesembarqueFinal(viewModel.todasParadas.value)
                    }
                }
                launch {
                    viewModel.rotaAtual.collect { aplicarRotaPublicada(it) }
                }
                launch {
                    viewModel.eventos.collect { atualizarNotificacoes(it) }
                }
            }
        }
    }

    /** Mostra um aviso na tela quando a van chega no ponto do filho (evento
     * registrado pelo motorista em NavegacaoViagemActivity.onArrival, junto
     * com o mesmo trigger que abre o card de confirmar embarque pra ele) ou
     * quando o embarque é confirmado — não repete o que já foi mostrado nem
     * o que aconteceu antes de abrir esta tela. Isso é só o aviso em app; a
     * notificação push (futura) vai consumir os mesmos eventos via
     * Firestore, não depende de esta tela estar aberta. */
    private fun atualizarNotificacoes(eventos: List<EventoViagemEntity>) {
        val idsDoFilho = viewModel.paradasDoFilho.value.map { it.id }.toSet()

        eventos
            .filter { it.tipo == "CHEGADA" || it.tipo == "EMBARQUE" }
            .filter { it.ocorridoEm >= inicioObservacaoMs }
            .filter { it.id !in eventosNotificados }
            .filter { it.paradaViagemId in idsDoFilho }
            .forEach { evento ->
                eventosNotificados.add(evento.id)
                val mensagem = if (evento.tipo == "CHEGADA") "O motorista chegou!" else "Seu filho embarcou!"
                Snackbar.make(findViewById(android.R.id.content), mensagem, Snackbar.LENGTH_LONG).show()
            }
    }

    private fun atualizarStatusViagem(viagem: ViagemEntity?) {
        txtStatusViagem.text = when (viagem?.status) {
            null -> "Nenhuma viagem em andamento agora."
            ViagemRepository.STATUS_EM_ANDAMENTO ->
                if (aCaminhoDaEscola) "A caminho da escola" else "Viagem em andamento"
            ViagemRepository.STATUS_PAUSADA -> "Viagem pausada"
            ViagemRepository.STATUS_FINALIZADA -> "Viagem finalizada"
            else -> "Viagem cancelada"
        }
    }

    /** Depois que todo mundo já embarcou (nenhuma parada de embarque mais
     * pendente) e o próximo ponto da rota é a escola, troca o texto genérico
     * de status por "A caminho da escola". */
    private fun atualizarStatusGeral(todasParadas: List<ParadaViagemEntity>) {
        val proximaGeral = todasParadas
            .filter {
                it.status == ViagemRepository.PARADA_AGUARDANDO ||
                    it.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE
            }
            .minByOrNull { it.ordem }

        aCaminhoDaEscola = proximaGeral != null &&
            proximaGeral.localId == "escola" &&
            proximaGeral.tipo == ViagemRepository.TIPO_DESEMBARQUE

        atualizarStatusViagem(viewModel.viagem.value)
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

    /** Retorna null quando as duas paradas já estiverem resolvidas (filho já
     * desembarcou, ou tem falta avisada e nasceu com as duas já resolvidas)
     * — sem isso, o código assumia que "embarque resolvido" sempre significa
     * "a caminho do desembarque", o que não vale mais pra um passageiro
     * ausente. */
    private fun paradaPendente(paradas: List<ParadaViagemEntity>): ParadaViagemEntity? {
        val embarque = paradas.firstOrNull { it.tipo == ViagemRepository.TIPO_EMBARQUE }
        val desembarque = paradas.firstOrNull { it.tipo == ViagemRepository.TIPO_DESEMBARQUE }
        return when {
            embarque != null && embarque.status == ViagemRepository.PARADA_AGUARDANDO -> embarque
            desembarque != null && desembarque.status == ViagemRepository.PARADA_AGUARDANDO_DESEMBARQUE -> desembarque
            else -> null
        }
    }

    private fun atualizarEndereco(paradas: List<ParadaViagemEntity>) {
        val pendente = paradaPendente(paradas) ?: return

        txtEndereco.text = "Indo para: ${pendente.endereco.ifBlank { pendente.nomeLocal }}"

        Glide.with(fotoAluno)
            .load(pendente.fotoPassageiroUrl)
            .placeholder(R.drawable.ic_perfil)
            .error(R.drawable.ic_perfil)
            .circleCrop()
            .into(fotoAluno)
    }

    /** ETA sem custo novo: o ao vivo do Navigation SDK do motorista quando
     * publicado, senão o minuto estimado gravado no início da viagem — ver
     * [calcularEta]. */
    private fun atualizarEta(paradas: List<ParadaViagemEntity>) {
        val pendente = paradaPendente(paradas)
        txtEta.text = calcularEta(pendente)?.let { "Chegada estimada: $it" }
        txtEta.visibility = if (txtEta.text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /** Último ponto da viagem (ordem mais alta) — a escola na ida, ou a
     * última casa na volta. É o "ponto de desembarque final"/"horário de
     * chegada final" mostrados no sheet, diferente do ponto pendente do
     * próprio filho (que pode ser um ponto anterior, se ele não for o
     * último da rota). */
    private fun atualizarDesembarqueFinal(todasParadas: List<ParadaViagemEntity>) {
        val ultima = todasParadas.maxByOrNull { it.ordem }

        txtDesembarqueFinal.text = ultima?.let {
            "Desembarque final: ${it.nomeLocal.ifBlank { it.endereco }}"
        }
        txtEtaFinal.text = calcularEta(ultima)?.let { "Chegada final estimada: $it" }
        txtEtaFinal.visibility = if (txtEtaFinal.text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun calcularEta(parada: ParadaViagemEntity?): String? {
        val viagem = viewModel.viagem.value
        if (parada == null || viagem == null || viagem.iniciadaEm <= 0L) return null

        // Preferência: ETA ao vivo (com trânsito) que o Navigation SDK do
        // motorista publica junto da posição. Sem ele, cai na estimativa fixa
        // gravada no início da viagem.
        val etaAoVivo = viewModel.localizacaoMotorista.value
            ?.etasParadas
            ?.let { EtaParadasUtil.decodificar(it)[parada.ordem] }

        val etaMillis = etaAoVivo ?: (viagem.iniciadaEm + parada.horarioEstimadoMinutos * 60_000L)
        val formato = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
        return formato.format(etaMillis)
    }

    // =========================================================
    // CHAT COM O MOTORISTA
    // =========================================================

    /** Espelha ListaRotasFragment.abrirChatComMotorista — o chat só existe
     * pra quem tem uma solicitação ACEITA pra essa rota/passageiro. */
    private fun abrirChat() {
        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid

        if (motoristaId.isBlank() || passageiroId.isBlank() || responsavelId == null) {
            Toast.makeText(this, "Motorista não encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val db = VaivanDatabase.getInstance(this@AcompanharViagemActivity)
                val solicitacao = SolicitacaoInclusaoRepository(
                    this@AcompanharViagemActivity,
                    db.solicitacaoInclusaoDao()
                ).buscarAceita(responsavelId, rotaId, passageiroId)

                if (solicitacao == null) {
                    Toast.makeText(
                        this@AcompanharViagemActivity,
                        "Solicitação aprovada não encontrada",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                startActivity(
                    Intent(this@AcompanharViagemActivity, ChatActivity::class.java).apply {
                        putExtra("outroUsuarioId", motoristaId)
                        putExtra("nomeContato", motoristaNome.ifBlank { "Motorista" })
                        putExtra("solicitacaoId", solicitacao.id)
                        putExtra("mensagemInicial", solicitacao.mensagem)
                        putExtra("mensagemInicialEm", solicitacao.criadoEm)
                        putExtra("rotaId", rotaId)
                        putExtra("passageiroId", passageiroId)
                        putExtra("passageiroNome", alunoNome)
                        putExtra("rotaNome", rotaNome)
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@AcompanharViagemActivity,
                    "Não foi possível abrir o chat",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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

        // Crop inicial fechado nos dois pontos do filho (não no trajeto
        // inteiro, que pode ser bem mais largo que a área que importa aqui)
        // — o responsável pode dar zoom out manualmente se quiser ver mais.
        val boundsBuilder = LatLngBounds.Builder()
            .include(pontoEmbarque)
            .include(pontoDesembarque)

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
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(ponto)
                    .icon(icone)
                    .anchor(0.5f, ANCHOR_Y_FOTO_NOME)
            )
            if (parada.tipo == ViagemRepository.TIPO_EMBARQUE) {
                marcadorEmbarqueFilho = marker
            }
        }
    }

    /** Some com o marcador do ponto de embarque do filho assim que a parada
     * deixar de estar pendente — embarcou, não embarcou, ou falta avisada,
     * qualquer status que não seja mais "aguardando". */
    private fun atualizarMarcadorEmbarqueFilho(paradas: List<ParadaViagemEntity>) {
        val embarque = paradas.firstOrNull { it.tipo == ViagemRepository.TIPO_EMBARQUE } ?: return
        if (embarque.status != ViagemRepository.PARADA_AGUARDANDO) {
            marcadorEmbarqueFilho?.remove()
            marcadorEmbarqueFilho = null
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
        val indiceAnterior = indiceNaRotaDaUltimaPosicao
        val indiceNovo = atualizarProgressoRota(novaPosicao)
        indiceNaRotaDaUltimaPosicao = indiceNovo

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
            if (autoRecentralizarAtivo) recentralizarCamera(novaPosicao)
            return
        }

        val posicaoAnterior = marcadorExistente.position
        if (posicaoAnterior.latitude == novaPosicao.latitude &&
            posicaoAnterior.longitude == novaPosicao.longitude
        ) {
            return
        }

        // Em cima da rota nas duas posições: anima seguindo as ruas (pelos
        // pontos da polyline entre uma e outra) em vez de cortar o quarteirão.
        // Fora da rota (desvio ainda sem rota nova): linha reta, como antes.
        val caminho: List<Pair<Double, Double>> =
            if (indiceAnterior != null && indiceNovo != null && indiceNovo >= indiceAnterior) {
                buildList {
                    add(posicaoAnterior.latitude to posicaoAnterior.longitude)
                    addAll(trajetoRota.subList(indiceAnterior + 1, indiceNovo + 1))
                    add(novaPosicao.latitude to novaPosicao.longitude)
                }
            } else {
                listOf(
                    posicaoAnterior.latitude to posicaoAnterior.longitude,
                    novaPosicao.latitude to novaPosicao.longitude
                )
            }

        marcadorExistente.rotation = calcularBearing(posicaoAnterior, novaPosicao)

        animacaoMarcador?.cancel()
        animacaoMarcador = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = duracaoAnimacaoMs
            addUpdateListener { animador ->
                val fracao = (animador.animatedValue as Float).toDouble()
                val (lat, lng) = ProgressoRotaUtil.pontoNaFracao(caminho, fracao)
                val posicao = LatLng(lat, lng)

                // Gira junto com a curva da rua
                val (latFrente, lngFrente) = ProgressoRotaUtil.pontoNaFracao(caminho, fracao + 0.02)
                if (latFrente != lat || lngFrente != lng) {
                    marcadorExistente.rotation = calcularBearing(posicao, LatLng(latFrente, lngFrente))
                }
                marcadorExistente.position = posicao
            }
            start()
        }

        if (autoRecentralizarAtivo) recentralizarCamera(novaPosicao)
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

    /** Tamanho fixo do ícone da van no mapa — `van_branco_visto_de_cima.png` é
     * uma imagem de alta resolução (1254×1254px); sem isso, o tamanho
     * "intrínseco" do drawable (escalado pela densidade da tela) faz o
     * marcador aparecer enorme. */
    private val tamanhoIconeVanDp = 50

    private fun criarIconeVan(cor: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(this, R.drawable.van_branco_visto_de_cima)
            ?: throw IllegalStateException("Não foi possível carregar o van_branco_visto_de_cima.")

        val tamanhoPx = (tamanhoIconeVanDp * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(tamanhoPx, tamanhoPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // =========================================================
    // RECENTRALIZAÇÃO AUTOMÁTICA DA CÂMERA (van + próximo ponto do filho)
    // =========================================================

    /** Enquadra a van e o próximo ponto que importa pro filho (embarque, ou
     * a escola depois que ele já embarcou — [paradaPendente] já resolve essa
     * troca). Chamada a cada atualização de localização enquanto
     * [autoRecentralizarAtivo], e uma vez de imediato ao retomar pelo FAB. */
    private fun recentralizarCamera(posicaoVan: LatLng) {
        val pendente = paradaPendente(viewModel.paradasDoFilho.value)

        if (pendente == null) {
            animarCameraComFallback(CameraUpdateFactory.newLatLngZoom(posicaoVan, 17.5f))
            return
        }

        val bounds = LatLngBounds.Builder()
            .include(posicaoVan)
            .include(LatLng(pendente.latitude, pendente.longitude))
            .build()

        animarCameraComFallback(CameraUpdateFactory.newLatLngBounds(bounds, 120))
    }

    /** `newLatLngBounds` pode lançar IllegalStateException se o mapa ainda
     * não tiver layout — mesmo padrão defensivo de VisualizarRotaActivity.enquadrar. */
    private fun animarCameraComFallback(update: CameraUpdate) {
        try {
            mMap.animateCamera(update, duracaoAnimacaoMs.toInt(), null)
        } catch (e: IllegalStateException) {
            mMap.setOnMapLoadedCallback {
                mMap.animateCamera(update, duracaoAnimacaoMs.toInt(), null)
            }
        }
    }

    override fun onDestroy() {
        listenerAvisosAusencia?.remove()
        listenerAvisosAusencia = null
        animacaoMarcador?.cancel()
        animacaoMarcador = null
        marcadorMotorista?.remove()
        marcadorMotorista = null
        super.onDestroy()
    }
}
