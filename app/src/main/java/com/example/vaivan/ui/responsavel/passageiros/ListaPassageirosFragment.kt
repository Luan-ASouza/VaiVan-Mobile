package com.example.vaivan.ui.responsavel.passageiros

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.ui.responsavel.passageiros.perfil.PerfilPassageiroActivity
import com.example.vaivan.core.util.DataUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.ui.responsavel.HomeResponsavelActivity
import com.example.vaivan.ui.responsavel.rotas.PesquisarRotasFragment
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.domain.usecase.solicitacao.CancelarSolicitacaoUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.example.vaivan.data.models.Mensalidade
import com.example.vaivan.data.models.StatusMensalidade
import com.example.vaivan.data.repository.MensalidadeRepository
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.example.vaivan.ui.responsavel.mensalidades.MensalidadesResponsavelActivity

class ListaPassageirosFragment : Fragment() {

    private lateinit var containerConfirmados: LinearLayout
    private lateinit var txtSemPassageiros: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    // Pedidos de vaga do responsável (para saber se cada filho já tem rota)
    private var ultimosPassageiros: List<PassageiroEntity> = emptyList()
    private var ultimasSolicitacoes: List<SolicitacaoInclusaoEntity> = emptyList()
    private var listenerSolicitacoes: ListenerRegistration? = null

    // Rotas já consultadas (rotaId -> escola/turno), para os quadrinhos do card
    private val rotasConsultadas = mutableMapOf<String, RotaResumo>()
    private val rotasCarregando = mutableSetOf<String>()

    // Viagens feitas por filho (passageiroId -> total); null = ainda carregando/erro
    private var viagensPorPassageiro: Map<String, Int>? = null

    // Mensalidades (para mostrar atraso no cartão, mesmo sem rota ativa)
    private var ultimasMensalidades: List<Mensalidade> = emptyList()

    // Reservas cujo prazo de pagamento já venceu e estão sendo liberadas (uma vez por reserva)
    private val reservasExpirando = mutableSetOf<String>()

    private val viagemRepository: ViagemRepository by lazy {
        val db = VaivanDatabase.getInstance(requireContext())
        ViagemRepository(db.viagemDao(), db.paradaViagemDao(), db.eventoViagemDao())
    }

    private val solicitacaoRepository: SolicitacaoInclusaoRepository by lazy {
        SolicitacaoInclusaoRepository(
            requireContext().applicationContext,
            VaivanDatabase.getInstance(requireContext()).solicitacaoInclusaoDao()
        )
    }

    private val cancelarSolicitacao: CancelarSolicitacaoUseCase by lazy {
        CancelarSolicitacaoUseCase(
            VaivanDatabase.getInstance(requireContext()).solicitacaoInclusaoDao()
        )
    }

    private val viewModel: ListaPassageirosViewModel by viewModels {
        viewModelFactory {
            initializer {
                val database = VaivanDatabase.getInstance(requireContext())
                ListaPassageirosViewModel(
                    PassageiroRepository(
                        passageiroDao = database.passageiroDao(),
                        paradaRotaDao = database.paradaRotaDao(),
                        solicitacaoInclusaoDao = database.solicitacaoInclusaoDao()
                    )
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_lista_passageiros,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        configurarViews(view)
        configurarBotoes()
        observarPassageiros()
        observarSolicitacoes()
        observarMensalidades()
        carregarViagensFeitas()
    }

    /** Atrasos continuam aparecendo no cartão do filho, mesmo depois de cancelar a rota. */
    private fun observarMensalidades() {
        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MensalidadeRepository().observarMensalidadesDoResponsavel(responsavelId).collect { lista ->
                    ultimasMensalidades = lista
                    renderizarLista(ultimosPassageiros)
                }
            }
        }
    }

    /** Soma das mensalidades vencidas e não pagas deste filho (em qualquer rota). */
    private fun valorEmAtraso(passageiroId: String): Long {
        val agora = System.currentTimeMillis()
        val reservas = ultimasSolicitacoes.filter { it.status == STATUS_RESERVADA }.map { it.id }.toSet()
        return ultimasMensalidades
            .filter { it.passageiroId == passageiroId }
            .filter { it.contratoId !in reservas }
            .filter { MensalidadePolicy.situacao(it, agora) == MensalidadePolicy.SituacaoMensalidade.ATRASADA }
            .sumOf { it.valorCentavos }
    }

    /** Busca (uma vez por abertura / puxar para atualizar) quantas viagens cada filho já fez. */
    private fun carregarViagensFeitas() {
        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            viagensPorPassageiro = try {
                viagemRepository.consultarViagensFeitasPorPassageiro(responsavelId)
            } catch (e: Exception) {
                null
            }
            renderizarLista(ultimosPassageiros)
        }
    }

