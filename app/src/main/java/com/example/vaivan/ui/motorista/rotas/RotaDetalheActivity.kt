package com.example.vaivan.ui.motorista.rotas

import android.content.Intent
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
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
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.local.entities.AvisoAusenciaEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.domain.usecase.rota.RecalcularRotaUseCase
import com.example.vaivan.domain.usecase.solicitacao.AceitarSolicitacaoUseCase
import com.example.vaivan.domain.usecase.viagem.IniciarViagemUseCase
import com.example.vaivan.ui.comum.chat.ChatActivity
import com.example.vaivan.ui.motorista.rotas.navegacao.NavegacaoViagemActivity
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import com.example.vaivan.data.repository.AssinaturaBloqueadaException
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi

class RotaDetalheActivity : BaseActivity() {

    companion object {
        const val EXTRA_ROTA_ID = "rotaId"
    }

    private lateinit var rotaId: String

    private val passageiroRepository by lazy {
        PassageiroRepository(VaivanDatabase.getInstance(this).passageiroDao())
    }

    private lateinit var txtNomeRotaDetalhe: TextView
    private lateinit var btnIniciarIdaDetalhe: Button
    private lateinit var btnIniciarVoltaDetalhe: Button
    private lateinit var btnVerMapaDetalhe: Button
    private lateinit var btnTrajetoDetalhe: Button

    private var rotaCarregada: RotaEntity? = null
    private var iniciandoViagem = false
    private lateinit var containerSolicitacoesDetalhe: LinearLayout
    private lateinit var txtSemSolicitacoesDetalhe: TextView
    private lateinit var containerSecaoAvisosAusenciaDetalhe: View
    private lateinit var containerAvisosAusenciaDetalhe: LinearLayout
    private lateinit var swipeRefreshRotaDetalhe: SwipeRefreshLayout
    private lateinit var txtAlunosConfirmadosDetalhe: TextView

    // Lista de alunos confirmados/recusados (criada por código, logo abaixo do texto antigo)
    private lateinit var containerAlunosDetalhe: LinearLayout

    // Dados recebidos, para redesenhar a lista de alunos
    private var ultimasSolicitacoes: List<SolicitacaoInclusaoEntity> = emptyList()
    private var ultimasParadas: List<ParadaRotaEntity> = emptyList()

    // Todas as solicitações dessa rota (aceitas, recusadas e pendentes), vindas do Firestore.
    // O Room do motorista só guarda as pendentes nessa tela.
    private var solicitacoesDoServidor: List<SolicitacaoInclusaoEntity> = emptyList()

    // Novos campos do visual
    private lateinit var txtEscolaDetalhe: TextView
    private lateinit var txtStatusDetalhe: TextView
    private lateinit var txtSentidoDetalhe: TextView
    private lateinit var txtTurnoDetalhe: TextView
    private lateinit var txtVagasLivresDetalhe: TextView
    private lateinit var txtVagasLegendaDetalhe: TextView
    private lateinit var txtVagasOcupadasDetalhe: TextView
    private lateinit var progressVagasDetalhe: ProgressBar
    private lateinit var txtOrigemDetalhe: TextView
    private lateinit var txtDestinoDetalhe: TextView
    private lateinit var txtDistanciaDetalhe: TextView
    private lateinit var txtDuracaoDetalhe: TextView
    private lateinit var cardBairrosDetalhe: View
    private lateinit var chipGroupBairrosDetalhe: ChipGroup
    private lateinit var mapaDias: Map<String, TextView>

    private val viewModel: RotaDetalheViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(this@RotaDetalheActivity)
                val routesClient = GoogleRoutesClient(this@RotaDetalheActivity)

                val rotaRepository = RotaRepository(
                    rotaDao = db.rotaDao(),
                    paradaRotaDao = db.paradaRotaDao(),
                    routesClient = routesClient
                )

                val solicitacaoRepository = SolicitacaoInclusaoRepository(
                    this@RotaDetalheActivity,
                    db.solicitacaoInclusaoDao()
                )

                val recalcularRotaUseCase = RecalcularRotaUseCase(
                    rotaRepository = rotaRepository,
                    routesClient = routesClient
                )

