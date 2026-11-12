package com.example.vaivan.ui.comum.perfil

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

/**
 * Tela de edição de UM campo de texto do perfil (nome, sobrenome ou
 * telefone), aberta a partir da linha correspondente no PerfilFragment.
 * Uma tela por campo — mesmo layout, configurado pelo [Campo] recebido.
 */
class EditarCampoPerfilActivity : BaseActivity() {

    enum class Campo(
        val titulo: String,
        val descricao: String,
        val dica: String,
        val erro: String,
        val mensagemSucesso: String
    ) {
        NOME(
            titulo = "Nome",
            descricao = "Use o seu primeiro nome, como aparece no seu documento.",
            dica = "Seu nome",
            erro = "*Informe um nome com pelo menos 3 letras.",
            mensagemSucesso = "Nome atualizado!"
        ),
        SOBRENOME(
            titulo = "Sobrenome",
            descricao = "Use o seu sobrenome como aparece no seu documento.",
            dica = "Seu sobrenome",
            erro = "*Informe um sobrenome com pelo menos 3 letras.",
            mensagemSucesso = "Sobrenome atualizado!"
        ),
        TELEFONE(
            titulo = "Telefone",
            descricao = "Motoristas e responsáveis usam este número para falar com você.",
            dica = "(00) 00000-0000",
            erro = "*Informe um telefone válido com DDD.",
            mensagemSucesso = "Telefone atualizado!"
        );

        fun valorAtual(usuario: UsuarioEntity): String = when (this) {
            NOME -> usuario.nome
            SOBRENOME -> usuario.sobrenome
            TELEFONE -> usuario.telefone
        }

        fun valido(valor: String): Boolean = when (this) {
            NOME, SOBRENOME -> CadastroValidator.nomeValido(valor)
            TELEFONE -> CadastroValidator.telefoneValido(valor)
        }

        fun aplicar(usuario: UsuarioEntity, valor: String): UsuarioEntity = when (this) {
            NOME -> usuario.copy(nome = valor)
            SOBRENOME -> usuario.copy(sobrenome = valor)
            TELEFONE -> usuario.copy(telefone = valor)
        }
    }

    companion object {
        private const val EXTRA_CAMPO = "campo"

        fun intent(context: Context, campo: Campo): Intent =
            Intent(context, EditarCampoPerfilActivity::class.java)
                .putExtra(EXTRA_CAMPO, campo.name)
    }

    private val viewModel: EditarPerfilViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(this@EditarCampoPerfilActivity)
                EditarPerfilViewModel(UsuarioRepository(db.usuarioDao()), FirebaseAuth.getInstance().currentUser?.uid)
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
        txtErro.text = campo.erro
        edtCampo.hint = campo.dica

        configurarEntrada()

        edtCampo.doAfterTextChanged {
            limparErro()
            atualizarBotao()
        }
        edtCampo.setOnEditorActionListener { _, acao, _ ->
            if (acao == EditorInfo.IME_ACTION_DONE && btnSalvar.isEnabled) {
                validarESalvar()
                true
            } else {
                false
            }
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
            aoCarregar = { usuario ->
                val valor = campo.valorAtual(usuario)
                valorOriginal = valor
                edtCampo.setText(valor)
                edtCampo.setSelection(edtCampo.text?.length ?: 0)
                edtCampo.requestFocus()
                atualizarBotao()
            },
            aoFalharSalvar = { mensagem -> mostrarErro(mensagem) }
        )
    }

    private fun configurarEntrada() {
        when (campo) {
            Campo.NOME, Campo.SOBRENOME -> {
                edtCampo.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PERSON_NAME or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS
                // Mesmo filtro do cadastro: só letras, espaço, hífen e apóstrofo.
                edtCampo.filters = arrayOf(
                    InputFilter { source, _, _, _, _, _ ->
                        if (source.isNullOrEmpty()) return@InputFilter null
                        val permitido = source.all { it.isLetter() || it == ' ' || it == '-' || it == '\'' }
                        if (permitido) null else ""
                    }
                )
            }

            Campo.TELEFONE -> {
                edtCampo.inputType = InputType.TYPE_CLASS_PHONE
                edtCampo.addTextChangedListener(MascaraUtil.inserir("(##) #####-####", edtCampo))
            }
        }
    }

    private fun valorDigitado(): String = edtCampo.text?.toString()?.trim().orEmpty()

    /** Só habilita quando houve mudança — salvar o mesmo valor não faz sentido. */
    private fun atualizarBotao() {
        val valor = valorDigitado()
        val habilitado = !ocupado && valorOriginal != null && valor.isNotEmpty() && valor != valorOriginal
        btnSalvar.isEnabled = habilitado
        btnSalvar.alpha = if (habilitado || ocupado) 1f else 0.5f
    }

    private fun validarESalvar() {
        val valor = valorDigitado()

        if (!campo.valido(valor)) {
            mostrarErro(campo.erro)
            return
        }

        viewModel.salvar(campo.mensagemSucesso) { usuario -> campo.aplicar(usuario, valor) }
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
