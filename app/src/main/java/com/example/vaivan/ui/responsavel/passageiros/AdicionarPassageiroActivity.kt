package com.example.vaivan.ui.responsavel.passageiros

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.DataUtil
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.core.util.SeletorDeFotoHelper
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.ui.comum.avatar.EscolherAvatar
import com.example.vaivan.ui.responsavel.passageiros.perfil.EditarPassageiroViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Cadastro de um passageiro novo, no mesmo visual das telas de edição do
 * perfil. A edição de um passageiro existente fica no perfil dele
 * (ui/responsavel/passageiros/perfil), uma tela por campo.
 */
class AdicionarPassageiroActivity : BaseActivity() {

    companion object {
        /** A partir dessa idade o passageiro cria a própria conta. */
        private const val IDADE_MAXIMA = 18
    }

    private val viewModel: AdicionarPassageiroViewModel by viewModels {
        viewModelFactory {
            initializer {
                AdicionarPassageiroViewModel(
                    EditarPassageiroViewModel.criarRepository(this@AdicionarPassageiroActivity)
                )
            }
        }
    }

    // Os dois precisam ser registrados em onCreate (antes de STARTED).
    private lateinit var seletorDeFoto: SeletorDeFotoHelper
    private val escolherAvatar = registerForActivityResult(EscolherAvatar()) { uri ->
        if (uri != null) definirFoto(uri)
    }

    /** Foto (galeria ou avatar) escolhida; sobe depois de salvar o passageiro. */
    private var foto: Uri? = null
    private var temNecessidade = false

    private lateinit var imgFoto: ShapeableImageView
    private lateinit var txtSeloFoto: TextView

    private lateinit var layoutNome: TextInputLayout
    private lateinit var edtNome: TextInputEditText
    private lateinit var txtErroNome: TextView

    private lateinit var layoutNascimento: TextInputLayout
    private lateinit var edtNascimento: TextInputEditText
    private lateinit var txtErroNascimento: TextView

    private lateinit var btnSim: MaterialButton
    private lateinit var btnNao: MaterialButton
    private lateinit var containerDescricao: View
    private lateinit var edtDescricao: TextInputEditText
    private lateinit var edtObservacoes: TextInputEditText

