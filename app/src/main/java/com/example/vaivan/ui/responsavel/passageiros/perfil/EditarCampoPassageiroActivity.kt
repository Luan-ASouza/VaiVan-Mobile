package com.example.vaivan.ui.responsavel.passageiros.perfil

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.ui.comum.perfil.acompanharEdicao
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Edição de um campo de texto do passageiro (nome ou observações), no mesmo
 * layout de campo único do perfil do usuário. */
class EditarCampoPassageiroActivity : BaseActivity() {

    enum class Campo(
        val titulo: String,
        val descricao: String,
        val dica: String,
        val mensagemSucesso: String
    ) {
        NOME(
            titulo = "Nome",
            descricao = "Nome completo do passageiro. É como o motorista vai vê-lo na lista da rota.",
            dica = "Nome do passageiro",
            mensagemSucesso = "Nome atualizado!"
        ),
        OBSERVACOES(
            titulo = "Observações",
            descricao = "Alguma informação útil para o motorista (opcional).",
            dica = "Ex.: desce no portão lateral",
            mensagemSucesso = "Observações atualizadas!"
        );

        fun valorAtual(passageiro: PassageiroEntity): String = when (this) {
            NOME -> passageiro.nome
            OBSERVACOES -> passageiro.observacoes.orEmpty()
        }

        /** Mensagem de erro, ou null se o valor é aceito. */
        fun erro(valor: String): String? = when (this) {
            NOME -> if (valor.length < 3) "*Informe o nome completo do passageiro." else null
            OBSERVACOES -> null
        }

        fun aplicar(passageiro: PassageiroEntity, valor: String): PassageiroEntity = when (this) {
            NOME -> passageiro.copy(nome = valor)
            OBSERVACOES -> passageiro.copy(observacoes = valor)
        }
    }

    companion object {
        private const val EXTRA_CAMPO = "campo"

        fun intent(context: Context, passageiroId: String, campo: Campo): Intent =
            Intent(context, EditarCampoPassageiroActivity::class.java)
                .putExtra(PerfilPassageiroActivity.EXTRA_PASSAGEIRO_ID, passageiroId)
                .putExtra(EXTRA_CAMPO, campo.name)
    }

    private val viewModel: EditarPassageiroViewModel by viewModels {
        viewModelFactory {
            initializer {
                EditarPassageiroViewModel(
                    EditarPassageiroViewModel.criarRepository(this@EditarCampoPassageiroActivity),
                    intent.getStringExtra(PerfilPassageiroActivity.EXTRA_PASSAGEIRO_ID).orEmpty()
                )
            }
        }
    }

    private lateinit var campo: Campo
    private lateinit var layoutCampo: TextInputLayout
    private lateinit var edtCampo: TextInputEditText
    private lateinit var txtErro: TextView
    private lateinit var btnSalvar: MaterialButton
    private lateinit var progress: ProgressBar

    private var valorOriginal: String? = null
    private var ocupado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_campo_perfil)
        aplicarGapsDeSistema(aplicarBase = false)
        WindowInsetsUtil.aplicarInsets(findViewById(R.id.conteudoEdicaoPerfil), aplicarTopo = false)

        campo = intent.getStringExtra(EXTRA_CAMPO)
            ?.let { nome -> Campo.entries.firstOrNull { it.name == nome } }
            ?: run { finish(); return }

        layoutCampo = findViewById(R.id.layoutCampo)
        edtCampo = findViewById(R.id.edtCampo)
        txtErro = findViewById(R.id.txtErroCampo)
        btnSalvar = findViewById(R.id.btnSalvarEdicaoPerfil)
        progress = findViewById(R.id.progressEdicaoPerfil)

        findViewById<TextView>(R.id.txtTituloCampo).text = campo.titulo
        findViewById<TextView>(R.id.txtDescricaoCampo).text = campo.descricao
        edtCampo.hint = campo.dica

        when (campo) {
            Campo.NOME -> edtCampo.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS

            Campo.OBSERVACOES -> {
                edtCampo.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                edtCampo.minLines = 4
                edtCampo.gravity = Gravity.TOP or Gravity.START
            }
        }

        edtCampo.doAfterTextChanged {
            limparErro()
            atualizarBotao()
        }
        btnSalvar.setOnClickListener { validarESalvar() }

        acompanharEdicao(
            viewModel = viewModel,
            aoMudarOcupado = { ocupado ->
                this.ocupado = ocupado
                progress.visibility = if (ocupado) View.VISIBLE else View.GONE
                btnSalvar.text = if (ocupado) "" else "Salvar"
                edtCampo.isEnabled = !ocupado
                atualizarBotao()
            },
            aoCarregar = { passageiro ->
                val valor = campo.valorAtual(passageiro)
                valorOriginal = valor
                edtCampo.setText(valor)
                edtCampo.setSelection(edtCampo.text?.length ?: 0)
                edtCampo.requestFocus()
                atualizarBotao()
            },
            aoFalharSalvar = { mensagem -> mostrarErro(mensagem) }
        )
    }

    private fun valorDigitado(): String = edtCampo.text?.toString()?.trim().orEmpty()

    /** Só habilita quando houve mudança (observações pode ficar vazia). */
    private fun atualizarBotao() {
        val valor = valorDigitado()
        val habilitado = !ocupado && valorOriginal != null && valor != valorOriginal &&
            (campo == Campo.OBSERVACOES || valor.isNotEmpty())
        btnSalvar.isEnabled = habilitado
        btnSalvar.alpha = if (habilitado || ocupado) 1f else 0.5f
    }

    private fun validarESalvar() {
        val valor = valorDigitado()

        campo.erro(valor)?.let {
            mostrarErro(it)
            return
        }

        viewModel.salvar(campo.mensagemSucesso) { passageiro -> campo.aplicar(passageiro, valor) }
    }

    private fun mostrarErro(mensagem: String) {
        txtErro.text = mensagem
        txtErro.visibility = View.VISIBLE
        layoutCampo.setBackgroundResource(R.drawable.bg_input_white_red)
    }

    private fun limparErro() {
        txtErro.visibility = View.GONE
        layoutCampo.setBackgroundResource(R.drawable.bg_input_white)
    }
}