    override fun onDestroyView() {
        listenerSolicitacoes?.remove()
        listenerSolicitacoes = null
        super.onDestroyView()
    }

    /** Mantém os pedidos de vaga atualizados (o motorista pode aceitar a qualquer momento). */
    private fun observarSolicitacoes() {
        val responsavelId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        listenerSolicitacoes = solicitacaoRepository.iniciarSincronizacaoDoResponsavel(responsavelId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                solicitacaoRepository.observarPorResponsavel(responsavelId).collect { lista ->
                    ultimasSolicitacoes = lista
                    renderizarLista(ultimosPassageiros)
                    expirarReservasVencidas(lista)
                }
            }
        }
    }

    private fun configurarViews(view: View) {
        containerConfirmados = view.findViewById(R.id.containerConfirmados)
        txtSemPassageiros = view.findViewById(R.id.txtSemPassageiros)

        swipeRefresh = view.findViewById(R.id.swipeRefreshPassageiros)
        swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    viewModel.atualizarPassageiros()
                    carregarViagensFeitas()
                } finally {
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun configurarBotoes() {
        val btnNovoPassageiro = view?.findViewById<LinearLayout>(R.id.btnNovoPassageiro) ?: return

        btnNovoPassageiro.setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    AdicionarPassageiroActivity::class.java
                )
            )
        }
    }

    private fun observarPassageiros() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.passageiros.collect { passageiros ->
                    renderizarLista(passageiros)
                }
            }
        }
    }

    private fun renderizarLista(passageiros: List<PassageiroEntity>) {
        ultimosPassageiros = passageiros
        containerConfirmados.removeAllViews()

        if (passageiros.isEmpty()) {
            txtSemPassageiros.visibility = View.VISIBLE
            return
        }

        txtSemPassageiros.visibility = View.GONE

        val inflater = LayoutInflater.from(requireContext())

        for (passageiro in passageiros) {
            val itemView = inflater.inflate(
                R.layout.item_passageiro,
                containerConfirmados,
                false
            )

            configurarItemPassageiro(itemView, passageiro)
            containerConfirmados.addView(itemView)
        }
    }

    private fun configurarItemPassageiro(
        itemView: View,
        passageiro: PassageiroEntity
    ) {
        val imgFoto = itemView.findViewById<ShapeableImageView>(R.id.imgFotoPassageiroItem)
        val txtNome = itemView.findViewById<TextView>(R.id.txtNomePassageiroItem)
        val txtIdade = itemView.findViewById<TextView>(R.id.txtIdadePassageiroItem)
        val btnEditar = itemView.findViewById<ImageView>(R.id.btnEditarPassageiroItem)

        txtNome.text = passageiro.nome

        Glide.with(imgFoto)
            .load(passageiro.fotoUrl)
            .placeholder(R.drawable.avatar_01)
            .error(R.drawable.avatar_01)
            .circleCrop()
            .into(imgFoto)

        val idade = DataUtil.calcularIdade(passageiro.dataNascimento)
        txtIdade.text = if (idade >= 0) {
            getString(R.string.idade_anos, idade)
        } else {
            getString(R.string.idade_indisponivel)
        }

        // Editar: abre o perfil do passageiro (cada campo tem sua tela)
        btnEditar.setOnClickListener {
            startActivity(PerfilPassageiroActivity.intent(requireContext(), passageiro.id))
        }

        configurarNumeros(itemView, passageiro)
        configurarRotaAtual(itemView, passageiro)
    }

    // ---------------------------------------------------------
    // QUADRINHOS (VIAGENS, ROTAS, ESCOLA, TURNO)
    // ---------------------------------------------------------

    private fun configurarNumeros(itemView: View, passageiro: PassageiroEntity) {
        val viagens = viagensPorPassageiro?.get(passageiro.id) ?: 0
        itemView.findViewById<TextView>(R.id.txtViagensPassageiroItem).text =
            if (viagensPorPassageiro == null) "–" else viagens.toString()
        itemView.findViewById<TextView>(R.id.txtRotuloViagensPassageiroItem).text =
            if (viagens == 1) "Viagem" else "Viagens"

        // Ponto de embarque: o escolhido no pedido/rota atual do filho
        val ativa = solicitacaoAtiva(passageiro.id)
        itemView.findViewById<TextView>(R.id.txtEmbarquePassageiroItem).text =
            ativa?.nomeLocalEmbarque?.takeIf { it.isNotBlank() } ?: "Sem embarque"
        itemView.findViewById<TextView>(R.id.txtEnderecoEmbarquePassageiroItem).text =
            ativa?.enderecoEmbarque?.takeIf { it.isNotBlank() } ?: "Definido ao buscar uma rota"

        // Escola e turno vêm da rota atual (aceita ou pedida) do filho
        val rota = ativa?.let { ativa ->
            rotasConsultadas[ativa.rotaId] ?: run {
                carregarRota(ativa.rotaId)
                null
            }
        }

        itemView.findViewById<TextView>(R.id.txtEscolaPassageiroItem).text =
            rota?.escola?.takeIf { it.isNotBlank() } ?: "Não definida"

        val txtTurno = itemView.findViewById<TextView>(R.id.txtTurnoPassageiroItem)
        val imgTurno = itemView.findViewById<ImageView>(R.id.imgTurnoPassageiroItem)
        txtTurno.text = when (rota?.turno) {
            "MANHA" -> "Turno da manhã"
            "TARDE" -> "Turno da tarde"
            "NOITE" -> "Turno da noite"
            else -> "Turno não definido"
        }
        imgTurno.setImageResource(if (rota?.turno == "NOITE") R.drawable.ic_lua else R.drawable.ic_sol)
    }

    // ---------------------------------------------------------
    // PEDIDO / ROTA ATUAL DO FILHO
    // ---------------------------------------------------------

    /**
     * O pedido, a reserva ou a rota aceita deste filho, se houver
     * (prioridade: aceita > reservada aguardando pagamento > pendente).
     */
    private fun solicitacaoAtiva(passageiroId: String): SolicitacaoInclusaoEntity? {
        val doFilho = ultimasSolicitacoes.filter { it.passageiroId == passageiroId }
        return doFilho.filter { it.status == STATUS_ACEITA }.maxByOrNull { it.criadoEm }
            ?: doFilho.filter { it.status == STATUS_RESERVADA }.maxByOrNull { it.criadoEm }
            ?: doFilho.filter { it.status == STATUS_PENDENTE }.maxByOrNull { it.criadoEm }
    }

    /**
     * Sem pedido nem rota: o botão vira "Buscar rotas para o passageiro".
     * Com pedido pendente, reserva ou rota aceita: "Ver rotas do passageiro" leva
     * para a aba Transportes, e abaixo aparece a situação (etiqueta, rota, valor,
     * pagar a 1ª mensalidade quando reservada) com a opção de cancelar.
     * A faixa de atraso aparece em qualquer caso, enquanto houver mensalidade
     * vencida e não paga.
     */
    private fun configurarRotaAtual(itemView: View, passageiro: PassageiroEntity) {
        configurarPendencia(itemView, passageiro)

        val btnRotas = itemView.findViewById<View>(R.id.btnRotasPassageiroItem)
        val txtBotao = itemView.findViewById<TextView>(R.id.txtBotaoRotasPassageiroItem)
        val imgBotao = itemView.findViewById<ImageView>(R.id.imgBotaoRotasPassageiroItem)
        val containerStatus = itemView.findViewById<View>(R.id.containerStatusRotaItem)
        val ativa = solicitacaoAtiva(passageiro.id)

        if (ativa == null) {
            txtBotao.text = "Buscar rotas para o passageiro"
            imgBotao.setImageResource(R.drawable.ic_search)
            btnRotas.setOnClickListener { abrirPesquisaRotas(passageiro) }
            containerStatus.visibility = View.GONE
            return
        }

        txtBotao.text = "Ver rotas do passageiro"
        imgBotao.setImageResource(R.drawable.ic_local)
        btnRotas.setOnClickListener { (activity as? HomeResponsavelActivity)?.abrirTransportes() }

        containerStatus.visibility = View.VISIBLE

        itemView.findViewById<TextView>(R.id.txtRotaAtualNomeItem).text =
            rotasConsultadas[ativa.rotaId]?.nome?.takeIf { it.isNotBlank() } ?: "Carregando rota..."

        val valorMensal =
            if (ativa.valorMensalCentavos > 0) "${AssinaturaUi.formatarValor(ativa.valorMensalCentavos)} por mês"
            else "Valor a combinar"

        val btnPagar = itemView.findViewById<View>(R.id.btnPagarReservaItem)
        btnPagar.visibility = View.GONE

        // Etiqueta, texto e botão de cancelar conforme a etapa
        val etapa = when (ativa.status) {
            STATUS_ACEITA -> Etapa("Ativa", COR_ATIVA, "Vai de van com esta rota. $valorMensal", "Cancelar rota")

            STATUS_RESERVADA -> {
                val primeira = primeiraMensalidade(ativa.id)
                val prazo = ativa.respondidoEm?.let {
                    AssinaturaUi.formatarData(MensalidadePolicy.prazoPrimeiroPagamento(it))
                }
                if (primeira?.status == StatusMensalidade.PAGA) {
                    Etapa(
                        "Pago", COR_ATIVA,
                        "Pagamento confirmado! A vaga é ativada assim que o motorista abrir a rota ou iniciar a próxima viagem.",
                        "Cancelar reserva"
                    )
                } else {
                    btnPagar.visibility = View.VISIBLE
                    btnPagar.setOnClickListener {
                        startActivity(
                            Intent(requireContext(), MensalidadesResponsavelActivity::class.java)
                                .putExtra(MensalidadesResponsavelActivity.EXTRA_PAGAR_CONTRATO_ID, ativa.id)
                        )
                    }
                    val valorPrimeira = primeira?.let { AssinaturaUi.formatarValor(it.valorCentavos) }
                    Etapa(
                        "Reservada", COR_AGUARDANDO,
                        "O motorista aceitou! Pague " +
                            (valorPrimeira?.let { "$it (1ª mensalidade, proporcional ao mês)" } ?: "a 1ª mensalidade") +
                            (prazo?.let { " até $it" } ?: "") +
                            " para ativar a vaga. Depois: $valorMensal.",
                        "Cancelar reserva"
                    )
                }
            }

            else -> Etapa("Aguardando", COR_AGUARDANDO, "Esperando o motorista aceitar. $valorMensal", "Cancelar pedido")
        }

        itemView.findViewById<TextView>(R.id.txtStatusRotaItem).apply {
            text = etapa.etiqueta
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, etapa.cores.first))
            setTextColor(ContextCompat.getColor(context, etapa.cores.second))
        }
        itemView.findViewById<TextView>(R.id.txtRotaAtualInfoItem).text = etapa.info

        itemView.findViewById<TextView>(R.id.btnCancelarRotaItem).apply {
            text = etapa.textoCancelar
            setOnClickListener { confirmarCancelamento(passageiro, ativa) }
        }
    }

    /** Como o quadro do filho aparece em cada etapa (cores = fundo e texto da etiqueta). */
    private data class Etapa(
        val etiqueta: String,
        val cores: Pair<Int, Int>,
        val info: String,
        val textoCancelar: String
    )

    /** 1ª mensalidade de uma reserva (o id do contrato é o da solicitação). */
    private fun primeiraMensalidade(solicitacaoId: String): Mensalidade? =
        ultimasMensalidades
            .filter { it.contratoId == solicitacaoId && it.status != StatusMensalidade.CANCELADA }
            .minByOrNull { it.competencia }

    /** Reserva com prazo de pagamento vencido: libera a vaga (uma vez por reserva). */
    private fun expirarReservasVencidas(lista: List<SolicitacaoInclusaoEntity>) {
        val agora = System.currentTimeMillis()
        lista
            .filter { it.status == STATUS_RESERVADA }
            .filter { MensalidadePolicy.reservaExpirada(it.respondidoEm ?: 0L, agora) }
            .filter { primeiraMensalidade(it.id)?.status != StatusMensalidade.PAGA }
            .filter { reservasExpirando.add(it.id) }
            .forEach { reserva ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        if (cancelarSolicitacao.expirarReserva(reserva.id)) {
                            Toast.makeText(
                                requireContext(),
                                "A reserva de ${reserva.nomePassageiro} expirou: a 1ª mensalidade não foi paga no prazo.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        reservasExpirando.remove(reserva.id) // tenta de novo depois
                    }
                }
            }
    }

    private fun configurarPendencia(itemView: View, passageiro: PassageiroEntity) {
        val container = itemView.findViewById<LinearLayout>(R.id.containerPendenciaItem)
        val atraso = valorEmAtraso(passageiro.id)

        if (atraso <= 0) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        itemView.findViewById<TextView>(R.id.txtPendenciaValorItem).text =
            "${AssinaturaUi.formatarValor(atraso)} em aberto"
        itemView.findViewById<View>(R.id.btnPagarPendenciaItem).setOnClickListener {
            startActivity(Intent(requireContext(), MensalidadesResponsavelActivity::class.java))
        }
    }

    /** Busca escola/turno da rota uma vez só e redesenha a lista quando chegar. */
    private fun carregarRota(rotaId: String) {
        if (rotaId.isBlank() || !rotasCarregando.add(rotaId)) return

        viewLifecycleOwner.lifecycleScope.launch {
            val documento = try {
                FirebaseFirestore.getInstance().collection("rotas").document(rotaId)
                    .get().await()
            } catch (e: Exception) {
                null
            }
            rotasConsultadas[rotaId] = RotaResumo(
                nome = documento?.getString("nome")?.takeIf { it.isNotBlank() } ?: "Rota",
                escola = documento?.getString("escolaNome")?.takeIf { it.isNotBlank() }
                    ?: documento?.getString("destinoNome").orEmpty(),
                turno = documento?.getString("turno").orEmpty()
            )
            renderizarLista(ultimosPassageiros)
        }
    }

    private data class RotaResumo(val nome: String, val escola: String, val turno: String)

    private fun confirmarCancelamento(passageiro: PassageiroEntity, solicitacao: SolicitacaoInclusaoEntity) {
        val nome = passageiro.nome
        val aceita = solicitacao.status == STATUS_ACEITA

        val atraso = valorEmAtraso(passageiro.id)
        val avisoAtraso = if (atraso > 0) {
            "\n\n$nome tem ${AssinaturaUi.formatarValor(atraso)} em mensalidades atrasadas. " +
                "Esse valor continua devido ao motorista e pode ser pago na tela Mensalidades."
        } else {
            ""
        }

        val reservada = solicitacao.status == STATUS_RESERVADA
        val pagou = primeiraMensalidade(solicitacao.id)?.status == StatusMensalidade.PAGA

        val (titulo, mensagem, botao) = if (reservada) {
            Triple(
                "Cancelar a reserva de $nome?",
                "A vaga reservada fica livre para outro aluno." +
                    if (pagou) "\n\nO valor já pago não é devolvido." else " A 1ª mensalidade em aberto é cancelada.",
                "Cancelar reserva"
            )
        } else if (aceita) {
            Triple(
                "Cancelar a rota de $nome?",
                "$nome deixa de ir com esta van e a vaga fica livre para outro aluno. " +
                    "As mensalidades que ainda não venceram são canceladas.\n\n" +
                    "Valores já pagos não são devolvidos." + avisoAtraso,
                "Cancelar rota"
            )
        } else {
            Triple(
                "Cancelar o pedido de $nome?",
                "O motorista não poderá mais aceitar este pedido. Depois, você pode buscar outra rota para $nome.",
                "Cancelar pedido"
            )
        }

        AlertDialog.Builder(requireContext())
            .setTitle(titulo)
            .setMessage(mensagem)
            .setPositiveButton(botao) { _, _ -> cancelar(solicitacao, aceita) }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private fun cancelar(solicitacao: SolicitacaoInclusaoEntity, aceita: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                cancelarSolicitacao(solicitacao.id)
                Toast.makeText(
                    requireContext(),
                    when (solicitacao.status) {
                        STATUS_ACEITA -> "Rota cancelada. Você já pode buscar outra."
                        STATUS_RESERVADA -> "Reserva cancelada. Você já pode buscar outra."
                        else -> "Pedido cancelado."
                    },
                    Toast.LENGTH_SHORT
                ).show()
            } catch (erro: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Não foi possível cancelar: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun abrirPesquisaRotas(passageiro: PassageiroEntity) {
        val fragment = PesquisarRotasFragment()

        fragment.arguments = Bundle().apply {
            putString("passageiroId", passageiro.id)
            putString("nomePassageiro", passageiro.nome)
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private companion object {
        const val STATUS_PENDENTE = SolicitacaoInclusaoRepository.STATUS_PENDENTE
        const val STATUS_ACEITA = SolicitacaoInclusaoRepository.STATUS_ACEITA
        const val STATUS_RESERVADA = SolicitacaoInclusaoRepository.STATUS_AGUARDANDO_PAGAMENTO

        // Etiqueta da situação (fundo, texto) — mesmas classes dos cards de Transportes
        val COR_ATIVA = R.color.transporte_andamento_fundo to R.color.transporte_andamento_texto
        val COR_AGUARDANDO = R.color.transporte_programada_fundo to R.color.transporte_programada_texto
    }
}
