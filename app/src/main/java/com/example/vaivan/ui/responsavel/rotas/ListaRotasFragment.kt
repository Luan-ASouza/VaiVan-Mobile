package com.example.vaivan.ui.responsavel.rotas

import com.example.vaivan.core.util.DataUtil
import android.content.Intent
import android.content.res.ColorStateList
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
    private lateinit var abaViagens: TextView
    private lateinit var abaPedidos: TextView
    private lateinit var txtVazio: TextView

    /** Aba aberta: viagens dos filhos (padrão) ou pedidos aguardando o motorista. */
    private var mostrandoPedidos = false

    /** Em que ponto da viagem de hoje o filho está (linha do tempo do card). */
    private enum class Etapa { NAO_INICIADA, AGUARDANDO, NA_VAN, DESCEU, FALTA, FINALIZADA }

    private var ultimasRotas: List<RotasDoResponsavel> = emptyList()
    private val listenersRotas = mutableListOf<ListenerRegistration>()
    private var rotasObservadas: List<String> = emptyList()
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

        abaViagens = view.findViewById(R.id.abaViagensRotas)
        abaPedidos = view.findViewById(R.id.abaPedidosRotas)
        txtVazio = view.findViewById(R.id.txtVazioRotas)

        abaViagens.setOnClickListener { mostrandoPedidos = false; renderizar() }
        abaPedidos.setOnClickListener { mostrandoPedidos = true; renderizar() }

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
        listenersRotas.forEach { it.remove() }
        listenersRotas.clear()
        rotasObservadas = emptyList()
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
            "AGUARDANDO_PAGAMENTO" -> "Vaga reservada: pague a 1ª mensalidade"
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
                passageiroRepository.consultarPassageiroComCache(solicitacao.passageiroId)
            } catch (e: Exception) {
                null
            }

            Glide.with(imgFotoAluno)
                .load(passageiro?.fotoUrl)
                .placeholder(R.drawable.avatar_01)
                .error(R.drawable.avatar_01)
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
                    observarMudancasDasRotas(rotas)
                }
            }
        }
    }

    /** Quando o motorista recalcula a rota (novo aluno, mudança de ordem...), o
     * documento da rota muda no Firestore. Esta tela só buscava as rotas uma vez,
     * então o card ficava desatualizado até o pull-to-refresh: aqui, qualquer
     * mudança numa rota exibida recarrega a lista. */
    private fun observarMudancasDasRotas(rotas: List<RotasDoResponsavel>) {
        val ids = rotas.map { it.rota.id }.distinct()
        if (ids == rotasObservadas) return

        listenersRotas.forEach { it.remove() }
        listenersRotas.clear()
        rotasObservadas = ids

        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        ids.forEach { rotaId ->
            var primeira = true
            listenersRotas += com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("rotas")
                .document(rotaId)
                .addSnapshotListener { snapshot, erro ->
                    if (erro != null || snapshot == null) return@addSnapshotListener
                    // O primeiro disparo é só o estado atual, que já está na tela.
                    if (primeira) {
                        primeira = false
                        return@addSnapshotListener
                    }
                    if (snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                    viewModel.carregarRotas(responsavelId)
                }
        }
    }

    /** Desenha a aba aberta: "Viagens" (um card por filho) ou "Pedidos"
     * (solicitações que ainda aguardam o motorista). */
    private fun renderizar() {

        pararStatusViagemDosCards()
        containerRotas.removeAllViews()

        // Só as que ainda aguardam resposta. As aprovadas já viram cards em
        // "Viagens" (com o botão de mensagem) e as antigas/recusadas poluem a tela.
        // Pedidos ainda sem rota ativa: aguardando o motorista ou o 1º pagamento
        val solicitacoes = ultimasSolicitacoes.filter { it.status == "PENDENTE" || it.status == "AGUARDANDO_PAGAMENTO" }

        abaViagens.text = "Viagens (${ultimasRotas.size})"
        abaPedidos.text = "Pedidos (${solicitacoes.size})"
        aplicarEstiloAba(abaViagens, selecionada = !mostrandoPedidos)
        aplicarEstiloAba(abaPedidos, selecionada = mostrandoPedidos)

        if (mostrandoPedidos) {
            solicitacoes.forEach { solicitacao ->
                val solicitacaoView = layoutInflater.inflate(
                    R.layout.item_solicitacao_responsavel,
                    containerRotas,
                    false
                )
                preencherSolicitacao(solicitacaoView, solicitacao)
                containerRotas.addView(solicitacaoView)
            }
            mostrarVazio(
                solicitacoes.isEmpty(),
                "Nenhum pedido aguardando resposta.\nPara pedir uma vaga, toque em \"Buscar rotas\" no card do passageiro."
            )
        } else {
            mostrarRotas(ultimasRotas)
            mostrarVazio(
                ultimasRotas.isEmpty(),
                "Nenhum transporte ainda.\nQuando um motorista aceitar o pedido, a viagem aparece aqui."
            )
        }
    }

    private fun aplicarEstiloAba(aba: TextView, selecionada: Boolean) {
        aba.setBackgroundResource(if (selecionada) R.drawable.bg_aba_selecionada else R.drawable.bg_aba)
        aba.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (selecionada) R.color.text_on_dark else R.color.text_primary
            )
        )
    }

    private fun mostrarVazio(vazio: Boolean, texto: String) {
        txtVazio.text = texto
        txtVazio.visibility = if (vazio) View.VISIBLE else View.GONE
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

        val turno = when (rota.turno) {
            "MANHA" -> "Manhã"
            "TARDE" -> "Tarde"
            "NOITE" -> "Noite"
            else -> rota.turno
        }
        val tipo = when (rota.tipoTrajeto) {
            "IDA" -> "Ida"
            "VOLTA" -> "Volta"
            "IDA_E_VOLTA" -> "Ida e volta"
            else -> rota.tipoTrajeto
        }

        // Rota (título) e aluno • turno • tipo (subtítulo)
        view.findViewById<TextView>(R.id.txtNomeRotaCard).text = rota.nome

        val imgFotoAluno = view.findViewById<ShapeableImageView>(R.id.imgFotoAlunoCard)
        val txtNomeAluno = view.findViewById<TextView>(R.id.txtNomeAlunoCard)

        fun aplicarNomeAluno(nome: String) {
            txtNomeAluno.text = listOf(nome.ifBlank { "Aluno" }, turno, tipo)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
        }
        aplicarNomeAluno(nomeAlunoDenormalizado)

        val passageiroId = item.parada?.passageiroId
        var nomeAluno = nomeAlunoDenormalizado

        if (passageiroId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val passageiro = try {
                    passageiroRepository.consultarPassageiroComCache(passageiroId)
                } catch (e: Exception) {
                    null
                }

                passageiro?.nome?.ifBlank { null }?.let { nomeAtual ->
                    nomeAluno = nomeAtual
                    aplicarNomeAluno(nomeAtual)
                }

                Glide.with(imgFotoAluno)
                    .load(passageiro?.fotoUrl)
                    .placeholder(R.drawable.avatar_01)
                    .error(R.drawable.avatar_01)
                    .circleCrop()
                    .into(imgFotoAluno)
            }
        }

        // Endereços: embarque (casa) e desembarque (escola)
        view.findViewById<TextView>(R.id.txtEmbarqueCard).text =
            item.parada?.endereco?.ifBlank { null } ?: "Embarque não informado"
        view.findViewById<TextView>(R.id.txtDesembarqueCard).text =
            rota.destinoEndereco.ifBlank { rota.escolaNome.ifBlank { rota.destinoNome } }

        view.findViewById<View>(R.id.txtNotaVoltaCard).visibility =
            if (rota.tipoTrajeto == "IDA_E_VOLTA") View.VISIBLE else View.GONE

        // Dias da semana (um chip por dia)
        montarDias(
            view.findViewById(R.id.containerDiasCard),
            rota.diasSemana
        )

        // Motorista e veículo
        view.findViewById<TextView>(R.id.txtMotoristaCard).text =
            listOfNotNull(motorista?.nome, motorista?.sobrenome)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Motorista não informado" }

        val txtVeiculo = view.findViewById<TextView>(R.id.txtVeiculoCard)
        val dadosVeiculo = listOfNotNull(veiculo?.modelo, veiculo?.placa)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        txtVeiculo.text = dadosVeiculo
        txtVeiculo.visibility = if (dadosVeiculo.isBlank()) View.GONE else View.VISIBLE

        val imgFotoMotorista = view.findViewById<ShapeableImageView>(R.id.imgFotoMotoristaCard)
        Glide.with(imgFotoMotorista)
            .load(motorista?.fotoUrl)
            .placeholder(R.drawable.ic_perfil)
            .error(R.drawable.ic_perfil)
            .circleCrop()
            .into(imgFotoMotorista)

        view.findViewById<View>(R.id.btnEnviarMensagemCard).setOnClickListener {
            abrirChatComMotorista(item)
        }

        // Selo, linha do tempo e botão Acompanhar / Ver trajeto (ao vivo)
        if (passageiroId != null) {
            configurarStatusViagemCard(view, item, passageiroId)
        }

        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid
        val containerAviso = view.findViewById<ViewGroup>(R.id.containerAvisoAusenciaCard)
        if (passageiroId != null && responsavelId != null) {
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

    /** Acompanha ao vivo a viagem de hoje de cada direção da rota (via
     * [ViagemRepository.iniciarSincronizacaoDoDia]) e, a cada mudança,
     * redesenha no card: o selo (em andamento / programada / concluída), a
     * linha do tempo da direção mais relevante, uma linha de status por
     * direção (só em ida e volta) e o botão único — "Acompanhar" (ao vivo)
     * enquanto alguma direção estiver em andamento, "Ver trajeto" (só o
     * percurso planejado) no resto do tempo. */
    private fun configurarStatusViagemCard(
        view: View,
        item: RotasDoResponsavel,
        passageiroId: String
    ) {
        val rota = item.rota
        val container = view.findViewById<ViewGroup>(R.id.containerStatusViagemCard)
        val btnAcompanhar = view.findViewById<MaterialButton>(R.id.btnAcompanharViagemCard)
        container.removeAllViews()

        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val direcoes = when (rota.tipoTrajeto) {
            "IDA" -> listOf(ViagemRepository.DIRECAO_IDA)
            "VOLTA" -> listOf(ViagemRepository.DIRECAO_VOLTA)
            "IDA_E_VOLTA" -> listOf(ViagemRepository.DIRECAO_IDA, ViagemRepository.DIRECAO_VOLTA)
            else -> emptyList()
        }

        val idaEVolta = direcoes.size > 1
        val etapas = Array(direcoes.size) { Etapa.NAO_INICIADA }
        val rotulos = Array(direcoes.size) { "Não iniciada" }

        fun atualizarCard() {
            val ativo = etapas.any { it == Etapa.AGUARDANDO || it == Etapa.NA_VAN || it == Etapa.DESCEU }
            val algumaAtiva = etapas.indexOfFirst { it == Etapa.AGUARDANDO || it == Etapa.NA_VAN || it == Etapa.DESCEU }
            val todasConcluidas = etapas.isNotEmpty() && etapas.all { it == Etapa.FINALIZADA || it == Etapa.FALTA }

            // Selo
            val (textoSelo, fundoSelo, corSelo) = when {
                ativo -> Triple("Em andamento", R.color.transporte_andamento_fundo, R.color.transporte_andamento_texto)
                todasConcluidas -> Triple("Concluída", R.color.transporte_concluida_fundo, R.color.transporte_concluida_texto)
                else -> Triple("Programada", R.color.transporte_programada_fundo, R.color.transporte_programada_texto)
            }
            view.findViewById<TextView>(R.id.txtSeloViagemCard).apply {
                text = textoSelo
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, fundoSelo))
                setTextColor(ContextCompat.getColor(context, corSelo))
            }

            // Botão
            if (ativo) {
                btnAcompanhar.text = "Acompanhar"
                btnAcompanhar.setIconResource(R.drawable.ic_mylocation)
                btnAcompanhar.backgroundTintList = ColorStateList.valueOf(cor(R.color.color_primary))
                btnAcompanhar.setTextColor(cor(R.color.text_on_dark))
                btnAcompanhar.iconTint = ColorStateList.valueOf(cor(R.color.text_on_dark))
                btnAcompanhar.setOnClickListener { abrirAcompanharViagem(item) }
            } else {
                btnAcompanhar.text = "Ver trajeto"
                btnAcompanhar.setIconResource(R.drawable.ic_rotas)
                btnAcompanhar.backgroundTintList = ColorStateList.valueOf(cor(R.color.transporte_programada_fundo))
                btnAcompanhar.setTextColor(cor(R.color.transporte_programada_texto))
                btnAcompanhar.iconTint = ColorStateList.valueOf(cor(R.color.transporte_programada_texto))
                btnAcompanhar.setOnClickListener { abrirVerTrajeto(rota.id) }
            }

            // Linha do tempo: a direção em andamento; senão a próxima que
            // ainda não terminou; senão a última.
            if (direcoes.isEmpty()) return
            val indice = algumaAtiva.takeIf { it >= 0 }
                ?: etapas.indexOfFirst { it == Etapa.NAO_INICIADA }.takeIf { it >= 0 }
                ?: (direcoes.size - 1)
            desenharLinhaDoTempo(view, item, direcoes[indice], etapas[indice], rotulos[indice])
        }

        atualizarCard()

        direcoes.forEachIndexed { index, direcao ->
            // Em ida e volta, uma linha por direção abaixo da linha do tempo;
            // numa direção só, o rótulo do meio da linha do tempo já basta.
            val txtStatus = criarTextoStatusViagem(container.context)
            if (idaEVolta) container.addView(txtStatus)

            listenersStatusViagem.add(viagemRepository.iniciarSincronizacaoDoDia(rota.id, direcao, responsavelId))

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    statusViagemDoDiaFlow(rota.id, passageiroId, responsavelId, direcao).collect { (viagem, statusPassageiro, etapa) ->
                        val (texto, corTexto) = rotuloEcorStatusViagem(viagem, statusPassageiro)
                        etapas[index] = etapa
                        rotulos[index] = texto

                        val prefixo = if (direcao == ViagemRepository.DIRECAO_IDA) "Ida" else "Volta"
                        txtStatus.text = "$prefixo: $texto"
                        txtStatus.setTextColor(cor(corTexto))

                        atualizarCard()
                    }
                }
            }
        }
    }

    /** Pontos/linhas acendem em laranja até onde o aluno chegou; falta pinta
     * o ponto do meio de vermelho. Na volta, o trajeto se inverte. */
    private fun desenharLinhaDoTempo(
        view: View,
        item: RotasDoResponsavel,
        direcao: String,
        etapa: Etapa,
        rotulo: String
    ) {
        val casa = item.parada?.nomeLocal?.ifBlank { null } ?: "Embarque"
        val ida = direcao == ViagemRepository.DIRECAO_IDA
        view.findViewById<TextView>(R.id.txtRotuloInicioCard).text = if (ida) casa else "Escola"
        view.findViewById<TextView>(R.id.txtRotuloFimCard).text = if (ida) "Escola" else casa

        val txtAluno = view.findViewById<TextView>(R.id.txtRotuloAlunoCard)
        txtAluno.text = when (etapa) {
            Etapa.NAO_INICIADA -> "Não iniciada"
            Etapa.FINALIZADA -> "Concluída"
            else -> rotulo
        }

        val inicio = etapa != Etapa.NAO_INICIADA && etapa != Etapa.FALTA
        val meio = etapa == Etapa.NA_VAN || etapa == Etapa.DESCEU || etapa == Etapa.FINALIZADA
        val fim = etapa == Etapa.DESCEU || etapa == Etapa.FINALIZADA

        val ativa = cor(R.color.transporte_linha_ativa)
        val inativa = cor(R.color.transporte_linha_inativa)
        fun corDe(aceso: Boolean) = if (aceso) ativa else inativa

        view.findViewById<View>(R.id.pontoInicioCard).backgroundTintList = ColorStateList.valueOf(corDe(inicio))
        view.findViewById<View>(R.id.linhaInicioCard).setBackgroundColor(corDe(meio))
        view.findViewById<View>(R.id.linhaFimCard).setBackgroundColor(corDe(fim))
        view.findViewById<View>(R.id.pontoFimCard).backgroundTintList = ColorStateList.valueOf(corDe(fim))

        val corAluno = when {
            etapa == Etapa.FALTA -> cor(R.color.status_danger)
            etapa == Etapa.AGUARDANDO || meio -> ativa
            else -> inativa
        }
        view.findViewById<View>(R.id.pontoAlunoCard).backgroundTintList = ColorStateList.valueOf(corAluno)
        txtAluno.setTextColor(
            if (etapa == Etapa.NAO_INICIADA) cor(R.color.text_secondary) else corAluno
        )
    }

    private fun cor(@androidx.annotation.ColorRes id: Int): Int = ContextCompat.getColor(requireContext(), id)

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
    ): Flow<Triple<ViagemEntity?, StatusPassageiro?, Etapa>> =
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
                        // Mesma ordem de checagem de statusDoPassageiro (falta antes de "desceu")
                        val etapa = when {
                            embarque?.status == ViagemRepository.PARADA_NAO_EMBARCOU -> Etapa.FALTA
                            desembarque?.status == ViagemRepository.PARADA_DESEMBARCADO -> Etapa.DESCEU
                            embarque?.status == ViagemRepository.PARADA_EMBARCADO -> Etapa.NA_VAN
                            else -> Etapa.AGUARDANDO
                        }
                        Triple(viagem, status, etapa)
                    }
                } else {
                    val etapa =
                        if (viagem?.status == ViagemRepository.STATUS_FINALIZADA) Etapa.FINALIZADA
                        else Etapa.NAO_INICIADA
                    flowOf(Triple(viagem, null, etapa))
                }
            }

    private fun rotuloEcorStatusViagem(
        viagem: ViagemEntity?,
        statusPassageiro: StatusPassageiro?
    ): Pair<String, Int> = when {
        viagem == null || viagem.status == ViagemRepository.STATUS_CANCELADA ->
            "Ainda não iniciada hoje" to R.color.text_tertiary

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

        DataUtil.ordenarDiasSemana(diasSemana)
            .forEach { dia ->

                val chip = TextView(ctx).apply {
                    text = dia.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(ctx, R.color.passageiro_texto_chip))
                    setPadding(padH, padV, padH, padV)
                    setBackgroundResource(R.drawable.bg_chip_idade_passageiro)
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