                val aceitarSolicitacaoUseCase = AceitarSolicitacaoUseCase(
                    solicitacaoInclusaoRepository = solicitacaoRepository,
                    recalcularRotaUseCase = recalcularRotaUseCase,
                    rotaRepository = rotaRepository
                )

                val viagemRepository = ViagemRepository(
                    viagemDao = db.viagemDao(),
                    paradaViagemDao = db.paradaViagemDao(),
                    eventoViagemDao = db.eventoViagemDao()
                )

                val avisoAusenciaRepository = AvisoAusenciaRepository(db.avisoAusenciaDao())

                val iniciarViagemUseCase = IniciarViagemUseCase(
                    rotaRepository = rotaRepository,
                    passageiroRepository = PassageiroRepository(db.passageiroDao()),
                    viagemRepository = viagemRepository,
                    avisoAusenciaRepository = avisoAusenciaRepository
                )

                RotaDetalheViewModel(
                    rotaRepository = rotaRepository,
                    solicitacaoRepository = solicitacaoRepository,
                    aceitarSolicitacaoUseCase = aceitarSolicitacaoUseCase,
                    iniciarViagemUseCase = iniciarViagemUseCase,
                    avisoAusenciaRepository = avisoAusenciaRepository
                )
            }
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_rota_detalhe
        )

        rotaId =
            intent.getStringExtra(
                EXTRA_ROTA_ID
            ) ?: run {
                finish()
                return
            }

        findViewById<ImageButton>(
            R.id.btnVoltar
        ).setOnClickListener {
            finish()
        }

        txtNomeRotaDetalhe =
            findViewById(
                R.id.txtNomeRotaDetalhe
            )

        btnIniciarIdaDetalhe =
            findViewById(
                R.id.btnIniciarIdaDetalhe
            )

        btnIniciarVoltaDetalhe =
            findViewById(
                R.id.btnIniciarVoltaDetalhe
            )

        btnVerMapaDetalhe =
            findViewById(
                R.id.btnVerMapaDetalhe
            )

        btnTrajetoDetalhe =
            findViewById(
                R.id.btnTrajetoDetalhe
            )

        containerSolicitacoesDetalhe =
            findViewById(
                R.id.containerSolicitacoesDetalhe
            )

        containerSecaoAvisosAusenciaDetalhe = findViewById(R.id.containerSecaoAvisosAusenciaDetalhe)
        containerAvisosAusenciaDetalhe = findViewById(R.id.containerAvisosAusenciaDetalhe)

        swipeRefreshRotaDetalhe = findViewById(R.id.swipeRefreshRotaDetalhe)
        swipeRefreshRotaDetalhe.setOnRefreshListener {
            val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
            if (motoristaId == null) {
                swipeRefreshRotaDetalhe.isRefreshing = false
            } else {
                lifecycleScope.launch {
                    try {
                        viewModel.atualizarTudo(rotaId, motoristaId)
                    } finally {
                        swipeRefreshRotaDetalhe.isRefreshing = false
                    }
                }
            }
        }

        txtSemSolicitacoesDetalhe =
            findViewById(
                R.id.txtSemSolicitacoesDetalhe
            )

        txtAlunosConfirmadosDetalhe =
            findViewById(
                R.id.txtAlunosConfirmadosDetalhe
            )

        prepararContainerDeAlunos()

        txtEscolaDetalhe = findViewById(R.id.txtEscolaDetalhe)
        txtStatusDetalhe = findViewById(R.id.txtStatusDetalhe)
        txtSentidoDetalhe = findViewById(R.id.txtSentidoDetalhe)
        txtTurnoDetalhe = findViewById(R.id.txtTurnoDetalhe)
        txtVagasLivresDetalhe = findViewById(R.id.txtVagasLivresDetalhe)
        txtVagasLegendaDetalhe = findViewById(R.id.txtVagasLegendaDetalhe)
        txtVagasOcupadasDetalhe = findViewById(R.id.txtVagasOcupadasDetalhe)
        progressVagasDetalhe = findViewById(R.id.progressVagasDetalhe)
        txtOrigemDetalhe = findViewById(R.id.txtOrigemDetalhe)
        txtDestinoDetalhe = findViewById(R.id.txtDestinoDetalhe)
        txtDistanciaDetalhe = findViewById(R.id.txtDistanciaDetalhe)
        txtDuracaoDetalhe = findViewById(R.id.txtDuracaoDetalhe)
        cardBairrosDetalhe = findViewById(R.id.cardBairrosDetalhe)
        chipGroupBairrosDetalhe = findViewById(R.id.chipGroupBairrosDetalhe)

        mapaDias = mapOf(
            "SEG" to findViewById<TextView>(R.id.diaSeg),
            "TER" to findViewById<TextView>(R.id.diaTer),
            "QUA" to findViewById<TextView>(R.id.diaQua),
            "QUI" to findViewById<TextView>(R.id.diaQui),
            "SEX" to findViewById<TextView>(R.id.diaSex),
            "SAB" to findViewById<TextView>(R.id.diaSab),
            "DOM" to findViewById<TextView>(R.id.diaDom)
        )

        // Trajeto da rota (ponto A -> escola), sempre disponível
        btnTrajetoDetalhe.setOnClickListener {

            val intent =
                Intent(
                    this,
                    VisualizarRotaActivity::class.java
                )

            intent.putExtra(
                VisualizarRotaActivity.EXTRA_ROTA_ID,
                rotaId
            )

            startActivity(
                intent
            )
        }

        // Rota com os alunos (só depois de ter alunos confirmados)
        btnVerMapaDetalhe.setOnClickListener {

            val intent =
                Intent(
                    this,
                    RotaCalculadaActivity::class.java
                )

            intent.putExtra(
                RotaCalculadaActivity.EXTRA_ROTA_ID,
                rotaId
            )

            startActivity(
                intent
            )
        }

        btnIniciarIdaDetalhe.setOnClickListener {
            rotaCarregada?.let { confirmarEIniciarViagem(it.id, ViagemRepository.DIRECAO_IDA, btnIniciarIdaDetalhe) }
        }

        btnIniciarVoltaDetalhe.setOnClickListener {
            rotaCarregada?.let { confirmarEIniciarViagem(it.id, ViagemRepository.DIRECAO_VOLTA, btnIniciarVoltaDetalhe) }
        }

        observarRota()
        observarSolicitacoes()
        observarAvisosAusencia()
        observarParadas()

        carregarParadasDoServidor()
        carregarSolicitacoesDoServidor()

        aplicarGapsDeSistema()
    }

    /**
     * Esconde o texto antigo "Alunos confirmados" e cria, logo abaixo dele,
     * um container onde cada aluno vira um card.
     */
    private fun prepararContainerDeAlunos() {

        txtAlunosConfirmadosDetalhe.visibility = View.GONE

        containerAlunosDetalhe = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val pai = txtAlunosConfirmadosDetalhe.parent as ViewGroup
        val indice = pai.indexOfChild(txtAlunosConfirmadosDetalhe)

        pai.addView(
            containerAlunosDetalhe,
            indice + 1,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun observarRota() {

        lifecycleScope.launch {

            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                viewModel
                    .observarRota(
                        rotaId
                    )
                    .collect { rota ->

                        preencherRota(
                            rota
                        )
                    }
            }
        }
    }

    // ---------------------------------------------------------
    // PREENCHER OS DADOS DA ROTA
    // ---------------------------------------------------------

    private fun preencherRota(
        rota: RotaEntity?
    ) {

        if (rota == null) {
            return
        }

        rotaCarregada = rota

        txtNomeRotaDetalhe.text = rota.nome

        // Cartão principal
        txtEscolaDetalhe.text = rota.escolaNome.ifBlank { rota.destinoNome }
        txtStatusDetalhe.text = if (rota.status == "ATIVA") "Ativa" else "Arquivada"
        txtSentidoDetalhe.text = rotuloSentido(rota.tipoTrajeto)
        txtTurnoDetalhe.text = traduzirTurno(rota.turno)

        // Dias da semana: os da rota ficam laranja, os outros cinza
        val diasDaRota = rota.diasSemana
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        mapaDias.forEach { (chave, textView) ->
            val ativo = chave in diasDaRota

            textView.setBackgroundResource(
                if (ativo) R.drawable.bg_dia_ativo else R.drawable.bg_dia_inativo
            )
            textView.setTextColor(
                if (ativo) {
                    ContextCompat.getColor(this, android.R.color.white)
                } else {
                    ContextCompat.getColor(this, R.color.light_gray)
                }
            )
        }

        // Vagas
        val livres = (rota.capacidadeTotal - rota.vagasOcupadas).coerceAtLeast(0)

        txtVagasLivresDetalhe.text = livres.toString()
        txtVagasLegendaDetalhe.text = if (livres == 1) "vaga livre" else "vagas livres"
        txtVagasOcupadasDetalhe.text =
            "${rota.vagasOcupadas} de ${rota.capacidadeTotal} ocupadas"

        progressVagasDetalhe.max = rota.capacidadeTotal.coerceAtLeast(1)
        progressVagasDetalhe.progress = rota.vagasOcupadas

        // Percurso
        txtOrigemDetalhe.text =
            rota.origemEndereco.ifBlank { "Não informado" }
        txtDestinoDetalhe.text =
            rota.destinoEndereco.ifBlank { rota.destinoNome }

        txtDistanciaDetalhe.text =
            if (rota.distanciaMetros > 0) {
                String.format(
                    Locale("pt", "BR"),
                    "%.1f km",
                    rota.distanciaMetros / 1000.0
                )
            } else {
                "—"
            }

        txtDuracaoDetalhe.text = formatarDuracao(rota.duracaoSegundos)

        // Bairros atendidos (etiquetas arredondadas)
        chipGroupBairrosDetalhe.removeAllViews()

        if (rota.bairrosAtendidos.isEmpty()) {
            cardBairrosDetalhe.visibility = View.GONE
        } else {
            cardBairrosDetalhe.visibility = View.VISIBLE

            rota.bairrosAtendidos.forEach { chave ->
                val etiqueta = TextView(this).apply {
                    text = formatarBairro(chave)
                    textSize = 13f
                    setTextColor(
                        ContextCompat.getColor(this@RotaDetalheActivity, R.color.dark_gray)
                    )
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    setBackgroundResource(R.drawable.bg_chip_bairro)
                }
                chipGroupBairrosDetalhe.addView(etiqueta)
            }
        }

        btnVerMapaDetalhe.isEnabled =
            rota.vagasOcupadas > 0

        val habilitado = rota.vagasOcupadas > 0 && rota.status == "ATIVA" && !iniciandoViagem

        when (rota.tipoTrajeto) {
            "IDA" -> {
                btnIniciarIdaDetalhe.visibility = View.VISIBLE
                btnIniciarVoltaDetalhe.visibility = View.GONE
            }
            "VOLTA" -> {
                btnIniciarIdaDetalhe.visibility = View.GONE
                btnIniciarVoltaDetalhe.visibility = View.VISIBLE
            }
            else -> { // IDA_E_VOLTA
                btnIniciarIdaDetalhe.visibility = View.VISIBLE
                btnIniciarVoltaDetalhe.visibility = View.VISIBLE
            }
        }

        btnIniciarIdaDetalhe.isEnabled = habilitado
        btnIniciarVoltaDetalhe.isEnabled = habilitado
    }

    // ---------------------------------------------------------
    // INICIAR ROTA (viagem)
    // ---------------------------------------------------------

    private fun confirmarEIniciarViagem(rotaId: String, direcao: String, botaoTocado: Button) {
        if (iniciandoViagem) return

        val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
        if (motoristaId == null) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_LONG).show()
            return
        }

        iniciandoViagem = true
        btnIniciarIdaDetalhe.isEnabled = false
        btnIniciarVoltaDetalhe.isEnabled = false
        botaoTocado.text = "Iniciando..."

        lifecycleScope.launch {
            try {
                val viagem = viewModel.iniciarViagem(
                    rotaId = rotaId,
                    motoristaId = motoristaId,
                    direcao = direcao
                )

                val intent = Intent(this@RotaDetalheActivity, NavegacaoViagemActivity::class.java)
                intent.putExtra(NavegacaoViagemActivity.EXTRA_VIAGEM_ID, viagem.id)
                startActivity(intent)

            } catch (erro: AssinaturaBloqueadaException) {

                AssinaturaUi.mostrarBloqueio(
                    this@RotaDetalheActivity,
                    erro.message ?: "Assinatura necessária para iniciar a viagem."
                )

            } catch (erro: Exception) {

                Toast.makeText(
                    this@RotaDetalheActivity,
                    "Não foi possível iniciar a viagem: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()

            } finally {
                iniciandoViagem = false
                botaoTocado.text = if (botaoTocado === btnIniciarIdaDetalhe) "Iniciar Ida" else "Iniciar Volta"
                val rota = rotaCarregada
                val habilitado = rota != null && rota.vagasOcupadas > 0 && rota.status == "ATIVA"
                btnIniciarIdaDetalhe.isEnabled = habilitado
                btnIniciarVoltaDetalhe.isEnabled = habilitado
            }
        }
    }

    // ---------------------------------------------------------
    // AUXILIARES DE FORMATAÇÃO
    // ---------------------------------------------------------

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()

    private fun rotuloSentido(tipoTrajeto: String): String =
        when (tipoTrajeto) {
            "IDA" -> "Ida"
            "VOLTA" -> "Volta"
            "IDA_E_VOLTA" -> "Ida e volta"
            else -> tipoTrajeto
        }

    private fun traduzirTurno(turno: String): String =
        when (turno) {
            "MANHA" -> "Manhã"
            "TARDE" -> "Tarde"
            "NOITE" -> "Noite"
            else -> turno
        }

    private fun formatarDuracao(segundos: Int): String {
        if (segundos <= 0) return "—"

        val minutosTotais = (segundos / 60).coerceAtLeast(1)
        val horas = minutosTotais / 60
        val minutos = minutosTotais % 60

        return when {
            horas == 0 -> "$minutosTotais min"
            minutos == 0 -> "${horas}h"
            else -> "${horas}h ${minutos}min"
        }
    }

    /** A chave do bairro é normalizada; aqui deixamos ela mais bonita para exibir. */
    private fun formatarBairro(chave: String): String {
        val base = chave
            .substringBefore("|")
            .substringBefore("#")
            .substringBefore(";")
            .replace("_", " ")
            .trim()

        return base
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { palavra ->
                palavra.replaceFirstChar { it.uppercase() }
            }
    }

    // ---------------------------------------------------------
    // SOLICITAÇÕES PENDENTES
    // ---------------------------------------------------------

    private fun observarSolicitacoes() {

        lifecycleScope.launch {

            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                val motoristaId =
                    FirebaseAuth
                        .getInstance()
                        .currentUser
                        ?.uid
                        ?: return@repeatOnLifecycle

                viewModel
                    .observarSolicitacoes(
                        motoristaId
                    )
                    .collect { todas ->

                        ultimasSolicitacoes = todas.filter {
                            it.rotaId == rotaId
                        }

                        renderizarSolicitacoes(ultimasSolicitacoes)

                        // Quando algo muda (aceitou, recusou, chegou uma nova),
                        // atualiza também a lista de confirmados/recusados.
                        carregarSolicitacoesDoServidor()
                    }
            }
        }
    }

    private fun renderizarSolicitacoes(
        solicitacoes: List<SolicitacaoInclusaoEntity>
    ) {

        containerSolicitacoesDetalhe.removeAllViews()

        val pendentes = solicitacoes.filter { it.status == "PENDENTE" }

        txtSemSolicitacoesDetalhe.visibility =
            if (pendentes.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)

        pendentes.forEach { solicitacao ->

            val itemView =
                inflater.inflate(
                    R.layout.item_solicitacao_pendente,
                    containerSolicitacoesDetalhe,
                    false
                )

            itemView
                .findViewById<TextView>(
                    R.id.txtNomeAlunoSolicitacao
                )
                .text =
                solicitacao.nomePassageiro

            val imgFotoAluno =
                itemView.findViewById<ShapeableImageView>(
                    R.id.imgFotoAlunoSolicitacao
                )

            lifecycleScope.launch {
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

            itemView
                .findViewById<TextView>(
                    R.id.txtEnderecoSolicitacao
                )
                .text =
                solicitacao.enderecoEmbarque

            itemView
                .findViewById<TextView>(
                    R.id.txtHorarioSolicitacao
                )

                .text =
                "Horário desejado: " +
                        solicitacao.horarioDesejado

            // Mensagem do responsável (o bloco só aparece se ele escreveu algo)
            val mensagem = solicitacao.mensagem.trim()
            val blocoMensagem = itemView.findViewById<View>(R.id.containerMensagemSolicitacao)

            if (mensagem.isBlank()) {
                blocoMensagem.visibility = View.GONE
            } else {
                blocoMensagem.visibility = View.VISIBLE
                itemView.findViewById<TextView>(R.id.txtMensagemSolicitacao).text = mensagem
            }

            val btnAceitar =
                itemView.findViewById<Button>(
                    R.id.btnAceitarSolicitacao
                )

            val btnRecusar =
                itemView.findViewById<Button>(
                    R.id.btnRecusarSolicitacao
                )

            btnAceitar.setOnClickListener {

                btnAceitar.isEnabled = false
                btnRecusar.isEnabled = false

                lifecycleScope.launch {

                    try {

                        viewModel
                            .aceitarSolicitacao(
                                solicitacao
                            )

                        Toast.makeText(
                            this@RotaDetalheActivity,
                            "Aluno incluído na rota!",
                            Toast.LENGTH_SHORT
                        ).show()

                    } catch (erro: Exception) {

                        btnAceitar.isEnabled = true
                        btnRecusar.isEnabled = true

                        Log.e(
                            "AceitarRota",
                            "Erro ao aceitar solicitação",
                            erro
                        )

                        Toast.makeText(
                            this@RotaDetalheActivity,
                            "Erro: ${erro.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            btnRecusar.setOnClickListener {

                btnAceitar.isEnabled = false
                btnRecusar.isEnabled = false

                lifecycleScope.launch {

                    try {

                        viewModel
                            .recusarSolicitacao(
                                solicitacao
                            )

                        Toast.makeText(
                            this@RotaDetalheActivity,
                            "Solicitação recusada.",
                            Toast.LENGTH_SHORT
                        ).show()

                    } catch (erro: Exception) {

                        btnAceitar.isEnabled = true
                        btnRecusar.isEnabled = true

                        Toast.makeText(
                            this@RotaDetalheActivity,
                            "Erro: ${erro.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            containerSolicitacoesDetalhe
                .addView(
                    itemView
                )
        }
    }

    // ---------------------------------------------------------
    // AVISOS DE AUSÊNCIA (responsável avisou com antecedência)
    // ---------------------------------------------------------

    private fun observarAvisosAusencia() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
                    ?: return@repeatOnLifecycle

                viewModel.observarAvisosAusencia(motoristaId).collect { todos ->
                    renderizarAvisosAusencia(todos.filter { it.rotaId == rotaId })
                }
            }
        }
    }

    /** Só leitura — é um aviso, não uma solicitação pra aceitar/recusar. A
     * exclusão da parada já aconteceu automaticamente em IniciarViagemUseCase. */
    private fun renderizarAvisosAusencia(avisos: List<AvisoAusenciaEntity>) {
        containerAvisosAusenciaDetalhe.removeAllViews()

        containerSecaoAvisosAusenciaDetalhe.visibility =
            if (avisos.isEmpty()) View.GONE else View.VISIBLE

        avisos.forEach { aviso ->
            val descricao = when (aviso.tipo) {
                AvisoAusenciaRepository.TIPO_DIA_INTEIRO -> "não vai usar o transporte hoje (ida e volta)"
                AvisoAusenciaRepository.TIPO_SOMENTE_VOLTA -> "não vai usar o transporte na volta hoje"
                else -> "não vai usar o transporte na ida hoje"
            }

            val linha = TextView(this).apply {
                text = "${aviso.nomePassageiro} $descricao"
                setTextColor(ContextCompat.getColor(this@RotaDetalheActivity, R.color.dark_gray))
                setBackgroundResource(R.drawable.bg_card_rounded)
                setPadding(16.dpParaPx(), 12.dpParaPx(), 16.dpParaPx(), 12.dpParaPx())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dpParaPx() }
            }

            containerAvisosAusenciaDetalhe.addView(linha)
        }
    }

    private fun Int.dpParaPx(): Int = (this * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------
    // ALUNOS CONFIRMADOS E RECUSADOS
    // ---------------------------------------------------------

    private fun observarParadas() {

        lifecycleScope.launch {

            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                viewModel
                    .observarParadas(
                        rotaId
                    )
                    .collect { paradas: List<ParadaRotaEntity> ->

                        ultimasParadas = paradas
                        renderizarAlunos()
                    }
            }
        }
    }

    /** Traz as paradas (alunos confirmados) do Firestore para o Room. */
    private fun carregarParadasDoServidor() {

        lifecycleScope.launch {
            try {
                val db = VaivanDatabase.getInstance(this@RotaDetalheActivity)

                val rotaRepository = RotaRepository(
                    db.rotaDao(),
                    db.paradaRotaDao(),
                    GoogleRoutesClient(this@RotaDetalheActivity)
                )

                val paradas = rotaRepository.consultarParadas(rotaId)

                Log.d("DETALHE_ALUNOS", "Firestore devolveu ${paradas.size} paradas para $rotaId")

                // Espelha o servidor no Room; a lista observa o Room e redesenha sozinha
                db.paradaRotaDao().deleteByRota(rotaId)
                db.paradaRotaDao().upsertAll(paradas)

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

                Log.d(
                    "DETALHE_ALUNOS",
                    "Firestore devolveu ${solicitacoesDoServidor.size} solicitações para $rotaId"
                )

                renderizarAlunos()

            } catch (e: Exception) {
                Log.e("DETALHE_ALUNOS", "Erro ao buscar solicitações", e)
            }
        }
    }

    /** Desenha "Alunos confirmados" (com botão de mensagem) e "Recusados". */
    private fun renderizarAlunos() {

        Log.d(
            "DETALHE_ALUNOS",
            "renderizarAlunos: paradas=${ultimasParadas.size} " +
                    "solicitacoes=${solicitacoesDoServidor.size}"
        )

        containerAlunosDetalhe.removeAllViews()

        // --- Confirmados -------------------------------------------------
        containerAlunosDetalhe.addView(criarTituloDeSecao("Alunos confirmados"))

        if (ultimasParadas.isEmpty()) {

            containerAlunosDetalhe.addView(
                TextView(this).apply {
                    text = "Nenhum aluno confirmado ainda."
                    textSize = 14f
                    setPadding(0, dp(4), 0, dp(8))
                }
            )

        } else {

            ultimasParadas
                .sortedBy { it.ordem }
                .forEach { parada ->
                    containerAlunosDetalhe.addView(criarCardAlunoConfirmado(parada))
                }
        }

        // --- Recusados ---------------------------------------------------
        val recusadas = solicitacoesDoServidor.filter { it.status == "RECUSADA" }

        if (recusadas.isNotEmpty()) {

            containerAlunosDetalhe.addView(criarTituloDeSecao("Recusados"))

            recusadas.forEach { solicitacao ->
                containerAlunosDetalhe.addView(
                    TextView(this).apply {
                        text = "${solicitacao.nomePassageiro} — ${solicitacao.enderecoEmbarque}"
                        textSize = 14f
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                    }
                )
            }
        }
    }

    private fun criarTituloDeSecao(titulo: String): TextView =
        TextView(this).apply {
            text = titulo
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        }

    private fun criarCardAlunoConfirmado(parada: ParadaRotaEntity): View {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(R.drawable.bg_beige_round_20)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        card.addView(
            TextView(this).apply {
                text = "${parada.ordem + 1}. ${parada.nomePassageiro}"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        val endereco = parada.endereco.orEmpty()
        val detalhe = buildString {
            if (endereco.isNotBlank()) append("Embarque: $endereco\n")
            append("Horário estimado: ~${parada.horarioEstimadoMinutos} min")
        }

        card.addView(
            TextView(this).apply {
                text = detalhe
                textSize = 13f
                setPadding(0, dp(4), 0, dp(8))
            }
        )

        card.addView(
            Button(this).apply {
                text = "Enviar mensagem"
                setOnClickListener { abrirChatComAluno(parada, this) }
            }
        )

        return card
    }

    // ---------------------------------------------------------
    // CHAT COM O RESPONSÁVEL
    // ---------------------------------------------------------

    private fun abrirChatComAluno(parada: ParadaRotaEntity, botao: Button) {

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
    private suspend fun encontrarSolicitacaoAceita(
        parada: ParadaRotaEntity
    ): SolicitacaoInclusaoEntity? {

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

        return documento
            .toObject(SolicitacaoInclusaoEntity::class.java)
            ?.copy(id = documento.id)
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
            }
        )
    }
}