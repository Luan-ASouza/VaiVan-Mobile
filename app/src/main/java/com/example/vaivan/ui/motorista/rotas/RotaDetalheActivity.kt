package com.example.vaivan.ui.motorista.rotas

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.data.repository.ViagemRepository
import com.example.vaivan.domain.usecase.rota.RecalcularRotaUseCase
import com.example.vaivan.domain.usecase.solicitacao.AceitarSolicitacaoUseCase
import com.example.vaivan.domain.usecase.viagem.IniciarViagemUseCase
import com.example.vaivan.ui.motorista.rotas.navegacao.NavegacaoViagemActivity
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale

class RotaDetalheActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROTA_ID = "rotaId"
    }

    private lateinit var rotaId: String

    private lateinit var txtNomeRotaDetalhe: TextView
    private lateinit var btnIniciarRotaDetalhe: Button
    private lateinit var btnVerMapaDetalhe: Button
    private lateinit var btnTrajetoDetalhe: Button

    private var rotaCarregada: RotaEntity? = null
    private var iniciandoViagem = false
    private lateinit var containerSolicitacoesDetalhe: LinearLayout
    private lateinit var txtSemSolicitacoesDetalhe: TextView
    private lateinit var txtAlunosConfirmadosDetalhe: TextView

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

    private val viewModel: RotaDetalheViewModel by lazy {

        val db =
            VaivanDatabase.getInstance(
                this
            )

        val routesClient =
            GoogleRoutesClient(
                this
            )

        val rotaRepository =
            RotaRepository(
                rotaDao =
                    db.rotaDao(),
                paradaRotaDao =
                    db.paradaRotaDao(),
                routesClient =
                    routesClient
            )

        val solicitacaoRepository =
            SolicitacaoInclusaoRepository(
                this,
                db.solicitacaoInclusaoDao()
            )

        val recalcularRotaUseCase =
            RecalcularRotaUseCase(
                rotaRepository =
                    rotaRepository,
                routesClient =
                    routesClient
            )

        val aceitarSolicitacaoUseCase =
            AceitarSolicitacaoUseCase(
                solicitacaoInclusaoRepository = solicitacaoRepository,
                recalcularRotaUseCase = recalcularRotaUseCase,
                rotaRepository = rotaRepository
            )

        val viagemRepository =
            ViagemRepository(
                viagemDao = db.viagemDao(),
                paradaViagemDao = db.paradaViagemDao(),
                eventoViagemDao = db.eventoViagemDao()
            )

        val iniciarViagemUseCase =
            IniciarViagemUseCase(
                rotaRepository = rotaRepository,
                passageiroRepository = PassageiroRepository(db.passageiroDao()),
                viagemRepository = viagemRepository
            )

        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {

                override fun <T : ViewModel>
                        create(
                    modelClass: Class<T>
                ): T {

                    return RotaDetalheViewModel(
                        rotaRepository =
                            rotaRepository,
                        solicitacaoRepository =
                            solicitacaoRepository,
                        aceitarSolicitacaoUseCase =
                            aceitarSolicitacaoUseCase,
                        iniciarViagemUseCase =
                            iniciarViagemUseCase
                    ) as T
                }
            }
        )[RotaDetalheViewModel::class.java]
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

        btnIniciarRotaDetalhe =
            findViewById(
                R.id.btnIniciarRotaDetalhe
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

        txtSemSolicitacoesDetalhe =
            findViewById(
                R.id.txtSemSolicitacoesDetalhe
            )

        txtAlunosConfirmadosDetalhe =
            findViewById(
                R.id.txtAlunosConfirmadosDetalhe
            )

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

        btnIniciarRotaDetalhe.setOnClickListener {
            iniciarRota()
        }

        observarRota()
        observarSolicitacoes()
        observarParadas()

        applyTopAndBottomGaps(
            findViewById(
                android.R.id.content
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

        btnIniciarRotaDetalhe.isEnabled =
            rota.vagasOcupadas > 0 && rota.status == "ATIVA" && !iniciandoViagem
    }

    // ---------------------------------------------------------
    // INICIAR ROTA (viagem)
    // ---------------------------------------------------------

    private fun iniciarRota() {
        val rota = rotaCarregada ?: return

        if (rota.tipoTrajeto == "IDA_E_VOLTA") {
            MaterialAlertDialogBuilder(this)
                .setTitle("Qual sentido?")
                .setItems(arrayOf("Ida (buscar alunos)", "Volta (levar alunos)")) { _, opcao ->
                    val direcao = if (opcao == 0) ViagemRepository.DIRECAO_IDA else ViagemRepository.DIRECAO_VOLTA
                    confirmarEIniciarViagem(rota.id, direcao)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            val direcao = if (rota.tipoTrajeto == "VOLTA") {
                ViagemRepository.DIRECAO_VOLTA
            } else {
                ViagemRepository.DIRECAO_IDA
            }
            confirmarEIniciarViagem(rota.id, direcao)
        }
    }

    private fun confirmarEIniciarViagem(rotaId: String, direcao: String) {
        if (iniciandoViagem) return

        val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
        if (motoristaId == null) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_LONG).show()
            return
        }

        iniciandoViagem = true
        btnIniciarRotaDetalhe.isEnabled = false
        btnIniciarRotaDetalhe.text = "Iniciando..."

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

            } catch (erro: Exception) {

                Toast.makeText(
                    this@RotaDetalheActivity,
                    "Não foi possível iniciar a viagem: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()

            } finally {
                iniciandoViagem = false
                btnIniciarRotaDetalhe.text = "Iniciar rota"
                rotaCarregada?.let { btnIniciarRotaDetalhe.isEnabled = it.vagasOcupadas > 0 && it.status == "ATIVA" }
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
    // SOLICITAÇÕES
    // ---------------------------------------------------------

    private fun observarSolicitacoes() {

        lifecycleScope.launch {

            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                val motoristaId =
                    com.google.firebase.auth.FirebaseAuth
                        .getInstance()
                        .currentUser
                        ?.uid
                        ?: return@repeatOnLifecycle

                viewModel
                    .observarSolicitacoes(
                        motoristaId
                    )
                    .collect { todas ->

                        renderizarSolicitacoes(
                            todas.filter {
                                it.rotaId == rotaId
                            }
                        )
                    }
            }
        }
    }

    private fun renderizarSolicitacoes(
        solicitacoes:
        List<SolicitacaoInclusaoEntity>
    ) {

        containerSolicitacoesDetalhe
            .removeAllViews()

        if (solicitacoes.isEmpty()) {

            txtSemSolicitacoesDetalhe.visibility =
                View.VISIBLE

            return
        }

        txtSemSolicitacoesDetalhe.visibility =
            View.GONE

        val inflater =
            LayoutInflater.from(
                this
            )

        solicitacoes.forEach { solicitacao ->

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
    // ALUNOS CONFIRMADOS
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

                        txtAlunosConfirmadosDetalhe.text =
                            if (paradas.isEmpty()) {

                                "Nenhum aluno confirmado ainda."

                            } else {

                                paradas
                                    .sortedBy {
                                        it.ordem
                                    }
                                    .joinToString("\n") {

                                        "${it.ordem + 1}. " +
                                                "${it.nomePassageiro} — " +
                                                "embarque em ~" +
                                                "${it.horarioEstimadoMinutos} min"
                                    }
                            }
                    }
            }
        }
    }
}