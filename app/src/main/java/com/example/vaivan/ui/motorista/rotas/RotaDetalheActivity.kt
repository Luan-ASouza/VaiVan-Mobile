package com.example.vaivan.ui.motorista.rotas

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.AvisoAusenciaEntity
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.example.vaivan.domain.usecase.preco.PrecoPolicy
import com.example.vaivan.domain.usecase.rota.AtualizarTrajetoDaRotaUseCase
import com.example.vaivan.domain.usecase.rota.CalcularOrdemVoltaUseCase
import com.example.vaivan.domain.usecase.rota.RecalcularRotaUseCase
import com.example.vaivan.domain.usecase.solicitacao.AceitarSolicitacaoUseCase
import com.example.vaivan.domain.usecase.solicitacao.CancelarSolicitacaoUseCase
import com.example.vaivan.domain.usecase.solicitacao.ConfirmarReservasPagasUseCase
import com.example.vaivan.domain.usecase.viagem.IniciarViagemUseCase
import com.example.vaivan.ui.comum.chat.ChatActivity
import com.example.vaivan.ui.comum.perfil.LinhaCampoPerfil
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.vaivan.ui.comum.tema.TemaPerfil
import com.example.vaivan.ui.comum.tema.putTema
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detalhe de uma rota do motorista. Cabeçalho fixo com "Iniciar viagem"
 * (modal de ida/volta) e "Ver trajeto" (mapa com os alunos), e três abas:
 *  - Informações: vagas + cada campo da rota numa linha que abre a sua tela
 *    de edição (CriarRotaActivity no modo de um campo só), e excluir.
 *  - Percurso: distância/tempo e a ordem das paradas (partida → alunos → escola).
 *  - Alunos: pedidos de vaga, avisos de falta, confirmados (com chat),
 *    aguardando 1º pagamento e recusados.
 */
class RotaDetalheActivity : BaseActivity() {

    companion object {
        const val EXTRA_ROTA_ID = "rotaId"
    }

    private enum class Aba { INFORMACOES, PERCURSO, ALUNOS }

    private lateinit var rotaId: String

    private val passageiroRepository by lazy {
        PassageiroRepository(VaivanDatabase.getInstance(this).passageiroDao())
    }

    private val viewModel: RotaDetalheViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(this@RotaDetalheActivity)
                val routesClient = GoogleRoutesClient(this@RotaDetalheActivity)

                val rotaRepository = RotaRepository(
                    rotaDao = db.rotaDao(),
                    paradaRotaDao = db.paradaRotaDao(),
                    routesClient = routesClient,
                    solicitacaoInclusaoDao = db.solicitacaoInclusaoDao()
                )

                val solicitacaoRepository = SolicitacaoInclusaoRepository(
                    this@RotaDetalheActivity,
                    db.solicitacaoInclusaoDao()
                )

                val recalcularRotaUseCase = RecalcularRotaUseCase(
                    rotaRepository = rotaRepository,
                    routesClient = routesClient
                )

                val viagemRepository = ViagemRepository(
                    viagemDao = db.viagemDao(),
                    paradaViagemDao = db.paradaViagemDao(),
                    eventoViagemDao = db.eventoViagemDao()
                )

                val avisoAusenciaRepository = AvisoAusenciaRepository(db.avisoAusenciaDao())

