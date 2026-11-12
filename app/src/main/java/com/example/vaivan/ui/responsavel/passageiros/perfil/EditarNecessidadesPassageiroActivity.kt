package com.example.vaivan.ui.responsavel.passageiros.perfil

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.ui.comum.perfil.acompanharEdicao
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/** Necessidades especiais do passageiro: Sim/Não + descrição quando Sim. */
class EditarNecessidadesPassageiroActivity : BaseActivity() {

    companion object {
        fun intent(context: Context, passageiroId: String): Intent =
            Intent(context, EditarNecessidadesPassageiroActivity::class.java)
                .putExtra(PerfilPassageiroActivity.EXTRA_PASSAGEIRO_ID, passageiroId)
    }

    private val viewModel: EditarPassageiroViewModel by viewModels {
        viewModelFactory {
            initializer {
                EditarPassageiroViewModel(
                    EditarPassageiroViewModel.criarRepository(this@EditarNecessidadesPassageiroActivity),
                    intent.getStringExtra(PerfilPassageiroActivity.EXTRA_PASSAGEIRO_ID).orEmpty()
                )
            }
        }
    }

    private lateinit var btnSim: MaterialButton
    private lateinit var btnNao: MaterialButton
    private lateinit var containerDescricao: View
    private lateinit var edtDescricao: TextInputEditText
    private lateinit var btnSalvar: MaterialButton
    private lateinit var progress: ProgressBar

    private var temNecessidade = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_necessidades_passageiro)
        aplicarGapsDeSistema(aplicarBase = false)
        WindowInsetsUtil.aplicarInsets(findViewById(R.id.conteudoEdicaoPerfil), aplicarTopo = false)

        btnSim = findViewById(R.id.btnNecessidadeSim)
        btnNao = findViewById(R.id.btnNecessidadeNao)
        containerDescricao = findViewById(R.id.containerDescricaoNecessidade)
        edtDescricao = findViewById(R.id.edtDescricaoNecessidade)
        btnSalvar = findViewById(R.id.btnSalvarEdicaoPerfil)
        progress = findViewById(R.id.progressEdicaoPerfil)

        btnSim.setOnClickListener { selecionar(true) }
        btnNao.setOnClickListener { selecionar(false) }
        btnSalvar.setOnClickListener { validarESalvar() }

        selecionar(false)

        acompanharEdicao(
            viewModel = viewModel,
            aoMudarOcupado = { ocupado ->
                progress.visibility = if (ocupado) View.VISIBLE else View.GONE
                btnSalvar.text = if (ocupado) "" else "Salvar"
                btnSalvar.isEnabled = !ocupado
            },
            aoCarregar = { passageiro ->
                edtDescricao.setText(passageiro.descricaoNecessidades.orEmpty())
                selecionar(passageiro.necessidadesEspeciais)
            }
        )
    }

    private fun selecionar(sim: Boolean) {
        temNecessidade = sim

        val ativo = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_primary))
        val inativo = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.light_gray))
        btnSim.backgroundTintList = if (sim) ativo else inativo
        btnNao.backgroundTintList = if (sim) inativo else ativo

        containerDescricao.visibility = if (sim) View.VISIBLE else View.GONE
    }

    private fun validarESalvar() {
        // Descrição é opcional, como no cadastro (sem ela a lista mostra
        // "Possui necessidade especial").
        val descricao = edtDescricao.text?.toString()?.trim().orEmpty()
        val tem = temNecessidade
        viewModel.salvar("Necessidades especiais atualizadas!") { passageiro ->
            passageiro.copy(
                necessidadesEspeciais = tem,
                descricaoNecessidades = if (tem) descricao else ""
            )
        }
    }
}
