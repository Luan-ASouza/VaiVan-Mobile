package com.example.vaivan.ui.responsavel.passageiros.perfil

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.core.util.DataUtil
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.ui.comum.perfil.LinhaCampoPerfil
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch

/**
 * Perfil do passageiro (aberto pelo lápis da lista): cada linha leva à tela
 * de edição do campo, como no perfil do usuário. Observa o Room, então volta
 * atualizado depois de cada edição.
 */
class PerfilPassageiroActivity : BaseActivity() {

    companion object {
        const val EXTRA_PASSAGEIRO_ID = "passageiro_id"

        fun intent(context: Context, passageiroId: String): Intent =
            Intent(context, PerfilPassageiroActivity::class.java)
                .putExtra(EXTRA_PASSAGEIRO_ID, passageiroId)
    }

    private val passageiroId: String by lazy { intent.getStringExtra(EXTRA_PASSAGEIRO_ID).orEmpty() }

    private val viewModel: EditarPassageiroViewModel by viewModels {
        viewModelFactory {
            initializer {
                EditarPassageiroViewModel(
                    EditarPassageiroViewModel.criarRepository(this@PerfilPassageiroActivity),
                    passageiroId
                )
            }
        }
    }

    private lateinit var imgFoto: ShapeableImageView
    private lateinit var txtNome: TextView
    private lateinit var txtIdade: TextView

    private lateinit var nome: LinhaCampoPerfil
    private lateinit var nascimento: LinhaCampoPerfil
    private lateinit var necessidades: LinhaCampoPerfil
    private lateinit var observacoes: LinhaCampoPerfil

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (passageiroId.isBlank()) {
            finish()
            return
        }

        setContentView(R.layout.activity_perfil_passageiro)
        aplicarGapsDeSistema()

        val raiz = findViewById<View>(android.R.id.content)

        imgFoto = findViewById(R.id.imgFotoPerfilPassageiro)
        txtNome = findViewById(R.id.txtNomePerfilPassageiro)
        txtIdade = findViewById(R.id.txtIdadePerfilPassageiro)

        nome = LinhaCampoPerfil(raiz, R.id.campoNomePassageiro, "Nome")
        nascimento = LinhaCampoPerfil(raiz, R.id.campoNascimentoPassageiro, "Data de nascimento")
        necessidades = LinhaCampoPerfil(raiz, R.id.campoNecessidadesPassageiro, "Necessidades especiais")
        observacoes = LinhaCampoPerfil(raiz, R.id.campoObservacoesPassageiro, "Observações")

        findViewById<View>(R.id.btnVoltarPerfilPassageiro).setOnClickListener { finish() }

        findViewById<View>(R.id.btnFotoPassageiro).setOnClickListener {
            startActivity(FotoPassageiroActivity.intent(this, passageiroId))
        }
        nome.aoTocar {
            startActivity(EditarCampoPassageiroActivity.intent(this, passageiroId, EditarCampoPassageiroActivity.Campo.NOME))
        }
        observacoes.aoTocar {
            startActivity(EditarCampoPassageiroActivity.intent(this, passageiroId, EditarCampoPassageiroActivity.Campo.OBSERVACOES))
        }
        nascimento.aoTocar {
            startActivity(EditarNascimentoPassageiroActivity.intent(this, passageiroId))
        }
        necessidades.aoTocar {
            startActivity(EditarNecessidadesPassageiroActivity.intent(this, passageiroId))
        }

        findViewById<View>(R.id.btnExcluirPassageiro).setOnClickListener { confirmarExclusao() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.passageiro.collect { passageiro ->
                    if (passageiro != null) preencher(passageiro)
                }
            }
        }
    }

    private fun preencher(passageiro: PassageiroEntity) {
        txtNome.text = passageiro.nome

        val idade = DataUtil.calcularIdade(passageiro.dataNascimento)
        txtIdade.text = if (idade >= 0) getString(R.string.idade_anos, idade) else ""

        nome.valor(passageiro.nome)
        nascimento.valor(DataUtil.paraBrasileiro(passageiro.dataNascimento))
        necessidades.valor(
            when {
                !passageiro.necessidadesEspeciais -> "Não"
                passageiro.descricaoNecessidades.isNullOrBlank() -> "Sim"
                else -> passageiro.descricaoNecessidades
            }
        )
        observacoes.valor(passageiro.observacoes?.takeIf { it.isNotBlank() } ?: "Nenhuma")

        Glide.with(imgFoto)
            .load(passageiro.fotoUrl)
            .placeholder(R.drawable.avatar_01)
            .error(R.drawable.avatar_01)
            .circleCrop()
            .into(imgFoto)
    }

    private fun confirmarExclusao() {
        val nomeAtual = txtNome.text.toString()

        ConfirmacaoExclusaoHelper.confirmar(
            contexto = this,
            titulo = "Excluir \"$nomeAtual\"?",
            mensagem = "Isso remove o passageiro e sua vaga em qualquer rota. Essa ação não pode ser desfeita."
        ) {
            lifecycleScope.launch {
                try {
                    viewModel.excluir()
                    Toast.makeText(this@PerfilPassageiroActivity, "Passageiro excluído.", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (erro: Exception) {
                    Toast.makeText(
                        this@PerfilPassageiroActivity,
                        "Erro ao excluir: ${erro.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
