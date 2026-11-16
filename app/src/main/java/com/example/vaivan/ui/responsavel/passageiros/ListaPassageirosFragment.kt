package com.example.vaivan.ui.responsavel.passageiros

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.core.util.DataUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.ui.responsavel.rotas.PesquisarRotasFragment
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import android.content.res.ColorStateList
import androidx.appcompat.app.AlertDialog
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.domain.usecase.solicitacao.CancelarSolicitacaoUseCase
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import androidx.core.content.ContextCompat
import com.example.vaivan.data.models.Mensalidade
import com.example.vaivan.data.models.StatusMensalidade
import com.example.vaivan.data.repository.MensalidadeRepository
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.example.vaivan.ui.responsavel.mensalidades.MensalidadesResponsavelActivity

class ListaPassageirosFragment : Fragment() {

    private lateinit var containerConfirmados: LinearLayout
    private lateinit var txtSemPassageiros: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    // Pedidos de vaga do responsável (para saber se cada filho já tem rota)
    private var ultimosPassageiros: List<PassageiroEntity> = emptyList()
    private var ultimasSolicitacoes: List<SolicitacaoInclusaoEntity> = emptyList()
    private var listenerSolicitacoes: ListenerRegistration? = null

    // Mensalidades (para mostrar atraso no cartão, mesmo sem rota ativa)
    private var ultimasMensalidades: List<Mensalidade> = emptyList()

    // Nome das rotas já consultadas (rotaId -> nome)
    private val nomesDasRotas = mutableMapOf<String, String>()
    private val rotasCarregando = mutableSetOf<String>()

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
        val linhaPrincipal = itemView.findViewById<LinearLayout>(R.id.linhaPrincipalItem)
        val imgFoto = itemView.findViewById<ShapeableImageView>(R.id.imgFotoPassageiroItem)
        val txtNome = itemView.findViewById<TextView>(R.id.txtNomePassageiroItem)
        val txtIdade = itemView.findViewById<TextView>(R.id.txtIdadePassageiroItem)
        val imgEditar = itemView.findViewById<ImageView>(R.id.imgEditarPassageiroItem)
        val imgExcluir = itemView.findViewById<ImageView>(R.id.imgExcluirPassageiroItem)
        val imgChevron = itemView.findViewById<ImageView>(R.id.imgChevronItem)
        val containerDetalhes = itemView.findViewById<LinearLayout>(R.id.containerDetalhesItem)
        val txtNecessidade = itemView.findViewById<TextView>(R.id.txtNecessidadePassageiroItem)
        val txtObservacoes = itemView.findViewById<TextView>(R.id.txtObservacoesPassageiroItem)
        val btnBuscarRota = itemView.findViewById<Button>(R.id.btnBuscarRotaItem)

        // Informações Principais usando String Resources
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

        // Necessidade Especial
        if (passageiro.necessidadesEspeciais) {
            txtNecessidade.visibility = View.VISIBLE
            txtNecessidade.text = if (!passageiro.descricaoNecessidades.isNullOrBlank()) {
                getString(R.string.necessidade_especial_descricao, passageiro.descricaoNecessidades)
            } else {
                getString(R.string.necessidade_especial_padrao)
            }
        } else {
            txtNecessidade.visibility = View.GONE
        }

        // Observações
        if (!passageiro.observacoes.isNullOrBlank()) {
            txtObservacoes.visibility = View.VISIBLE
            txtObservacoes.text = getString(R.string.observacoes_descricao, passageiro.observacoes)
        } else {
            txtObservacoes.visibility = View.GONE
        }

        // Expansão do Card
        val temDetalhes = passageiro.necessidadesEspeciais || !passageiro.observacoes.isNullOrBlank()

        imgChevron.visibility = if (temDetalhes) View.VISIBLE else View.INVISIBLE

        linhaPrincipal.setOnClickListener {
            if (!temDetalhes) return@setOnClickListener

            val vaiExpandir = containerDetalhes.visibility != View.VISIBLE

            containerDetalhes.visibility = if (vaiExpandir) View.VISIBLE else View.GONE

            imgChevron.animate()
                .rotation(if (vaiExpandir) 180f else 0f)
                .setDuration(150)
                .start()
        }

        // Buscar Rota: só para quem não tem pedido pendente nem rota aceita
        btnBuscarRota.setOnClickListener {
            abrirPesquisaRotas(passageiro)
        }
        configurarRotaAtual(itemView, passageiro, btnBuscarRota)

        // Editar: abre o perfil do passageiro (cada campo tem sua tela)
        imgEditar.setOnClickListener {
            startActivity(PerfilPassageiroActivity.intent(requireContext(), passageiro.id))
        }

