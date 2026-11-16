package com.example.vaivan.ui.motorista.rotas

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.vaivan.R
import com.example.vaivan.core.util.DataUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.AssinaturaBloqueadaException
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.domain.usecase.rota.AtualizarTrajetoDaRotaUseCase
import com.example.vaivan.domain.usecase.rota.CalcularOrdemVoltaUseCase
import com.example.vaivan.domain.usecase.rota.RecalcularRotaUseCase
import com.example.vaivan.domain.usecase.solicitacao.CancelarSolicitacaoUseCase
import com.example.vaivan.domain.usecase.solicitacao.ConfirmarReservasPagasUseCase
import com.example.vaivan.domain.usecase.viagem.IniciarViagemUseCase
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale

class MinhasRotasFragment : Fragment() {

    private lateinit var containerRotas: LinearLayout
    private lateinit var txtSemRotas: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var cardViagemAndamento: View
    private lateinit var txtDetalheViagemAndamento: TextView

    /** Rota cuja viagem está sendo criada agora (trava o botão do card). */
    private var rotaIniciando: String? = null

    private val viewModel: MinhasRotasViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(requireContext())
                val routesClient = GoogleRoutesClient(requireContext())
                val rotaRepository = RotaRepository(
                    rotaDao = db.rotaDao(),
                    paradaRotaDao = db.paradaRotaDao(),
                    routesClient = routesClient
                )
                val viagemRepository = ViagemRepository(db.viagemDao(), db.paradaViagemDao(), db.eventoViagemDao())
                val recalcularRotaUseCase = RecalcularRotaUseCase(rotaRepository = rotaRepository, routesClient = routesClient)

