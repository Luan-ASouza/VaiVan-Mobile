package com.example.vaivan.ui.responsavel.rotas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.google.android.material.imageview.ShapeableImageView
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.core.util.configurarBlocoAvisoAusencia
import com.example.vaivan.data.models.RotasDoResponsavel
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.domain.StatusPassageiro
import com.example.vaivan.domain.statusDoPassageiro
import com.example.vaivan.domain.usecase.ausencia.RegistrarAvisoAusenciaUseCase
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.domain.usecase.rota.BuscarRotasDoResponsavelUseCase
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.ui.comum.chat.ChatActivity
import com.example.vaivan.ui.motorista.rotas.VisualizarRotaActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ListaRotasFragment : Fragment() {

    private lateinit var containerRotas: LinearLayout

    private lateinit var swipeRefresh: SwipeRefreshLayout

    private var ultimasRotas: List<RotasDoResponsavel> = emptyList()
    private var ultimasSolicitacoes: List<SolicitacaoInclusaoEntity> = emptyList()
    private var listenerSolicitacoes: ListenerRegistration? = null
    private var listenerAvisosAusencia: ListenerRegistration? = null

    /** Status de viagem/passageiro dos cards (ver atualizarStatusViagemCard):
     * limpos e refeitos a cada renderizar(), já que esta tela reconstrói
     * todos os cards do zero a cada emissão. */
    private val listenersStatusViagem = mutableListOf<ListenerRegistration>()
    private val viagemIdsSincronizados = mutableSetOf<String>()

    private val viagemRepository: ViagemRepository by lazy {
        val db = VaivanDatabase.getInstance(requireContext())
        ViagemRepository(db.viagemDao(), db.paradaViagemDao(), db.eventoViagemDao())
    }

    private val solicitacaoRepository: SolicitacaoInclusaoRepository by lazy {
        SolicitacaoInclusaoRepository(
            requireContext(),
            VaivanDatabase.getInstance(requireContext()).solicitacaoInclusaoDao()
        )
    }

    private val passageiroRepository: PassageiroRepository by lazy {
        PassageiroRepository(VaivanDatabase.getInstance(requireContext()).passageiroDao())
    }

    private val avisoAusenciaRepository: AvisoAusenciaRepository by lazy {
        AvisoAusenciaRepository(VaivanDatabase.getInstance(requireContext()).avisoAusenciaDao())
    }

    private val registrarAvisoAusenciaUseCase: RegistrarAvisoAusenciaUseCase by lazy {
        RegistrarAvisoAusenciaUseCase(avisoAusenciaRepository, viagemRepository)
    }

    private val viewModel: ListaRotasViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(requireContext())

                val useCase = BuscarRotasDoResponsavelUseCase(
                    passageiroRepository = PassageiroRepository(db.passageiroDao()),
                    rotaRepository = RotaRepository(
                        db.rotaDao(),
                        db.paradaRotaDao(),
                        GoogleRoutesClient(requireContext())
                    ),
                    usuarioRepository = UsuarioRepository(db.usuarioDao()),
                    veiculoRepository = VeiculoRepository(db.veiculoDao())
                )

                ListaRotasViewModel(useCase)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_lista_rotas,
            container,
            false
        )

        containerRotas = view.findViewById(R.id.containerListaRotas)
        swipeRefresh = view.findViewById(R.id.swipeRefreshListaRotas)
        swipeRefresh.setOnRefreshListener { atualizarTudo() }

        return view
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        // O botão genérico "Acompanhar viagem" foi removido da tela.
        // Agora ele fica dentro de cada card (ver preencherRota).

        iniciarSolicitacoes()
        iniciarAvisosAusencia()
        observarRotas()
        carregarRotas()
    }

    override fun onDestroyView() {
        listenerSolicitacoes?.remove()
        listenerSolicitacoes = null
        listenerAvisosAusencia?.remove()
        listenerAvisosAusencia = null
        pararStatusViagemDosCards()
        super.onDestroyView()
    }

    /** Desliga os listeners/sincronizações de status abertos pelos cards
     * atuais — chamado antes de reconstruir tudo em renderizar() e de novo
     * em onDestroyView(), já que esta tela recria os cards do zero a cada
     * emissão (ver renderizar()). */
    private fun pararStatusViagemDosCards() {
        listenersStatusViagem.forEach { it.remove() }
        listenersStatusViagem.clear()
        viagemIdsSincronizados.forEach { viagemRepository.pararSincronizacao(it) }
        viagemIdsSincronizados.clear()
    }

    /** Pull-to-refresh: força uma releitura da lista de rotas (consulta direta
     * no Firestore, não confia no cache) — solicitações e avisos de ausência
     * já chegam em tempo real pelos listeners, não precisam de ação aqui.
     * Desliga o spinner direto ao terminar (try/finally), sem depender da
     * emissão do StateFlow — ver comentário em ListaRotasViewModel.atualizarRotas. */
    private fun atualizarTudo() {
        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid
        if (responsavelId == null) {
            swipeRefresh.isRefreshing = false
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.atualizarRotas(responsavelId)
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    // ---------------------------------------------------------
    // SOLICITAÇÕES (Aguardando, Aprovada, Recusada)
    // ---------------------------------------------------------

    private fun iniciarSolicitacoes() {

        val responsavelId = FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid
            ?: return

        // Mantém o Room deste celular atualizado com o que o motorista respondeu
        listenerSolicitacoes = solicitacaoRepository
            .iniciarSincronizacaoDoResponsavel(responsavelId)

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                solicitacaoRepository
                    .observarPorResponsavel(responsavelId)
                    .collect { lista ->
                        ultimasSolicitacoes = lista
                        renderizar()
                    }
            }
        }
    }

    /** Mantém o Room deste celular atualizado com o que acontece no aviso
     * depois de criado (motorista consumindo ao iniciar viagem, ou o próprio
     * responsável cancelando em outro dispositivo) — sem isso, o Room só
     * reflete o estado do momento em que o aviso foi salvo, nunca mais. */
    private fun iniciarAvisosAusencia() {
        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        listenerAvisosAusencia = avisoAusenciaRepository
            .iniciarSincronizacaoDoResponsavel(responsavelId)
    }

    private fun rotuloStatus(status: String): String =
        when (status) {
            "ACEITA" -> "Aprovada"
            "RECUSADA" -> "Recusada"
            else -> "Aguardando"
        }

    private fun preencherSolicitacao(
        view: View,
        solicitacao: SolicitacaoInclusaoEntity
    ) {

        view.findViewById<TextView>(R.id.txtNomeAlunoSolicitacaoResp).text =
            solicitacao.nomePassageiro

        val imgFotoAluno = view.findViewById<ShapeableImageView>(R.id.imgFotoAlunoSolicitacaoResp)

        viewLifecycleOwner.lifecycleScope.launch {
            val passageiro = try {
                passageiroRepository.consultarPassageiro(solicitacao.passageiroId)
            } catch (e: Exception) {
                null
            }

            Glide.with(imgFotoAluno)
                .load(passageiro?.fotoUrl)
                .placeholder(R.drawable.filho1)
                .error(R.drawable.filho1)
                .circleCrop()
                .into(imgFotoAluno)
        }

        view.findViewById<TextView>(R.id.txtStatusSolicitacaoResp).text =
            rotuloStatus(solicitacao.status)

        view.findViewById<TextView>(R.id.txtEnderecoSolicitacaoResp).text =
            solicitacao.enderecoEmbarque

        val mensagem = solicitacao.mensagem.trim()
        val txtMensagem = view.findViewById<TextView>(R.id.txtMensagemSolicitacaoResp)

        if (mensagem.isBlank()) {
            txtMensagem.visibility = View.GONE
        } else {
            txtMensagem.visibility = View.VISIBLE
            txtMensagem.text = "Seu pedido: $mensagem"
        }

        // O chat só aparece para solicitações aprovadas
        val btnChat = view.findViewById<View>(R.id.btnChatSolicitacaoResp)

        if (solicitacao.status == "ACEITA") {
            btnChat.visibility = View.VISIBLE
            btnChat.setOnClickListener { abrirChatDaSolicitacao(solicitacao) }
        } else {
            btnChat.visibility = View.GONE
        }
    }

    private fun abrirChatDaSolicitacao(solicitacao: SolicitacaoInclusaoEntity) {

        startActivity(
            Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("outroUsuarioId", solicitacao.motoristaId)
                putExtra("nomeContato", "Motorista")
                putExtra("solicitacaoId", solicitacao.id)
                putExtra("mensagemInicial", solicitacao.mensagem)
                putExtra("mensagemInicialEm", solicitacao.criadoEm)

                // Cada filho e cada rota têm a sua própria conversa
                putExtra("rotaId", solicitacao.rotaId)
                putExtra("passageiroId", solicitacao.passageiroId)
                putExtra("passageiroNome", solicitacao.nomePassageiro)
            }
        )
    }

    private fun carregarRotas() {

        val responsavelId = FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid
            ?: return

        viewModel.carregarRotas(responsavelId)
    }

    private fun observarRotas() {

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.rotas.collect { rotas ->
                    ultimasRotas = rotas
                    renderizar()
                }
            }
        }
    }

    /** Desenha, nesta ordem: seção Solicitações e seção Suas viagens. */
    private fun renderizar() {

        pararStatusViagemDosCards()
        containerRotas.removeAllViews()

        // Só as que ainda aguardam resposta. As aprovadas já viram cards em
        // "Suas viagens" (com o botão de mensagem) e as antigas/recusadas poluem a tela.
        val solicitacoes = ultimasSolicitacoes.filter { it.status == "PENDENTE" }

        if (solicitacoes.isNotEmpty()) {

            containerRotas.addView(criarTituloSecao("Solicitações"))

            solicitacoes.forEach { solicitacao ->

                val solicitacaoView = layoutInflater.inflate(
                    R.layout.item_solicitacao_responsavel,
                    containerRotas,
                    false
                )

                preencherSolicitacao(solicitacaoView, solicitacao)

                containerRotas.addView(solicitacaoView)
            }
        }

        if (ultimasRotas.isNotEmpty()) {

            containerRotas.addView(criarTituloSecao("Suas viagens"))

            mostrarRotas(ultimasRotas)
        }
    }

    private fun criarTituloSecao(titulo: String): TextView {

        val dp = resources.displayMetrics.density

        return TextView(requireContext()).apply {
            text = titulo
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (8 * dp).toInt(), 0, (10 * dp).toInt())
        }
    }

    private fun mostrarRotas(rotas: List<RotasDoResponsavel>) {

        rotas.forEach { item ->

            val rotaView = layoutInflater.inflate(
                R.layout.item_rota_responsavel,
                containerRotas,
                false
            )

            preencherRota(rotaView, item)

            containerRotas.addView(rotaView)
        }
    }

    // ---------------------------------------------------------
    // CARD DE VIAGEM (um por filho)
    // ---------------------------------------------------------

    private fun preencherRota(
        view: View,
        item: RotasDoResponsavel
    ) {

        val rota = item.rota
        val motorista = item.motorista
        val veiculo = item.veiculo
        // Nome denormalizado em ParadaRotaEntity no momento em que o
        // passageiro entrou na rota — só um fallback pra não mostrar em
        // branco enquanto a busca abaixo não volta; se o nome foi editado
        // depois, essa cópia fica desatualizada (ver passageiroRepository
        // .consultarPassageiro abaixo, que traz o nome atual de verdade).
        val nomeAlunoDenormalizado = item.parada?.nomePassageiro.orEmpty()

        // Aluno
        val txtInicialAluno = view.findViewById<TextView>(R.id.txtInicialAlunoCard)
        val imgFotoAluno = view.findViewById<ShapeableImageView>(R.id.imgFotoAlunoCard)
        val txtNomeAluno = view.findViewById<TextView>(R.id.txtNomeAlunoCard)

        txtInicialAluno.text =
            nomeAlunoDenormalizado.firstOrNull()?.uppercase() ?: "?"

        txtNomeAluno.text =
            nomeAlunoDenormalizado.ifBlank { "Aluno não informado" }

        val passageiroId = item.parada?.passageiroId
        var nomeAluno = nomeAlunoDenormalizado

        if (passageiroId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val passageiro = try {
                    passageiroRepository.consultarPassageiro(passageiroId)
                } catch (e: Exception) {
                    null
                }

                val nomeAtual = passageiro?.nome?.ifBlank { null }
                if (nomeAtual != null) {
                    nomeAluno = nomeAtual
                    txtInicialAluno.text = nomeAtual.firstOrNull()?.uppercase() ?: "?"
                    txtNomeAluno.text = nomeAtual
                }

                if (!passageiro?.fotoUrl.isNullOrBlank()) {
                    txtInicialAluno.visibility = View.GONE
                    imgFotoAluno.visibility = View.VISIBLE
                    Glide.with(imgFotoAluno).load(passageiro?.fotoUrl).circleCrop().into(imgFotoAluno)
                }
            }
        }

        // Rota, turno e desembarque
        view.findViewById<TextView>(R.id.txtNomeRotaCard).text = rota.nome

        view.findViewById<TextView>(R.id.txtTurnoRotaCard).text = rota.turno

        view.findViewById<TextView>(R.id.txtDesembarqueCard).text =
            rota.destinoEndereco

        // Dias da semana (um chip por dia)
        montarDias(
            view.findViewById(R.id.containerDiasCard),
            rota.diasSemana
        )

        // Motorista e veículo
        val nomeMotorista = motorista?.nome ?: "Motorista não informado"

        val dadosVeiculo = listOfNotNull(veiculo?.modelo, veiculo?.placa)
            .joinToString(" • ")

        view.findViewById<TextView>(R.id.txtMotoristaCard).text =
            if (dadosVeiculo.isBlank()) nomeMotorista
            else "$nomeMotorista  |  $dadosVeiculo"

        // Tipo, turno e embarque
        view.findViewById<TextView>(R.id.txtTipoRotaCard).text =
            when (rota.tipoTrajeto) {
                "IDA" -> "Ida"
                "VOLTA" -> "Volta"
                "IDA_E_VOLTA" -> "Ida e volta"
                else -> rota.tipoTrajeto
            }

        view.findViewById<TextView>(R.id.txtTurnoRotaCard).text =
            when (rota.turno) {
                "MANHA" -> "Manhã"
                "TARDE" -> "Tarde"
                "NOITE" -> "Noite"
                else -> rota.turno
            }

        view.findViewById<TextView>(R.id.txtEmbarqueCard).text =
            item.parada?.endereco?.ifBlank { null } ?: "Não informado"

        view.findViewById<View>(R.id.txtNotaVoltaCard).visibility =
            if (rota.tipoTrajeto == "IDA_E_VOLTA") View.VISIBLE else View.GONE

        // TODO: txtProximaCobrancaCard (depende da regra de cobrança mensal)

        val containerStatusViagem = view.findViewById<ViewGroup>(R.id.containerStatusViagemCard)
        val btnAcompanhar = view.findViewById<MaterialButton>(R.id.btnAcompanharViagemCard)
        if (passageiroId != null && containerStatusViagem != null && btnAcompanhar != null) {
            configurarStatusViagemCard(containerStatusViagem, btnAcompanhar, item, passageiroId)
        }

        // Ações dentro do card (os botões são opcionais: se ainda não
        // existirem no XML, o código simplesmente os ignora)
        view.findViewById<View>(R.id.btnEnviarMensagemCard)?.setOnClickListener {
            abrirChatComMotorista(item)
        }

        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid
        val containerAviso = view.findViewById<ViewGroup>(R.id.containerAvisoAusenciaCard)
        if (passageiroId != null && responsavelId != null && containerAviso != null) {
            configurarBlocoAvisoAusencia(
                container = containerAviso,
                lifecycleOwner = viewLifecycleOwner,
                repository = avisoAusenciaRepository,
                registrarAvisoUseCase = registrarAvisoAusenciaUseCase,
                viagemRepository = viagemRepository,
                rotaId = rota.id,
                passageiroId = passageiroId,
                nomePassageiro = nomeAluno,
                responsavelId = responsavelId,
                motoristaId = rota.motoristaId,
                rotaIdaEVolta = rota.tipoTrajeto == "IDA_E_VOLTA"
            )
        }
    }

    private fun abrirAcompanharViagem(item: RotasDoResponsavel) {

        startActivity(
            Intent(requireContext(), AcompanharViagemActivity::class.java).apply {
                putExtra(AcompanharViagemActivity.EXTRA_ROTA_ID, item.rota.id)
                putExtra(AcompanharViagemActivity.EXTRA_ALUNO_NOME, item.parada?.nomePassageiro)
                putExtra(AcompanharViagemActivity.EXTRA_PASSAGEIRO_ID, item.parada?.passageiroId)
                putExtra(AcompanharViagemActivity.EXTRA_ROTA_POLYLINE, item.rota.polylineEncoded)
                putExtra(AcompanharViagemActivity.EXTRA_ROTA_NOME, item.rota.nome)
                putExtra(AcompanharViagemActivity.EXTRA_MOTORISTA_NOME, item.motorista?.nome)
                putExtra(AcompanharViagemActivity.EXTRA_VEICULO_PLACA, item.veiculo?.placa)
                putExtra(AcompanharViagemActivity.EXTRA_MOTORISTA_FOTO_URL, item.motorista?.fotoUrl)
                putExtra(AcompanharViagemActivity.EXTRA_MOTORISTA_ID, item.rota.motoristaId)
                putExtra(AcompanharViagemActivity.EXTRA_ROTA_IDA_E_VOLTA, item.rota.tipoTrajeto == "IDA_E_VOLTA")
            }
        )
    }

    private fun abrirChatComMotorista(item: RotasDoResponsavel) {

        val motoristaId = item.rota.motoristaId
        val passageiroId = item.parada?.passageiroId
        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid

        if (motoristaId.isBlank() || passageiroId.isNullOrBlank() || responsavelId == null) {
            Toast.makeText(requireContext(), "Motorista não encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val db = VaivanDatabase.getInstance(requireContext())

                // O chat só existe para quem foi aprovado: acha a solicitação ACEITA
                val solicitacao = SolicitacaoInclusaoRepository(
                    requireContext(),
                    db.solicitacaoInclusaoDao()
                ).buscarAceita(responsavelId, item.rota.id, passageiroId)

                if (solicitacao == null) {
                    Toast.makeText(
                        requireContext(),
                        "Solicitação aprovada não encontrada",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                startActivity(
                    Intent(requireContext(), ChatActivity::class.java).apply {
                        putExtra("outroUsuarioId", motoristaId)
                        putExtra("nomeContato", item.motorista?.nome ?: "Motorista")
                        putExtra("solicitacaoId", solicitacao.id)
                        putExtra("mensagemInicial", solicitacao.mensagem)
                        putExtra("mensagemInicialEm", solicitacao.criadoEm)

                        // Cada filho e cada rota têm a sua própria conversa
                        putExtra("rotaId", item.rota.id)
                        putExtra("passageiroId", passageiroId)
                        putExtra("passageiroNome", item.parada?.nomePassageiro.orEmpty())
                        putExtra("rotaNome", item.rota.nome)
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Não foi possível abrir o chat",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ---------------------------------------------------------
    // STATUS DA VIAGEM/PASSAGEIRO DE HOJE (um por direção da rota)
    // ---------------------------------------------------------

    /** Um TextView por direção que a rota tiver, mostrando se a viagem de
     * hoje ainda não começou, está em andamento (com o status do próprio
     * filho: aguardando/na van/desceu), ou já foi finalizada — atualizado ao
     * vivo via [ViagemRepository.iniciarSincronizacaoDoDia]. O mesmo estado
     * decide o botão único do card: "Acompanhar viagem" (ao vivo) enquanto
     * alguma direção estiver em andamento, "Ver trajeto" (só o percurso
     * planejado, sem navegação) no resto do tempo. */
    private fun configurarStatusViagemCard(
        container: ViewGroup,
        btnAcompanhar: MaterialButton,
        item: RotasDoResponsavel,
        passageiroId: String
    ) {
        val rota = item.rota
        container.removeAllViews()

        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val direcoes = when (rota.tipoTrajeto) {
            "IDA" -> listOf(ViagemRepository.DIRECAO_IDA)
            "VOLTA" -> listOf(ViagemRepository.DIRECAO_VOLTA)
            "IDA_E_VOLTA" -> listOf(ViagemRepository.DIRECAO_IDA, ViagemRepository.DIRECAO_VOLTA)
            else -> emptyList()
        }

        val mostrarPrefixo = direcoes.size > 1
        val ativoPorDirecao = BooleanArray(direcoes.size)

        fun atualizarBotaoAcompanhar() {
            if (ativoPorDirecao.any { it }) {
                btnAcompanhar.text = "Acompanhar viagem"
                btnAcompanhar.setIconResource(R.drawable.ic_mylocation)
                btnAcompanhar.setOnClickListener { abrirAcompanharViagem(item) }
            } else {
                btnAcompanhar.text = "Ver trajeto"
                btnAcompanhar.setIconResource(R.drawable.ic_rotas)
                btnAcompanhar.setOnClickListener { abrirVerTrajeto(rota.id) }
            }
        }

        atualizarBotaoAcompanhar()

        direcoes.forEachIndexed { index, direcao ->
            val txtStatus = criarTextoStatusViagem(container.context)
            container.addView(txtStatus)

            listenersStatusViagem.add(viagemRepository.iniciarSincronizacaoDoDia(rota.id, direcao, responsavelId))

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    statusViagemDoDiaFlow(rota.id, passageiroId, responsavelId, direcao).collect { (viagem, statusPassageiro) ->
                        ativoPorDirecao[index] = viagem != null &&
                            (viagem.status == ViagemRepository.STATUS_EM_ANDAMENTO || viagem.status == ViagemRepository.STATUS_PAUSADA)
                        atualizarBotaoAcompanhar()

                        val (texto, cor) = rotuloEcorStatusViagem(viagem, statusPassageiro)
                        txtStatus.text = if (mostrarPrefixo) {
                            val prefixo = if (direcao == ViagemRepository.DIRECAO_IDA) "Ida" else "Volta"
                            "$prefixo: $texto"
                        } else {
                            texto
                        }
                        txtStatus.setTextColor(ContextCompat.getColor(requireContext(), cor))
                    }
                }
            }
        }
    }

    private fun abrirVerTrajeto(rotaId: String) {
        startActivity(
            Intent(requireContext(), VisualizarRotaActivity::class.java)
                .putExtra(VisualizarRotaActivity.EXTRA_ROTA_ID, rotaId)
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun statusViagemDoDiaFlow(
        rotaId: String,
        passageiroId: String,
        responsavelId: String,
        direcao: String
    ): Flow<Pair<ViagemEntity?, StatusPassageiro?>> =
        viagemRepository.observarUltimaViagemDeHoje(rotaId, direcao)
            .flatMapLatest { viagem ->
                val emAndamento = viagem != null &&
                    (viagem.status == ViagemRepository.STATUS_EM_ANDAMENTO || viagem.status == ViagemRepository.STATUS_PAUSADA)

                if (viagem != null && emAndamento) {
                    viagemRepository.iniciarSincronizacao(viagem.id, responsavelId)
                    viagemIdsSincronizados.add(viagem.id)

                    viagemRepository.observarParadas(viagem.id).map { paradas ->
                        val embarque = paradas.firstOrNull {
                            it.passageiroId == passageiroId && it.tipo == ViagemRepository.TIPO_EMBARQUE
                        }
                        val desembarque = paradas.firstOrNull {
                            it.passageiroId == passageiroId && it.tipo == ViagemRepository.TIPO_DESEMBARQUE
                        }
                        val status = if (embarque != null && desembarque != null) {
                            statusDoPassageiro(embarque, desembarque)
                        } else {
                            null
                        }
                        viagem to status
                    }
                } else {
                    flowOf(viagem to null)
                }
            }

    private fun rotuloEcorStatusViagem(
        viagem: ViagemEntity?,
        statusPassageiro: StatusPassageiro?
    ): Pair<String, Int> = when {
        viagem == null || viagem.status == ViagemRepository.STATUS_CANCELADA ->
            "Ainda não iniciada hoje" to R.color.light_gray

        viagem.status == ViagemRepository.STATUS_FINALIZADA ->
            "Finalizada hoje" to R.color.status_success

        else ->
            (statusPassageiro?.texto ?: "Em andamento") to (statusPassageiro?.cor ?: R.color.status_success)
    }

    private fun criarTextoStatusViagem(context: android.content.Context): TextView {
        val dp = context.resources.displayMetrics.density
        return TextView(context).apply {
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
        }
    }

    private fun montarDias(
        container: LinearLayout,
        diasSemana: String
    ) {

        container.removeAllViews()

        val ctx = container.context
        val dp = ctx.resources.displayMetrics.density
        val margem = (6 * dp).toInt()
        val padH = (10 * dp).toInt()
        val padV = (4 * dp).toInt()

        diasSemana
            .split(Regex("[,;/\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { dia ->

                val chip = TextView(ctx).apply {
                    text = dia.take(3).replaceFirstChar { it.uppercase() }
                    textSize = 12f
                    setPadding(padH, padV, padH, padV)
                    setBackgroundResource(R.drawable.bg_beige_round_20)
                }

                container.addView(
                    chip,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = margem }
                )
            }
    }
}