    private lateinit var txtErroGeral: TextView
    private lateinit var btnSalvar: MaterialButton
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adicionar_passageiro)
        aplicarGapsDeSistema(aplicarBase = false)
        WindowInsetsUtil.aplicarInsets(findViewById(R.id.conteudoEdicaoPerfil), aplicarTopo = false)

        seletorDeFoto = SeletorDeFotoHelper(this) { uri -> definirFoto(uri) }

        inicializarViews()

        findViewById<ImageButton>(R.id.btnVoltarEdicaoPerfil).setOnClickListener { finish() }
        findViewById<View>(R.id.btnFotoNovoPassageiro).setOnClickListener { escolherOrigemDaFoto() }

        edtNascimento.addTextChangedListener(MascaraUtil.inserir("##/##/####", edtNascimento))
        edtNome.doAfterTextChanged { mostrarErro(layoutNome, txtErroNome, null) }
        edtNascimento.doAfterTextChanged { mostrarErro(layoutNascimento, txtErroNascimento, null) }

        btnSim.setOnClickListener { selecionarNecessidade(true) }
        btnNao.setOnClickListener { selecionarNecessidade(false) }
        selecionarNecessidade(false)

        btnSalvar.text = "Cadastrar passageiro"
        btnSalvar.setOnClickListener { validarESalvar() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { renderizar(it) }
            }
        }
    }

    private fun inicializarViews() {
        imgFoto = findViewById(R.id.imgFotoNovoPassageiro)
        txtSeloFoto = findViewById(R.id.txtSeloFotoNovoPassageiro)

        layoutNome = findViewById(R.id.layoutNomeNovoPassageiro)
        edtNome = findViewById(R.id.edtNomeNovoPassageiro)
        txtErroNome = findViewById(R.id.txtErroNomeNovoPassageiro)

        layoutNascimento = findViewById(R.id.layoutNascimentoNovoPassageiro)
        edtNascimento = findViewById(R.id.edtNascimentoNovoPassageiro)
        txtErroNascimento = findViewById(R.id.txtErroNascimentoNovoPassageiro)

        btnSim = findViewById(R.id.btnNecessidadeSim)
        btnNao = findViewById(R.id.btnNecessidadeNao)
        containerDescricao = findViewById(R.id.containerDescricaoNecessidade)
        edtDescricao = findViewById(R.id.edtDescricaoNecessidade)
        edtObservacoes = findViewById(R.id.edtObservacoesNovoPassageiro)

        txtErroGeral = findViewById(R.id.txtErroGeralNovoPassageiro)
        btnSalvar = findViewById(R.id.btnSalvarEdicaoPerfil)
        progress = findViewById(R.id.progressEdicaoPerfil)
    }

    // =========================================================
    // FOTO
    // =========================================================

    /** Galeria ou avatar pronto; com foto já escolhida, também dá pra tirar. */
    private fun escolherOrigemDaFoto() {
        val opcoes = buildList {
            add("Escolher da galeria")
            add("Escolher um avatar")
            if (foto != null) add("Remover foto")
        }

        AlertDialog.Builder(this)
            .setTitle("Foto do passageiro")
            .setItems(opcoes.toTypedArray()) { _, opcao ->
                when (opcao) {
                    0 -> seletorDeFoto.abrirSeletor()
                    1 -> escolherAvatar.launch(Unit)
                    2 -> definirFoto(null)
                }
            }
            .show()
    }

    private fun definirFoto(uri: Uri?) {
        foto = uri

        if (uri == null) {
            imgFoto.setImageResource(R.drawable.avatar_01)
            txtSeloFoto.text = "Adicionar foto"
        } else {
            Glide.with(imgFoto).load(uri).circleCrop().into(imgFoto)
            txtSeloFoto.text = "Trocar foto"
        }
    }

    // =========================================================
    // NECESSIDADES
    // =========================================================

    private fun selecionarNecessidade(sim: Boolean) {
        temNecessidade = sim

        val ativo = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_primary))
        val inativo = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.light_gray))
        btnSim.backgroundTintList = if (sim) ativo else inativo
        btnNao.backgroundTintList = if (sim) inativo else ativo

        containerDescricao.visibility = if (sim) View.VISIBLE else View.GONE
    }

    // =========================================================
    // SALVAR
    // =========================================================

    private fun texto(edt: TextInputEditText) = edt.text?.toString()?.trim().orEmpty()

    private fun validarESalvar() {
        val nome = texto(edtNome)
        val nascimentoIso = DataUtil.paraIso(texto(edtNascimento))

        val erroNome = if (nome.length < 3) "*Informe o nome completo do passageiro." else null
        val erroNascimento = when {
            nascimentoIso == null -> "*Informe uma data válida (DD/MM/AAAA)."
            DataUtil.calcularIdade(nascimentoIso) >= IDADE_MAXIMA ->
                "*Este passageiro já é maior de idade. Peça para ele criar a própria conta."
            else -> null
        }

        mostrarErro(layoutNome, txtErroNome, erroNome)
        mostrarErro(layoutNascimento, txtErroNascimento, erroNascimento)
        if (erroNome != null || erroNascimento != null || nascimentoIso == null) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            mostrarErroGeral("Sessão expirada. Faça login novamente.")
            return
        }

        val passageiro = PassageiroEntity(
            nome = nome,
            dataNascimento = nascimentoIso,
            necessidadesEspeciais = temNecessidade,
            descricaoNecessidades = if (temNecessidade) texto(edtDescricao) else "",
            observacoes = texto(edtObservacoes),
            responsavelId = uid
        )

        viewModel.cadastrar(passageiro, foto)
    }

    private fun renderizar(estado: AdicionarPassageiroViewModel.Estado) {
        val salvando = estado == AdicionarPassageiroViewModel.Estado.Salvando
        progress.visibility = if (salvando) View.VISIBLE else View.GONE
        btnSalvar.text = if (salvando) "" else "Cadastrar passageiro"
        btnSalvar.isEnabled = !salvando

        when (estado) {
            is AdicionarPassageiroViewModel.Estado.Cadastrado -> {
                Toast.makeText(
                    this,
                    estado.aviso ?: "Passageiro cadastrado com sucesso!",
                    if (estado.aviso == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                ).show()
                finish()
            }

            is AdicionarPassageiroViewModel.Estado.Erro -> {
                mostrarErroGeral(estado.mensagem)
                viewModel.erroTratado()
            }

            else -> Unit
        }
    }

    private fun mostrarErro(layout: TextInputLayout, txtErro: TextView, mensagem: String?) {
        txtErro.text = mensagem
        txtErro.visibility = if (mensagem == null) View.GONE else View.VISIBLE
        layout.setBackgroundResource(
            if (mensagem == null) R.drawable.bg_input_white else R.drawable.bg_input_white_red
        )
        if (mensagem != null) txtErroGeral.visibility = View.GONE
    }

    private fun mostrarErroGeral(mensagem: String) {
        txtErroGeral.text = mensagem
        txtErroGeral.visibility = View.VISIBLE
    }
}