        // Excluir
        imgExcluir.setOnClickListener {
            ConfirmacaoExclusaoHelper.confirmar(
                contexto = requireContext(),
                titulo = "Excluir \"${passageiro.nome}\"?",
                mensagem = "Isso remove o passageiro e sua vaga em qualquer rota. Essa ação não pode ser desfeita."
            ) {
                excluirPassageiro(passageiro)
            }
        }
    }

    // ---------------------------------------------------------
    // PEDIDO / ROTA ATUAL DO FILHO
    // ---------------------------------------------------------

    /** O pedido pendente ou a rota aceita deste filho, se houver. */
    private fun solicitacaoAtiva(passageiroId: String): SolicitacaoInclusaoEntity? =
        ultimasSolicitacoes
            .filter { it.passageiroId == passageiroId && it.status in SolicitacaoInclusaoRepository.STATUS_ATIVOS }
            .maxByOrNull { it.criadoEm }

    /**
     * Com pedido pendente ou rota aceita, o "Buscar rota" some e aparece a rota
     * (ou o pedido) com a etiqueta de situação e o botão de cancelar.
     * Sem nada ativo, volta o "Buscar rota". A faixa de atraso aparece em
     * qualquer caso, enquanto houver mensalidade vencida e não paga.
     */
    private fun configurarRotaAtual(itemView: View, passageiro: PassageiroEntity, btnBuscarRota: View) {
        configurarPendencia(itemView, passageiro)

        val container = itemView.findViewById<LinearLayout>(R.id.containerRotaAtualItem)
        val ativa = solicitacaoAtiva(passageiro.id)

        if (ativa == null) {
            btnBuscarRota.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        btnBuscarRota.visibility = View.GONE
        container.visibility = View.VISIBLE

        itemView.findViewById<TextView>(R.id.txtRotaAtualNomeItem).text =
            nomesDasRotas[ativa.rotaId] ?: run {
                carregarNomeDaRota(ativa.rotaId)
                "Carregando rota..."
            }

        val valorMensal =
            if (ativa.valorMensalCentavos > 0) "${AssinaturaUi.formatarValor(ativa.valorMensalCentavos)} por mês"
            else "Valor a combinar"

        val btnPagar = itemView.findViewById<View>(R.id.btnPagarReservaItem)
        btnPagar.visibility = View.GONE

        // Etiqueta, texto e botão de cancelar conforme a etapa
        val (etiqueta, corEtiqueta, info, textoCancelar) = when (ativa.status) {
            STATUS_ACEITA -> Etapa("Ativa", R.color.green, valorMensal, "Cancelar rota")

            STATUS_RESERVADA -> {
                val primeira = primeiraMensalidade(ativa.id)
                val prazo = ativa.respondidoEm?.let {
                    AssinaturaUi.formatarData(MensalidadePolicy.prazoPrimeiroPagamento(it))
                }
                if (primeira?.status == StatusMensalidade.PAGA) {
                    Etapa(
                        "Pago", R.color.green,
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
                        "Reservada", R.color.orange,
                        "O motorista aceitou! Pague " +
                            (valorPrimeira?.let { "$it (1ª mensalidade, proporcional ao mês)" } ?: "a 1ª mensalidade") +
                            (prazo?.let { " até $it" } ?: "") +
                            " para ativar a vaga. Depois: $valorMensal.",
                        "Cancelar reserva"
                    )
                }
            }

            else -> Etapa("Aguardando", R.color.orange, "Esperando o motorista aceitar. $valorMensal", "Cancelar pedido")
        }

        itemView.findViewById<TextView>(R.id.txtStatusRotaItem).apply {
            text = etiqueta
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, corEtiqueta))
            setTextColor(
                ContextCompat.getColor(context, if (corEtiqueta == R.color.green) R.color.white else R.color.dark_gray)
            )
        }
        itemView.findViewById<TextView>(R.id.txtRotaAtualInfoItem).text = info

        itemView.findViewById<TextView>(R.id.btnCancelarRotaItem).apply {
            text = textoCancelar
            setOnClickListener { confirmarCancelamento(passageiro, ativa) }
        }
    }

    /** Como o quadro do filho aparece em cada etapa. */
    private data class Etapa(val etiqueta: String, val cor: Int, val info: String, val textoCancelar: String)

    /** 1ª mensalidade de uma reserva (o id do contrato é o da solicitação). */
    private fun primeiraMensalidade(solicitacaoId: String): Mensalidade? =
        ultimasMensalidades
            .filter { it.contratoId == solicitacaoId && it.status != StatusMensalidade.CANCELADA }
            .minByOrNull { it.competencia }

    /** Reserva com prazo de pagamento vencido: libera a vaga (uma vez por reserva). */
    private val reservasExpirando = mutableSetOf<String>()

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

    /** Busca o nome da rota uma vez só e redesenha a lista quando chegar. */
    private fun carregarNomeDaRota(rotaId: String) {
        if (rotaId.isBlank() || !rotasCarregando.add(rotaId)) return

        viewLifecycleOwner.lifecycleScope.launch {
            val nome = try {
                FirebaseFirestore.getInstance().collection("rotas").document(rotaId)
                    .get().await().getString("nome")
            } catch (e: Exception) {
                null
            }
            nomesDasRotas[rotaId] = nome?.takeIf { it.isNotBlank() } ?: "Rota"
            renderizarLista(ultimosPassageiros)
        }
    }

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

    private fun excluirPassageiro(passageiro: PassageiroEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.excluirPassageiro(passageiro.id)

                Toast.makeText(requireContext(), "Passageiro excluído.", Toast.LENGTH_SHORT).show()

            } catch (erro: Exception) {
                Toast.makeText(requireContext(), "Erro ao excluir: ${erro.message}", Toast.LENGTH_LONG).show()
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
    }
}