                RotaDetalheViewModel(
                    rotaRepository = rotaRepository,
                    solicitacaoRepository = solicitacaoRepository,
                    aceitarSolicitacaoUseCase = AceitarSolicitacaoUseCase(
                        solicitacaoInclusaoRepository = solicitacaoRepository,
                        rotaRepository = rotaRepository
                    ),
                    iniciarViagemUseCase = IniciarViagemUseCase(
                        rotaRepository = rotaRepository,
                        passageiroRepository = PassageiroRepository(db.passageiroDao()),
                        viagemRepository = viagemRepository,
                        avisoAusenciaRepository = avisoAusenciaRepository,
                        calcularOrdemVolta = CalcularOrdemVoltaUseCase(routesClient)
                    ),
                    avisoAusenciaRepository = avisoAusenciaRepository,
                    confirmarReservasPagasUseCase = ConfirmarReservasPagasUseCase(
                        solicitacaoRepository = solicitacaoRepository,
                        recalcularRotaUseCase = recalcularRotaUseCase,
                        rotaRepository = rotaRepository,
                        cancelarSolicitacaoUseCase = CancelarSolicitacaoUseCase(db.solicitacaoInclusaoDao())
                    ),
                    atualizarTrajetoUseCase = AtualizarTrajetoDaRotaUseCase(rotaRepository, recalcularRotaUseCase)
                )
            }
        }
    }

    // Cabeçalho
    private lateinit var txtNomeRota: TextView
    private lateinit var txtResumoRota: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnIniciarViagem: MaterialButton
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var scroll: ScrollView

    // Abas
    private lateinit var abas: Map<Aba, Pair<View, View>> // aba -> (botão, conteúdo)
    private lateinit var txtAbaAlunos: TextView
    private lateinit var txtBadgeAlunos: TextView
    private var abaAtual = Aba.INFORMACOES

    // Informações
    private lateinit var txtVagasLivres: TextView
    private lateinit var txtVagasLegenda: TextView
    private lateinit var txtVagasOcupadas: TextView
    private lateinit var progressVagas: ProgressBar
    private lateinit var campoNome: LinhaCampoPerfil
    private lateinit var campoSentido: LinhaCampoPerfil
    private lateinit var campoTurno: LinhaCampoPerfil
    private lateinit var campoDias: LinhaCampoPerfil
    private lateinit var campoCapacidade: LinhaCampoPerfil
    private lateinit var campoEscola: LinhaCampoPerfil
    private lateinit var campoPonto: LinhaCampoPerfil
    private lateinit var campoBairros: LinhaCampoPerfil
    private lateinit var campoVeiculo: LinhaCampoPerfil
    private var veiculoExibidoId: String? = null

    // Percurso
    private lateinit var txtDistancia: TextView
    private lateinit var txtDuracao: TextView
    private lateinit var containerPercurso: LinearLayout
    private lateinit var txtNotaVolta: TextView

    // Alunos
    private lateinit var containerSecaoSolicitacoes: View
    private lateinit var containerSolicitacoes: LinearLayout
    private lateinit var containerSecaoAvisos: View
    private lateinit var containerAvisos: LinearLayout
    private lateinit var containerAlunos: LinearLayout

    private var rotaCarregada: RotaEntity? = null
    private var iniciandoViagem = false

    private var ultimasParadas: List<ParadaRotaEntity> = emptyList()

    // Todas as solicitações dessa rota (aceitas, recusadas e pendentes), vindas do Firestore.
    // O Room do motorista só guarda as pendentes nessa tela.
    private var solicitacoesDoServidor: List<SolicitacaoInclusaoEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rota_detalhe)

        rotaId = intent.getStringExtra(EXTRA_ROTA_ID) ?: run {
            finish()
            return
        }

        prepararCabecalho()
        prepararAbas()
        prepararInformacoes()
        prepararPercurso()
        prepararAlunos()

        observarRota()
        observarSolicitacoes()
        observarAvisosAusencia()
        observarParadas()

        carregarParadasDoServidor()
        carregarSolicitacoesDoServidor()
        processarReservasAoAbrir()

        aplicarGapsDeSistema()
    }

    // ---------------------------------------------------------
    // CABEÇALHO
    // ---------------------------------------------------------

    private fun prepararCabecalho() {
        findViewById<ImageButton>(R.id.btnVoltar).setOnClickListener { finish() }

        txtNomeRota = findViewById(R.id.txtNomeRotaDetalhe)
        txtResumoRota = findViewById(R.id.txtResumoRotaDetalhe)
        txtStatus = findViewById(R.id.txtStatusDetalhe)
        btnIniciarViagem = findViewById(R.id.btnIniciarViagemDetalhe)
        scroll = findViewById(R.id.scrollRotaDetalhe)

        btnIniciarViagem.setOnClickListener {
            val rota = rotaCarregada ?: return@setOnClickListener
            if (iniciandoViagem) return@setOnClickListener
            val motoristaId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

            abrirIniciarViagem(
                context = this,
                lifecycleOwner = this,
                rota = rota,
                aoMudarOcupado = { ocupado ->
                    iniciandoViagem = ocupado
                    btnIniciarViagem.text = if (ocupado) "Iniciando..." else "Iniciar viagem"
                    btnIniciarViagem.isEnabled = !ocupado
                }
            ) { direcao -> viewModel.iniciarViagem(rota.id, motoristaId, direcao) }
        }

        // Trajeto com os alunos (inclui a partida e a escola)
        findViewById<View>(R.id.btnTrajetoDetalhe).setOnClickListener {
            startActivity(
                Intent(this, RotaCalculadaActivity::class.java)
                    .putExtra(RotaCalculadaActivity.EXTRA_ROTA_ID, rotaId)
            )
        }

        swipeRefresh = findViewById(R.id.swipeRefreshRotaDetalhe)
        swipeRefresh.setOnRefreshListener {
            val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
            if (motoristaId == null) {
                swipeRefresh.isRefreshing = false
            } else {
                lifecycleScope.launch {
                    try {
                        viewModel.atualizarTudo(rotaId, motoristaId)
                        carregarSolicitacoesDoServidor()
                    } finally {
                        swipeRefresh.isRefreshing = false
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // ABAS
    // ---------------------------------------------------------

    private fun prepararAbas() {
        txtAbaAlunos = findViewById(R.id.txtAbaAlunosDetalhe)
        txtBadgeAlunos = findViewById(R.id.txtBadgeAlunosDetalhe)

        abas = mapOf(
            Aba.INFORMACOES to (findViewById<View>(R.id.abaInformacoesDetalhe) to findViewById(R.id.conteudoInformacoesDetalhe)),
            Aba.PERCURSO to (findViewById<View>(R.id.abaPercursoDetalhe) to findViewById(R.id.conteudoPercursoDetalhe)),
            Aba.ALUNOS to (findViewById<View>(R.id.abaAlunosDetalhe) to findViewById(R.id.conteudoAlunosDetalhe))
        )

        abas.forEach { (aba, par) -> par.first.setOnClickListener { selecionarAba(aba) } }
        selecionarAba(Aba.INFORMACOES)
    }

    private fun selecionarAba(aba: Aba) {
        abaAtual = aba
        abas.forEach { (chave, par) ->
            val (botao, conteudo) = par
            val selecionada = chave == aba
            botao.setBackgroundResource(if (selecionada) R.drawable.bg_aba_escura_selecionada else R.drawable.bg_aba)
            val corTexto = cor(if (selecionada) R.color.text_on_dark else R.color.text_primary)
            (botao as? TextView)?.setTextColor(corTexto)
            if (chave == Aba.ALUNOS) txtAbaAlunos.setTextColor(corTexto)
            conteudo.visibility = if (selecionada) View.VISIBLE else View.GONE
        }
        scroll.scrollTo(0, 0)
    }

    // ---------------------------------------------------------
    // ABA INFORMAÇÕES
    // ---------------------------------------------------------

    private fun prepararInformacoes() {
        txtVagasLivres = findViewById(R.id.txtVagasLivresDetalhe)
        txtVagasLegenda = findViewById(R.id.txtVagasLegendaDetalhe)
        txtVagasOcupadas = findViewById(R.id.txtVagasOcupadasDetalhe)
        progressVagas = findViewById(R.id.progressVagasDetalhe)

        val raiz = findViewById<View>(android.R.id.content)
        campoNome = LinhaCampoPerfil(raiz, R.id.campoNomeRota, "Nome")
        campoSentido = LinhaCampoPerfil(raiz, R.id.campoSentidoRota, "Sentido")
        campoTurno = LinhaCampoPerfil(raiz, R.id.campoTurnoRota, "Turno")
        campoDias = LinhaCampoPerfil(raiz, R.id.campoDiasRota, "Dias da semana")
        campoCapacidade = LinhaCampoPerfil(raiz, R.id.campoCapacidadeRota, "Capacidade")
        campoEscola = LinhaCampoPerfil(raiz, R.id.campoEscolaRota, "Escola")
        campoPonto = LinhaCampoPerfil(raiz, R.id.campoPontoRota, "Ponto de embarque")
        campoBairros = LinhaCampoPerfil(raiz, R.id.campoBairrosRota, "Bairros atendidos")
        campoVeiculo = LinhaCampoPerfil(raiz, R.id.campoVeiculoRota, "Veículo")

        mapOf(
            campoVeiculo to CriarRotaActivity.Etapa.VEICULO,
            campoNome to CriarRotaActivity.Etapa.NOME,
            campoSentido to CriarRotaActivity.Etapa.SENTIDO,
            campoTurno to CriarRotaActivity.Etapa.TURNO,
            campoDias to CriarRotaActivity.Etapa.DIAS,
            campoCapacidade to CriarRotaActivity.Etapa.CAPACIDADE,
            campoEscola to CriarRotaActivity.Etapa.ESCOLA,
            campoPonto to CriarRotaActivity.Etapa.PONTO,
            campoBairros to CriarRotaActivity.Etapa.BAIRROS
        ).forEach { (linha, etapa) ->
            linha.aoTocar { startActivity(CriarRotaActivity.intentEditar(this, rotaId, etapa)) }
        }

        findViewById<View>(R.id.btnExcluirRota).setOnClickListener { confirmarExclusao() }
    }

    private fun preencherInformacoes(rota: RotaEntity) {
        val livres = (rota.capacidadeTotal - rota.vagasOcupadas).coerceAtLeast(0)
        txtVagasLivres.text = livres.toString()
        txtVagasLegenda.text = if (livres == 1) "vaga livre" else "vagas livres"
        txtVagasOcupadas.text = "${rota.vagasOcupadas} de ${rota.capacidadeTotal} ocupadas"
        progressVagas.max = rota.capacidadeTotal.coerceAtLeast(1)
        progressVagas.progress = rota.vagasOcupadas

        campoNome.valor(rota.nome)
        campoSentido.valor(RotaTextos.sentido(rota.tipoTrajeto))
        campoTurno.valor(RotaTextos.turno(rota.turno))
        campoDias.valor(RotaTextos.dias(rota.diasSemana.split(",")))
        campoCapacidade.valor("${rota.capacidadeTotal} alunos")
        campoEscola.valor(rota.escolaNome.ifBlank { rota.destinoNome })
        preencherVeiculo(rota.veiculoId)
        campoPonto.rotulo(RotaTextos.rotuloPonto(rota.tipoTrajeto))
        campoPonto.valor(rota.origemNome.ifBlank { rota.origemEndereco }.ifBlank { "Não informado" })
        campoBairros.valor(
            when (rota.bairrosAtendidos.size) {
                0 -> "Nenhum"
                1 -> RotaTextos.bairro(rota.bairrosAtendidos.first())
                else -> "${rota.bairrosAtendidos.size} bairros"
            }
        )
    }

    /** Veículo da rota (lido uma vez por troca de veículo, direto do Firestore). */
    private fun preencherVeiculo(veiculoId: String?) {
        val id = veiculoId?.takeIf { it.isNotBlank() }
        if (id == null) {
            veiculoExibidoId = null
            campoVeiculo.valor("Não definido")
            return
        }
        if (id == veiculoExibidoId) return
        veiculoExibidoId = id
        campoVeiculo.valor("…")
        lifecycleScope.launch {
            val veiculo = runCatching {
                VeiculoRepository(VaivanDatabase.getInstance(this@RotaDetalheActivity).veiculoDao())
                    .consultarVeiculo(id)
            }.getOrNull()
            campoVeiculo.valor(
                veiculo?.let { listOf(it.modelo, it.placa).filter(String::isNotBlank).joinToString(" · ") }
                    ?: "Não encontrado"
            )
        }
    }

    private fun confirmarExclusao() {
        val rota = rotaCarregada ?: return

        val aviso =
            if (rota.vagasOcupadas > 0)
                "Esta rota já tem ${rota.vagasOcupadas} passageiro(s) vinculado(s). " +
                    "Eles perderão a vaga.\n\nEssa ação não pode ser desfeita."
            else
                "Essa ação não pode ser desfeita."

        ConfirmacaoExclusaoHelper.confirmar(
            contexto = this,
            titulo = "Excluir \"${rota.nome}\"?",
            mensagem = aviso
        ) {
            lifecycleScope.launch {
                try {
                    viewModel.excluirRota(rota.id)
                    Toast.makeText(this@RotaDetalheActivity, "Rota excluída.", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@RotaDetalheActivity,
                        "Erro ao excluir: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ---------------------------------------------------------
    // ABA PERCURSO
    // ---------------------------------------------------------

    private fun prepararPercurso() {
        txtDistancia = findViewById(R.id.txtDistanciaDetalhe)
        txtDuracao = findViewById(R.id.txtDuracaoDetalhe)
        containerPercurso = findViewById(R.id.containerPercursoDetalhe)
        txtNotaVolta = findViewById(R.id.txtNotaVoltaDetalhe)

        val btnRecalcular = findViewById<TextView>(R.id.btnRecalcularTrajetoDetalhe)
        btnRecalcular.setOnClickListener {
            btnRecalcular.isEnabled = false
            btnRecalcular.text = "Recalculando..."
            lifecycleScope.launch {
                val mensagem = try {
                    if (viewModel.recalcularTrajeto(rotaId)) "Trajeto atualizado."
                    else "A rota mudou enquanto calculava. Tente de novo."
                } catch (e: Exception) {
                    "Não foi possível recalcular: ${e.message}"
                }
                Toast.makeText(this@RotaDetalheActivity, mensagem, Toast.LENGTH_LONG).show()
                btnRecalcular.isEnabled = true
                btnRecalcular.text = "Recalcular trajeto"
            }
        }
    }

    /** Linha do tempo na ordem em que a van passa: partida, cada aluno (com
     * o tempo estimado desde a saída) e a escola. */
    private fun renderizarPercurso() {
        val rota = rotaCarregada ?: return

        txtDistancia.text =
            if (rota.distanciaMetros > 0) String.format(Locale("pt", "BR"), "%.1f km", rota.distanciaMetros / 1000.0)
            else "—"
        txtDuracao.text = RotaTextos.duracao(rota.duracaoSegundos)

        txtNotaVolta.visibility = if (rota.tipoTrajeto == "IDA") View.GONE else View.VISIBLE
        txtNotaVolta.text =
            if (rota.tipoTrajeto == "VOLTA") "Rota só de volta: a van sai da escola e faz este caminho ao contrário."
            else "Na volta, a van sai da escola e a ordem das casas é recalculada ao iniciar a viagem."

        containerPercurso.removeAllViews()

        data class Ponto(val marcador: String, val titulo: String, val endereco: String, val tempo: String?, val escuro: Boolean)

        val pontos = buildList {
            add(Ponto("▶", RotaTextos.rotuloPonto(rota.tipoTrajeto).let { if (rota.tipoTrajeto == "VOLTA") it else "Partida" },
                rota.origemEndereco.ifBlank { rota.origemNome }, null, escuro = true))
            ultimasParadas.sortedBy { it.ordem }.forEachIndexed { indice, parada ->
                add(
                    Ponto(
                        "${indice + 1}",
                        parada.nomePassageiro.ifBlank { "Aluno" },
                        parada.endereco,
                        if (parada.horarioEstimadoMinutos > 0) "+${parada.horarioEstimadoMinutos} min" else null,
                        escuro = false
                    )
                )
            }
            add(
                Ponto(
                    "E",
                    rota.escolaNome.ifBlank { rota.destinoNome }.ifBlank { "Escola" },
                    rota.destinoEndereco,
                    if (rota.duracaoSegundos > 0) RotaTextos.duracao(rota.duracaoSegundos) else null,
                    escuro = true
                )
            )
        }

        val inflater = LayoutInflater.from(this)
        pontos.forEachIndexed { indice, ponto ->
            val item = inflater.inflate(R.layout.item_percurso_parada, containerPercurso, false)

            item.findViewById<View>(R.id.linhaCimaPercurso).visibility =
                if (indice == 0) View.INVISIBLE else View.VISIBLE
            item.findViewById<View>(R.id.linhaBaixoPercurso).visibility =
                if (indice == pontos.lastIndex) View.INVISIBLE else View.VISIBLE

            item.findViewById<TextView>(R.id.marcadorPercurso).apply {
                text = ponto.marcador
                backgroundTintList = ColorStateList.valueOf(
                    cor(if (ponto.escuro) R.color.rota_destaque else R.color.color_primary)
                )
                setTextColor(cor(if (ponto.escuro) R.color.text_on_dark else R.color.text_primary))
            }
            item.findViewById<TextView>(R.id.txtTituloPercurso).text = ponto.titulo
            item.findViewById<TextView>(R.id.txtEnderecoPercurso).apply {
                text = ponto.endereco
                visibility = if (ponto.endereco.isBlank()) View.GONE else View.VISIBLE
            }
            item.findViewById<TextView>(R.id.txtTempoPercurso).apply {
                text = ponto.tempo.orEmpty()
                visibility = if (ponto.tempo == null) View.GONE else View.VISIBLE
            }

            containerPercurso.addView(item)
        }
    }

    // ---------------------------------------------------------
    // ROTA
    // ---------------------------------------------------------

    private fun observarRota() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observarRota(rotaId).collect { rota ->
                    if (rota != null) preencherRota(rota)
                }
            }
        }
    }

    private fun preencherRota(rota: RotaEntity) {
        rotaCarregada = rota

        txtNomeRota.text = rota.nome
        txtResumoRota.text = listOf(
            RotaTextos.turno(rota.turno),
            RotaTextos.sentido(rota.tipoTrajeto),
            RotaTextos.dias(rota.diasSemana.split(","))
        ).filter { it.isNotBlank() }.joinToString(" • ")

        val ativa = rota.status == "ATIVA"
        txtStatus.text = if (ativa) "Ativa" else "Arquivada"
        txtStatus.backgroundTintList = ColorStateList.valueOf(
            cor(if (ativa) R.color.transporte_andamento_fundo else R.color.transporte_concluida_fundo)
        )
        txtStatus.setTextColor(cor(if (ativa) R.color.transporte_andamento_texto else R.color.transporte_concluida_texto))

        // Sem alunos ou arquivada: o botão fica apagado e o toque explica
        btnIniciarViagem.alpha = if (rota.vagasOcupadas > 0 && ativa) 1f else 0.5f

        preencherInformacoes(rota)
        renderizarPercurso()
    }

    // ---------------------------------------------------------
    // ABA ALUNOS: SOLICITAÇÕES PENDENTES
    // ---------------------------------------------------------

    private fun prepararAlunos() {
        containerSecaoSolicitacoes = findViewById(R.id.containerSecaoSolicitacoesDetalhe)
        containerSolicitacoes = findViewById(R.id.containerSolicitacoesDetalhe)
        containerSecaoAvisos = findViewById(R.id.containerSecaoAvisosAusenciaDetalhe)
        containerAvisos = findViewById(R.id.containerAvisosAusenciaDetalhe)
        containerAlunos = findViewById(R.id.containerAlunosDetalhe)
    }

    private fun observarSolicitacoes() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val motoristaId = FirebaseAuth.getInstance().currentUser?.uid ?: return@repeatOnLifecycle

                viewModel.observarSolicitacoes(motoristaId).collect { todas ->
                    renderizarSolicitacoes(todas.filter { it.rotaId == rotaId })

                    // Quando algo muda (aceitou, recusou, chegou uma nova),
                    // atualiza também a lista de confirmados/recusados.
                    carregarSolicitacoesDoServidor()
                }
            }
        }
    }

    private fun renderizarSolicitacoes(solicitacoes: List<SolicitacaoInclusaoEntity>) {
        containerSolicitacoes.removeAllViews()

        val pendentes = solicitacoes.filter { it.status == "PENDENTE" }

        containerSecaoSolicitacoes.visibility = if (pendentes.isEmpty()) View.GONE else View.VISIBLE
        txtBadgeAlunos.text = pendentes.size.toString()
        txtBadgeAlunos.visibility = if (pendentes.isEmpty()) View.GONE else View.VISIBLE

        val inflater = LayoutInflater.from(this)

        pendentes.forEach { solicitacao ->
            val itemView = inflater.inflate(R.layout.item_solicitacao_pendente, containerSolicitacoes, false)

            itemView.findViewById<TextView>(R.id.txtNomeAlunoSolicitacao).text = solicitacao.nomePassageiro
            carregarFotoAluno(itemView.findViewById(R.id.imgFotoAlunoSolicitacao), solicitacao.passageiroId)

            itemView.findViewById<TextView>(R.id.txtEnderecoSolicitacao).text = solicitacao.enderecoEmbarque
            itemView.findViewById<TextView>(R.id.txtHorarioSolicitacao).text =
                "Horário desejado: ${solicitacao.horarioDesejado}"

            // Valor que o responsável viu ao pedir a vaga
            itemView.findViewById<TextView>(R.id.txtValorSolicitacao).text =
                if (solicitacao.valorMensalCentavos > 0) {
                    val qtdDias = solicitacao.diasSemana.split(",").count { it.isNotBlank() }
                    "${AssinaturaUi.formatarValor(solicitacao.valorMensalCentavos)} / mês · " +
                        "${PrecoPolicy.rotuloModalidade(solicitacao.modalidade)} · " +
                        "$qtdDias ${if (qtdDias == 1) "dia" else "dias"} · " +
                        "${PrecoPolicy.distanciaKm(solicitacao.distanciaMetros.toDouble())} km"
                } else {
                    "Valor a combinar"
                }

            // Mensagem do responsável (o bloco só aparece se ele escreveu algo)
            val mensagem = solicitacao.mensagem.trim()
            val blocoMensagem = itemView.findViewById<View>(R.id.containerMensagemSolicitacao)
            if (mensagem.isBlank()) {
                blocoMensagem.visibility = View.GONE
            } else {
                blocoMensagem.visibility = View.VISIBLE
                itemView.findViewById<TextView>(R.id.txtMensagemSolicitacao).text = mensagem
            }

            val btnAceitar = itemView.findViewById<Button>(R.id.btnAceitarSolicitacao)
            val btnRecusar = itemView.findViewById<Button>(R.id.btnRecusarSolicitacao)

            btnAceitar.setOnClickListener {
                btnAceitar.isEnabled = false
                btnRecusar.isEnabled = false

                lifecycleScope.launch {
                    try {
                        viewModel.aceitarSolicitacao(solicitacao)
                        Toast.makeText(
                            this@RotaDetalheActivity,
                            "Vaga reservada! O aluno entra na rota quando o responsável pagar a 1ª mensalidade (prazo de 3 dias).",
                            Toast.LENGTH_LONG
                        ).show()
                        carregarSolicitacoesDoServidor()
                    } catch (erro: Exception) {
                        btnAceitar.isEnabled = true
                        btnRecusar.isEnabled = true
                        Log.e("AceitarRota", "Erro ao aceitar solicitação", erro)
                        Toast.makeText(this@RotaDetalheActivity, "Erro: ${erro.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            btnRecusar.setOnClickListener {
                btnAceitar.isEnabled = false
                btnRecusar.isEnabled = false

                lifecycleScope.launch {
                    try {
                        viewModel.recusarSolicitacao(solicitacao)
                        Toast.makeText(this@RotaDetalheActivity, "Solicitação recusada.", Toast.LENGTH_SHORT).show()
                    } catch (erro: Exception) {
                        btnAceitar.isEnabled = true
                        btnRecusar.isEnabled = true
                        Toast.makeText(this@RotaDetalheActivity, "Erro: ${erro.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            containerSolicitacoes.addView(itemView)
        }
    }

    // ---------------------------------------------------------
    // ABA ALUNOS: AVISOS DE AUSÊNCIA (responsável avisou com antecedência)
    // ---------------------------------------------------------

    private fun observarAvisosAusencia() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val motoristaId = FirebaseAuth.getInstance().currentUser?.uid ?: return@repeatOnLifecycle

                viewModel.observarAvisosAusencia(motoristaId).collect { todos ->
                    renderizarAvisosAusencia(todos.filter { it.rotaId == rotaId })
                }
            }
        }
    }

    /** Só leitura — é um aviso, não uma solicitação pra aceitar/recusar. A
     * exclusão da parada já aconteceu automaticamente em IniciarViagemUseCase. */
    private fun renderizarAvisosAusencia(avisos: List<AvisoAusenciaEntity>) {
        containerAvisos.removeAllViews()
        containerSecaoAvisos.visibility = if (avisos.isEmpty()) View.GONE else View.VISIBLE

        avisos.forEach { aviso ->
            val descricao = when (aviso.tipo) {
                AvisoAusenciaRepository.TIPO_DIA_INTEIRO -> "não vai usar o transporte hoje (ida e volta)"
                AvisoAusenciaRepository.TIPO_SOMENTE_VOLTA -> "não vai usar o transporte na volta hoje"
                else -> "não vai usar o transporte na ida hoje"
            }
            containerAvisos.addView(criarLinhaInformativa("${aviso.nomePassageiro} $descricao"))
        }
    }

    // ---------------------------------------------------------
    // ABA ALUNOS: CONFIRMADOS, AGUARDANDO PAGAMENTO E RECUSADOS
    // ---------------------------------------------------------

    private fun observarParadas() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observarParadas(rotaId).collect { paradas: List<ParadaRotaEntity> ->
                    ultimasParadas = paradas
                    renderizarAlunos()
                    renderizarPercurso()
                }
            }
        }
    }

    /**
     * Ao abrir a tela: quem já pagou a 1ª mensalidade entra no trajeto, e reservas
     * com prazo vencido são liberadas. Depois recarrega paradas e solicitações.
     */
    private fun processarReservasAoAbrir() {
        val motoristaId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            val ativados = viewModel.processarReservas(motoristaId)
            if (ativados > 0) {
                Toast.makeText(
                    this@RotaDetalheActivity,
                    if (ativados == 1) "1 aluno pagou e entrou na rota." else "$ativados alunos pagaram e entraram na rota.",
                    Toast.LENGTH_LONG
                ).show()
            }
            carregarParadasDoServidor()
            carregarSolicitacoesDoServidor()
        }
    }

    /** Traz as paradas (alunos confirmados) do Firestore para o Room. */
    private fun carregarParadasDoServidor() {
        lifecycleScope.launch {
            try {
                viewModel.sincronizarParadas(rotaId)
            } catch (e: Exception) {
                Log.e("DETALHE_ALUNOS", "Erro ao buscar paradas", e)
            }
        }
    }

    /** Traz do Firestore todas as solicitações dessa rota (aceitas, recusadas e pendentes). */
    private fun carregarSolicitacoesDoServidor() {
        val motoristaId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("solicitacoes_inclusao")
                    .whereEqualTo("motoristaId", motoristaId)
                    .whereEqualTo("rotaId", rotaId)
                    .get()
                    .await()

                solicitacoesDoServidor = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(SolicitacaoInclusaoEntity::class.java)?.copy(id = doc.id)
                }

                renderizarAlunos()
            } catch (e: Exception) {
                Log.e("DETALHE_ALUNOS", "Erro ao buscar solicitações", e)
            }
        }
    }

    private fun renderizarAlunos() {
        containerAlunos.removeAllViews()

        txtAbaAlunos.text = if (ultimasParadas.isEmpty()) "Alunos" else "Alunos (${ultimasParadas.size})"

        // --- Confirmados -------------------------------------------------
        containerAlunos.addView(criarTituloDeSecao("Confirmados"))

        if (ultimasParadas.isEmpty()) {
            containerAlunos.addView(criarLinhaInformativa("Nenhum aluno confirmado ainda."))
        } else {
            val inflater = LayoutInflater.from(this)
            ultimasParadas.sortedBy { it.ordem }.forEachIndexed { indice, parada ->
                containerAlunos.addView(criarCardAluno(inflater, indice, parada))
            }
        }

        // --- Aguardando pagamento (vaga reservada) -------------------------
        val reservadas = solicitacoesDoServidor
            .filter { it.status == SolicitacaoInclusaoRepository.STATUS_AGUARDANDO_PAGAMENTO }

        if (reservadas.isNotEmpty()) {
            containerAlunos.addView(criarTituloDeSecao("Aguardando 1º pagamento"))

            reservadas.forEach { solicitacao ->
                val prazo = solicitacao.respondidoEm
                    ?.let { MensalidadePolicy.prazoPrimeiroPagamento(it) }
                    ?.let { SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(Date(it)) }
                containerAlunos.addView(
                    criarLinhaInformativa(
                        "${solicitacao.nomePassageiro}: vaga reservada" +
                            (prazo?.let { ", o responsável paga até $it" } ?: "") +
                            ". Entra na rota após o pagamento."
                    )
                )
            }
        }

        // --- Recusados ---------------------------------------------------
        val recusadas = solicitacoesDoServidor.filter { it.status == "RECUSADA" }

        if (recusadas.isNotEmpty()) {
            containerAlunos.addView(criarTituloDeSecao("Recusados"))
            recusadas.forEach { solicitacao ->
                containerAlunos.addView(
                    criarLinhaInformativa("${solicitacao.nomePassageiro} — ${solicitacao.enderecoEmbarque}")
                )
            }
        }
    }

    private fun criarCardAluno(inflater: LayoutInflater, indice: Int, parada: ParadaRotaEntity): View {
        val card = inflater.inflate(R.layout.item_aluno_rota, containerAlunos, false)

        card.findViewById<TextView>(R.id.txtOrdemAlunoRota).text = "${indice + 1}"
        card.findViewById<TextView>(R.id.txtNomeAlunoRota).text = parada.nomePassageiro
        card.findViewById<TextView>(R.id.txtEnderecoAlunoRota).text = parada.endereco
        carregarFotoAluno(card.findViewById(R.id.imgFotoAlunoRota), parada.passageiroId)

        val btnChat = card.findViewById<ImageButton>(R.id.btnChatAlunoRota)
        btnChat.setOnClickListener { abrirChatComAluno(parada, btnChat) }

        return card
    }

    private fun carregarFotoAluno(imagem: ShapeableImageView, passageiroId: String) {
        lifecycleScope.launch {
            val passageiro = try {
                passageiroRepository.consultarPassageiroComCache(passageiroId)
            } catch (e: Exception) {
                null
            }

            Glide.with(imagem)
                .load(passageiro?.fotoUrl)
                .placeholder(R.drawable.avatar_01)
                .error(R.drawable.avatar_01)
                .circleCrop()
                .into(imagem)
        }
    }

    private fun criarTituloDeSecao(titulo: String): TextView =
        TextView(this).apply {
            text = titulo
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cor(R.color.text_secondary))
            setPadding(dp(6), dp(16), 0, dp(8))
        }

    private fun criarLinhaInformativa(texto: String): TextView =
        TextView(this).apply {
            text = texto
            textSize = 14f
            setTextColor(cor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_card_rounded)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

    // ---------------------------------------------------------
    // CHAT COM O RESPONSÁVEL
    // ---------------------------------------------------------

    private fun abrirChatComAluno(parada: ParadaRotaEntity, botao: View) {
        botao.isEnabled = false

        lifecycleScope.launch {
            try {
                val solicitacao = encontrarSolicitacaoAceita(parada)

                if (solicitacao == null) {
                    Toast.makeText(
                        this@RotaDetalheActivity,
                        "Solicitação aprovada desse aluno não encontrada",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                abrirChatComResponsavel(solicitacao)
            } catch (e: Exception) {
                Log.e("RotaDetalhe", "Erro ao abrir chat", e)
                Toast.makeText(
                    this@RotaDetalheActivity,
                    "Não foi possível abrir o chat: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                botao.isEnabled = true
            }
        }
    }

    /**
     * Acha a solicitação ACEITA do aluno: primeiro na lista já carregada;
     * se não estiver lá, consulta o Firestore pelo motorista logado.
     */
    private suspend fun encontrarSolicitacaoAceita(parada: ParadaRotaEntity): SolicitacaoInclusaoEntity? {
        solicitacoesDoServidor
            .firstOrNull { it.status == "ACEITA" && it.passageiroId == parada.passageiroId }
            ?.let { return it }

        val motoristaId = FirebaseAuth.getInstance().currentUser?.uid ?: return null

        val snapshot = FirebaseFirestore.getInstance()
            .collection("solicitacoes_inclusao")
            .whereEqualTo("motoristaId", motoristaId)
            .whereEqualTo("rotaId", rotaId)
            .whereEqualTo("passageiroId", parada.passageiroId)
            .whereEqualTo("status", "ACEITA")
            .get()
            .await()

        val documento = snapshot.documents.firstOrNull() ?: return null
        return documento.toObject(SolicitacaoInclusaoEntity::class.java)?.copy(id = documento.id)
    }

    private fun abrirChatComResponsavel(solicitacao: SolicitacaoInclusaoEntity) {
        val meuId = FirebaseAuth.getInstance().currentUser?.uid

        if (meuId == null || solicitacao.responsavelId.isBlank()) {
            Toast.makeText(this, "Responsável não encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        if (solicitacao.responsavelId == meuId) {
            Toast.makeText(
                this,
                "Esta solicitação é da sua própria conta. Para conversar, use duas contas diferentes.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        startActivity(
            Intent(this, ChatActivity::class.java).apply {
                putExtra("outroUsuarioId", solicitacao.responsavelId)
                putExtra("nomeContato", "Responsável")
                putExtra("solicitacaoId", solicitacao.id)

                // Mesma conversa que a mãe abre: mesma rota + mesmo aluno
                putExtra("rotaId", solicitacao.rotaId)
                putExtra("passageiroId", solicitacao.passageiroId)
                putExtra("passageiroNome", solicitacao.nomePassageiro)
                putExtra("rotaNome", rotaCarregada?.nome.orEmpty())
                putTema(TemaPerfil.MOTORISTA)
            }
        )
    }

    // ---------------------------------------------------------
    // AUXILIARES
    // ---------------------------------------------------------

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    private fun cor(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)
}
