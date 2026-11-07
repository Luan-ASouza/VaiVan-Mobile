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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.google.android.material.imageview.ShapeableImageView
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.models.RotasDoResponsavel
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.domain.usecase.rota.BuscarRotasDoResponsavelUseCase
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.ui.comum.chat.ChatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ListaRotasFragment : Fragment() {

    private lateinit var containerRotas: LinearLayout

    private var ultimasRotas: List<RotasDoResponsavel> = emptyList()
    private var ultimasSolicitacoes: List<SolicitacaoInclusaoEntity> = emptyList()
    private var listenerSolicitacoes: ListenerRegistration? = null

    private val solicitacaoRepository: SolicitacaoInclusaoRepository by lazy {
        SolicitacaoInclusaoRepository(
            requireContext(),
            VaivanDatabase.getInstance(requireContext()).solicitacaoInclusaoDao()
        )
    }

    private val passageiroRepository: PassageiroRepository by lazy {
        PassageiroRepository(VaivanDatabase.getInstance(requireContext()).passageiroDao())
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
        observarRotas()
        carregarRotas()
    }

    override fun onDestroyView() {
        listenerSolicitacoes?.remove()
        listenerSolicitacoes = null
        super.onDestroyView()
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
        val nomeAluno = item.parada?.nomePassageiro.orEmpty()

        // Aluno
        val txtInicialAluno = view.findViewById<TextView>(R.id.txtInicialAlunoCard)
        val imgFotoAluno = view.findViewById<ShapeableImageView>(R.id.imgFotoAlunoCard)

        txtInicialAluno.text =
            nomeAluno.firstOrNull()?.uppercase() ?: "?"

        view.findViewById<TextView>(R.id.txtNomeAlunoCard).text =
            nomeAluno.ifBlank { "Aluno não informado" }

        val passageiroId = item.parada?.passageiroId
        if (passageiroId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val passageiro = try {
                    passageiroRepository.consultarPassageiro(passageiroId)
                } catch (e: Exception) {
                    null
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

        // Ações dentro do card (os botões são opcionais: se ainda não
        // existirem no XML, o código simplesmente os ignora)
        view.findViewById<View>(R.id.btnAcompanharViagemCard)?.setOnClickListener {
            abrirAcompanharViagem(item)
        }

        view.findViewById<View>(R.id.btnEnviarMensagemCard)?.setOnClickListener {
            abrirChatComMotorista(item)
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