                MinhasRotasViewModel(
                    rotaRepository = rotaRepository,
                    viagemRepository = viagemRepository,
                    iniciarViagemUseCase = IniciarViagemUseCase(
                        rotaRepository = rotaRepository,
                        passageiroRepository = PassageiroRepository(db.passageiroDao()),
                        viagemRepository = viagemRepository,
                        avisoAusenciaRepository = AvisoAusenciaRepository(db.avisoAusenciaDao()),
                        calcularOrdemVolta = CalcularOrdemVoltaUseCase(routesClient)
                    ),
                    confirmarReservasPagasUseCase = ConfirmarReservasPagasUseCase(
                        solicitacaoRepository = SolicitacaoInclusaoRepository(requireContext(), db.solicitacaoInclusaoDao()),
                        recalcularRotaUseCase = recalcularRotaUseCase,
                        rotaRepository = rotaRepository,
                        cancelarSolicitacaoUseCase = CancelarSolicitacaoUseCase(db.solicitacaoInclusaoDao())
                    ),
                    atualizarTrajetoUseCase = AtualizarTrajetoDaRotaUseCase(rotaRepository, recalcularRotaUseCase)
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_minhas_rotas, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        containerRotas = view.findViewById(R.id.containerRotas)
        txtSemRotas = view.findViewById(R.id.txtSemRotas)
        cardViagemAndamento = view.findViewById(R.id.cardViagemAndamento)
        txtDetalheViagemAndamento = view.findViewById(R.id.txtDetalheViagemAndamento)

        swipeRefresh = view.findViewById(R.id.swipeRefreshMinhasRotas)
        swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    viewModel.atualizarRotas()
                } finally {
                    swipeRefresh.isRefreshing = false
                }
            }
        }

        view.findViewById<View>(R.id.btnNovaRota).setOnClickListener { abrirNovaRota() }

        observarRotas()
    }

    override fun onStart() {
        super.onStart()
        // Volta da navegação sem finalizar, ou o app foi reaberto: confere no
        // Firestore se ainda há viagem ativa pra mostrar o "Retomar viagem".
        viewModel.atualizarViagemAtiva()
    }

    private fun abrirNovaRota() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                AssinaturaRepository().exigirPodeCriarRota(uid)
                startActivity(Intent(requireContext(), CriarRotaActivity::class.java))
            } catch (e: AssinaturaBloqueadaException) {
                AssinaturaUi.mostrarBloqueio(requireContext(), e.message ?: "Assinatura necessária.")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observarRotas() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.rotas.collect { rotas ->
                        renderizar(rotas, viewModel.viagemAtiva.value)
                    }
                }
                launch {
                    viewModel.viagemAtiva.collect { viagem ->
                        renderizar(viewModel.rotas.value, viagem)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // VIAGEM EM ANDAMENTO (card no topo)
    // ---------------------------------------------------------

    /** Card no topo da lista quando há viagem em andamento/pausada — leva
     * direto pra navegação daquela viagem (retomar não exige assinatura em
     * dia, só iniciar uma nova). */
    private fun renderizarViagemAtiva(viagem: ViagemEntity?, rotas: List<RotaEntity>) {
        if (viagem == null) {
            cardViagemAndamento.visibility = View.GONE
            return
        }

        val nomeRota = rotas.firstOrNull { it.id == viagem.rotaId }?.nome ?: "Rota"
        val direcao = if (viagem.direcao == ViagemRepository.DIRECAO_VOLTA) "Volta" else "Ida"
        val pausada = if (viagem.status == ViagemRepository.STATUS_PAUSADA) " · pausada" else ""
        txtDetalheViagemAndamento.text = "$nomeRota · $direcao$pausada"

        cardViagemAndamento.visibility = View.VISIBLE
        cardViagemAndamento.findViewById<View>(R.id.btnRetomarViagem).setOnClickListener {
            abrirNavegacaoDaViagem(requireContext(), viagem.id)
        }
    }

    // ---------------------------------------------------------
    // LISTA
    // ---------------------------------------------------------

    private fun renderizar(rotas: List<RotaEntity>, viagemAtiva: ViagemEntity?) {
        renderizarViagemAtiva(viagemAtiva, rotas)
        containerRotas.removeAllViews()

        txtSemRotas.visibility = if (rotas.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(requireContext())

        rotas.forEach { rota ->
            val itemView = inflater.inflate(R.layout.item_rota_motorista, containerRotas, false)
            preencherCard(itemView, rota, viagemAtiva)

            // Toque no card abre a rota direto
            itemView.setOnClickListener {
                startActivity(
                    Intent(requireContext(), RotaDetalheActivity::class.java)
                        .putExtra(RotaDetalheActivity.EXTRA_ROTA_ID, rota.id)
                )
            }

            containerRotas.addView(itemView)
        }
    }

    /** Card da rota: nome, turno • trajeto, selo (ativa/arquivada), origem →
     * escola, lotação com barra, distância/duração, os dias da semana e o
     * botão de iniciar/retomar a viagem. */
    private fun preencherCard(itemView: View, rota: RotaEntity, viagemAtiva: ViagemEntity?) {

        itemView.findViewById<TextView>(R.id.txtNomeRotaItem).text = rota.nome

        itemView.findViewById<TextView>(R.id.txtDetalheRotaItem).text =
            listOf(RotaTextos.turno(rota.turno), RotaTextos.sentido(rota.tipoTrajeto))
                .filter { it.isNotBlank() }
                .joinToString(" • ")

        val arquivada = rota.status == "ARQUIVADA"
        itemView.findViewById<TextView>(R.id.txtSeloRotaItem).apply {
            text = if (arquivada) "Arquivada" else "Ativa"
            backgroundTintList = ColorStateList.valueOf(
                cor(if (arquivada) R.color.transporte_concluida_fundo else R.color.transporte_andamento_fundo)
            )
            setTextColor(cor(if (arquivada) R.color.transporte_concluida_texto else R.color.transporte_andamento_texto))
        }

        itemView.findViewById<TextView>(R.id.txtOrigemRotaItem).text =
            rota.origemNome.ifBlank { rota.origemEndereco }.ifBlank { "Saída não informada" }
        itemView.findViewById<TextView>(R.id.txtDestinoRotaItem).text =
            rota.escolaNome.ifBlank { rota.destinoNome }.ifBlank { rota.destinoEndereco }

        // Lotação
        itemView.findViewById<TextView>(R.id.txtVagasRotaItem).text =
            "${rota.vagasOcupadas} / ${rota.capacidadeTotal}"
        itemView.findViewById<ProgressBar>(R.id.progressVagasRotaItem).progress =
            if (rota.capacidadeTotal > 0) (rota.vagasOcupadas * 100 / rota.capacidadeTotal).coerceIn(0, 100) else 0

        // Distância e duração (calculadas ao salvar a rota; 0 = ainda não calculadas)
        itemView.findViewById<TextView>(R.id.txtDistanciaRotaItem).text =
            if (rota.distanciaMetros > 0) {
                String.format(Locale("pt", "BR"), "%.1f km", rota.distanciaMetros / 1000.0)
            } else {
                "–"
            }
        itemView.findViewById<TextView>(R.id.txtDuracaoRotaItem).text =
            if (rota.duracaoSegundos > 0) "cerca de ${RotaTextos.duracao(rota.duracaoSegundos)}" else "Percurso"

        montarDias(itemView.findViewById(R.id.containerDiasRotaItem), rota.diasSemana)

        configurarBotaoViagem(itemView.findViewById(R.id.btnViagemRotaItem), rota, viagemAtiva)
    }

    /** "Retomar viagem" se a viagem ativa é desta rota; senão "Iniciar
     * viagem" (modal de ida/volta). Sem alunos ou arquivada, fica apagado e
     * o toque explica o motivo. */
    private fun configurarBotaoViagem(botao: MaterialButton, rota: RotaEntity, viagemAtiva: ViagemEntity?) {
        if (viagemAtiva != null && viagemAtiva.rotaId == rota.id) {
            botao.text = "Retomar viagem"
            botao.alpha = 1f
            botao.isEnabled = true
            botao.setOnClickListener { abrirNavegacaoDaViagem(requireContext(), viagemAtiva.id) }
            return
        }

        val iniciando = rotaIniciando == rota.id
        botao.text = if (iniciando) "Iniciando..." else "Iniciar viagem"
        botao.isEnabled = !iniciando
        botao.alpha = if (rota.vagasOcupadas > 0 && rota.status == "ATIVA") 1f else 0.5f
        botao.setOnClickListener {
            abrirIniciarViagem(
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner,
                rota = rota,
                aoMudarOcupado = { ocupado ->
                    rotaIniciando = if (ocupado) rota.id else null
                    botao.text = if (ocupado) "Iniciando..." else "Iniciar viagem"
                    botao.isEnabled = !ocupado
                }
            ) { direcao -> viewModel.iniciarViagem(rota.id, direcao) }
        }
    }

    private fun montarDias(container: LinearLayout, diasSemana: String) {
        container.removeAllViews()

        val dp = resources.displayMetrics.density
        val padH = (10 * dp).toInt()
        val padV = (4 * dp).toInt()

        DataUtil.ordenarDiasSemana(diasSemana).forEach { dia ->
            val chip = TextView(requireContext()).apply {
                text = RotaTextos.diaCurto(dia)
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(cor(R.color.text_primary))
                setPadding(padH, padV, padH, padV)
                setBackgroundResource(R.drawable.bg_tile_rota)
            }
            container.addView(
                chip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (6 * dp).toInt() }
            )
        }
    }

    private fun cor(@ColorRes id: Int): Int = ContextCompat.getColor(requireContext(), id)
}
