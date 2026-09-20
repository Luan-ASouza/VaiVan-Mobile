package com.example.vaivan.ui.motorista.rotas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
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

    private val rotaRepository by lazy {
        val db = VaivanDatabase.getInstance(this)
        RotaRepository(this, db.rotaDao(), db.paradaRotaDao())
    }

    private val solicitacaoRepository by lazy {
        SolicitacaoInclusaoRepository(this, VaivanDatabase.getInstance(this).solicitacaoInclusaoDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rota_detalhe)

        rotaId = intent.getStringExtra(EXTRA_ROTA_ID) ?: run {
            finish(); return
        }

        findViewById<ImageButton>(R.id.btnVoltar).setOnClickListener { finish() }

        txtNomeRotaDetalhe = findViewById(R.id.txtNomeRotaDetalhe)
        txtResumoRotaDetalhe = findViewById(R.id.txtResumoRotaDetalhe)
        btnVerMapaDetalhe = findViewById(R.id.btnVerMapaDetalhe)
        containerSolicitacoesDetalhe = findViewById(R.id.containerSolicitacoesDetalhe)
        txtSemSolicitacoesDetalhe = findViewById(R.id.txtSemSolicitacoesDetalhe)
        txtAlunosConfirmadosDetalhe = findViewById(R.id.txtAlunosConfirmadosDetalhe)

        btnVerMapaDetalhe.setOnClickListener {
            val intent = Intent(this, RotaCalculadaActivity::class.java)
            intent.putExtra(RotaCalculadaActivity.EXTRA_ROTA_ID, rotaId)
            startActivity(intent)
        }

        observarRota()
        observarSolicitacoes()
        observarParadas()

        applyTopAndBottomGaps(findViewById(android.R.id.content))
    }

    private fun observarRota() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rotaRepository.observarRota(rotaId).collect { rota ->
                    preencherRota(rota)
                }
            }
        }
    }

    private fun preencherRota(rota: RotaEntity?) {
        if (rota == null) return
        txtNomeRotaDetalhe.text = rota.nome
        txtResumoRotaDetalhe.text =
            "Destino: ${rota.destinoEndereco}\nVagas: ${rota.vagasOcupadas}/${rota.capacidadeTotal}"
        btnVerMapaDetalhe.isEnabled = rota.vagasOcupadas > 0
    }

    private fun observarSolicitacoes() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                solicitacaoRepository.observarPendentesPorMotorista(
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@repeatOnLifecycle
                ).collect { todas ->
                    renderizarSolicitacoes(todas.filter { it.rotaId == rotaId })
                }
            }
        }
    }

    private fun renderizarSolicitacoes(solicitacoes: List<SolicitacaoInclusaoEntity>) {
        containerSolicitacoesDetalhe.removeAllViews()

        if (solicitacoes.isEmpty()) {
            txtSemSolicitacoesDetalhe.visibility = android.view.View.VISIBLE
            return
        }
        txtSemSolicitacoesDetalhe.visibility = android.view.View.GONE

        val inflater = LayoutInflater.from(this)
        solicitacoes.forEach { solicitacao ->
            val itemView = inflater.inflate(R.layout.item_solicitacao_pendente, containerSolicitacoesDetalhe, false)

            itemView.findViewById<TextView>(R.id.txtNomeAlunoSolicitacao).text = solicitacao.nomePassageiro
            itemView.findViewById<TextView>(R.id.txtEnderecoSolicitacao).text = solicitacao.enderecoEmbarque
            itemView.findViewById<TextView>(R.id.txtHorarioSolicitacao).text =
                "Horário desejado: ${solicitacao.horarioDesejado}"

            val btnAceitar = itemView.findViewById<Button>(R.id.btnAceitarSolicitacao)
            val btnRecusar = itemView.findViewById<Button>(R.id.btnRecusarSolicitacao)

            btnAceitar.setOnClickListener {
                btnAceitar.isEnabled = false; btnRecusar.isEnabled = false
                solicitacaoRepository.aceitarAsync(
                    solicitacao,
                    onSuccess = { Toast.makeText(this, "Aluno incluído na rota!", Toast.LENGTH_SHORT).show() },
                    onError = { erro ->
                        btnAceitar.isEnabled = true; btnRecusar.isEnabled = true
                        Toast.makeText(this, "Erro: ${erro.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }

            btnRecusar.setOnClickListener {
                btnAceitar.isEnabled = false; btnRecusar.isEnabled = false
                solicitacaoRepository.recusarAsync(
                    solicitacao,
                    onSuccess = { Toast.makeText(this, "Solicitação recusada.", Toast.LENGTH_SHORT).show() },
                    onError = { erro ->
                        btnAceitar.isEnabled = true; btnRecusar.isEnabled = true
                        Toast.makeText(this, "Erro: ${erro.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }

            containerSolicitacoesDetalhe.addView(itemView)
        }
    }

    private fun observarParadas() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rotaRepository.observarParadas(rotaId).collect { paradas: List<ParadaRotaEntity> ->
                    txtAlunosConfirmadosDetalhe.text = if (paradas.isEmpty()) {
                        "Nenhum aluno confirmado ainda."
                    } else {
                        paradas.sortedBy { it.ordem }
                            .joinToString("\n") { "${it.ordem + 1}. ${it.nomePassageiro} — embarque em ~${it.horarioEstimadoMinutos} min" }
                    }
                }
            }
        }
    }
}