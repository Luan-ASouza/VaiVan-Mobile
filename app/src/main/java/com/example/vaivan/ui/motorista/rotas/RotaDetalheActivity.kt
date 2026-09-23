package com.example.vaivan.ui.motorista.rotas

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.example.vaivan.domain.usecase.rota.RecalcularRotaUseCase
import com.example.vaivan.domain.usecase.solicitacao.AceitarSolicitacaoUseCase
import kotlinx.coroutines.launch

class RotaDetalheActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROTA_ID = "rotaId"
    }

    private lateinit var rotaId: String

    private lateinit var txtNomeRotaDetalhe: TextView
    private lateinit var txtResumoRotaDetalhe: TextView
    private lateinit var btnVerMapaDetalhe: Button
    private lateinit var containerSolicitacoesDetalhe: LinearLayout
    private lateinit var txtSemSolicitacoesDetalhe: TextView
    private lateinit var txtAlunosConfirmadosDetalhe: TextView

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
                recalcularRotaUseCase = recalcularRotaUseCase
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
                            aceitarSolicitacaoUseCase
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

        txtResumoRotaDetalhe =
            findViewById(
                R.id.txtResumoRotaDetalhe
            )

        btnVerMapaDetalhe =
            findViewById(
                R.id.btnVerMapaDetalhe
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

    private fun preencherRota(
        rota: RotaEntity?
    ) {

        if (rota == null) {
            return
        }

        txtNomeRotaDetalhe.text =
            rota.nome

        txtResumoRotaDetalhe.text =
            "Destino: ${rota.destinoEndereco}\n" +
                    "Vagas: " +
                    "${rota.vagasOcupadas}/" +
                    rota.capacidadeTotal

        btnVerMapaDetalhe.isEnabled =
            rota.vagasOcupadas > 0
    }

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
                android.view.View.VISIBLE

            return
        }

        txtSemSolicitacoesDetalhe.visibility =
            android.view.View.GONE

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