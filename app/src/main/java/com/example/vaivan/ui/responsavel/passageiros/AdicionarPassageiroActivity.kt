package com.example.vaivan.ui.responsavel.passageiros

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.core.util.SeletorDeFotoHelper
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.core.util.DataUtil
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AdicionarPassageiroActivity : BaseActivity() {

    companion object {
        const val EXTRA_PASSAGEIRO_ID = "extra_passageiro_id"
    }

    // ---------------------------------------------------------
    // VIEWS
    // ---------------------------------------------------------

    private lateinit var edtNome: EditText
    private lateinit var edtNascimento: EditText

    private lateinit var layoutNecessidades:
            TextInputLayout

    private lateinit var edtNecessidades: EditText
    private lateinit var edtObservacoes: EditText

    private lateinit var btnSim: Button
    private lateinit var btnNao: Button
    private lateinit var btnSalvar: Button

    private lateinit var txtTitulo: TextView
    private lateinit var imgFoto: ShapeableImageView
    private lateinit var btnTrocarFoto: TextView
    private lateinit var btnRemoverFoto: TextView

    // ---------------------------------------------------------
    // MODO EDIÇÃO
    // ---------------------------------------------------------

    private var passageiroId: String? = null
    private var passageiroCarregado: PassageiroEntity? = null

    // ---------------------------------------------------------
    // FOTO
    // ---------------------------------------------------------

    // Precisa ser criado em onCreate (antes da Activity chegar em STARTED) —
    // ver SeletorDeFotoHelper.
    private lateinit var seletorDeFoto: SeletorDeFotoHelper

    private var fotoUrlAtual: String? = null
    private var novaFotoUri: Uri? = null
    private var fotoRemovida = false

    // ---------------------------------------------------------
    // NECESSIDADES ESPECIAIS
    // ---------------------------------------------------------

    private var temNecessidadeEspecial = false

    // ---------------------------------------------------------
    // FIREBASE
    // ---------------------------------------------------------

    private val auth =
        FirebaseAuth.getInstance()

    // ---------------------------------------------------------
    // REPOSITORY
    // ---------------------------------------------------------

    private val repository by lazy {

        val db = VaivanDatabase.getInstance(this)
        // DAOs de paradas/solicitações: ao renomear o passageiro, as cópias
        // do nome no cache local também são atualizadas na hora.
        PassageiroRepository(
            passageiroDao = db.passageiroDao(),
            paradaRotaDao = db.paradaRotaDao(),
            solicitacaoInclusaoDao = db.solicitacaoInclusaoDao()
        )
    }

    // ---------------------------------------------------------
    // ON CREATE
    // ---------------------------------------------------------

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_adicionar_passageiro
        )

        passageiroId = intent.getStringExtra(EXTRA_PASSAGEIRO_ID)

        seletorDeFoto = SeletorDeFotoHelper(this) { uri -> onFotoEscolhida(uri) }

        inicializarViews()

        configurarDataNascimento()

        configurarToggleNecessidades()

        configurarBotoes()

        if (passageiroId != null) {
            txtTitulo.text = "Editar Passageiro"
            carregarPassageiroParaEdicao(passageiroId!!)
        }

        aplicarGapsDeSistema()
    }

    // ---------------------------------------------------------
    // VIEWS
    // ---------------------------------------------------------

    private fun inicializarViews() {

        val btnVoltar =
            findViewById<ImageView>(
                R.id.btnVoltar
            )

        btnSalvar =
            findViewById(
                R.id.btnSalvar
            )

        edtNome =
            findViewById(
                R.id.edtNomePassageiro
            )

        edtNascimento =
            findViewById(
                R.id.edtNascimento
            )

        edtNecessidades =
            findViewById(
                R.id.edtNecessidades
            )

        layoutNecessidades =
            findViewById(
                R.id.layoutNecessidades
            )

        edtObservacoes =
            findViewById(
                R.id.edtObservacoes
            )

        btnSim =
            findViewById(
                R.id.btnSimNecessidade
            )

        btnNao =
            findViewById(
                R.id.btnNaoNecessidade
            )

        txtTitulo =
            findViewById(
                R.id.txtTitulo
            )

        imgFoto =
            findViewById(
                R.id.imgFotoNovoPassageiro
            )

        btnTrocarFoto =
            findViewById(
                R.id.btnTrocarFotoPassageiro
            )

        btnRemoverFoto =
            findViewById(
                R.id.btnRemoverFotoPassageiro
            )

        btnVoltar.setOnClickListener {
            finish()
        }

        btnTrocarFoto.setOnClickListener {
            seletorDeFoto.abrirSeletor()
        }

        btnRemoverFoto.setOnClickListener {
            confirmarRemoverFoto()
        }
    }

    // ---------------------------------------------------------
    // CARREGAR PARA EDIÇÃO
    // ---------------------------------------------------------

    private fun carregarPassageiroParaEdicao(id: String) {

        lifecycleScope.launch {
            try {
                val passageiro = repository.consultarPassageiro(id)

                if (passageiro == null) {
                    Toast.makeText(
                        this@AdicionarPassageiroActivity,
                        "Passageiro não encontrado.",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                    return@launch
                }

                passageiroCarregado = passageiro
                preencherFormulario(passageiro)

            } catch (erro: Exception) {
                Toast.makeText(
                    this@AdicionarPassageiroActivity,
                    "Erro ao carregar passageiro: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
        }
    }

    private fun preencherFormulario(passageiro: PassageiroEntity) {
        edtNome.setText(passageiro.nome)
        edtNascimento.setText(DataUtil.paraBrasileiro(passageiro.dataNascimento))
        edtObservacoes.setText(passageiro.observacoes ?: "")

        atualizarBotoesNecessidade(selecionado = passageiro.necessidadesEspeciais)
        if (passageiro.necessidadesEspeciais) {
            edtNecessidades.setText(passageiro.descricaoNecessidades ?: "")
        }

        fotoUrlAtual = passageiro.fotoUrl
        exibirFoto(fotoUrlAtual)
    }

    // ---------------------------------------------------------
    // FOTO
    // ---------------------------------------------------------

    private fun exibirFoto(url: String?) {
        if (url == null) {
            imgFoto.setImageResource(android.R.drawable.ic_menu_camera)
            btnTrocarFoto.text = "Adicionar foto"
            btnRemoverFoto.visibility = View.GONE
            return
        }

        Glide.with(imgFoto)
            .load(url)
            .circleCrop()
            .into(imgFoto)

        btnTrocarFoto.text = "Trocar foto"
        btnRemoverFoto.visibility = View.VISIBLE
    }

    private fun onFotoEscolhida(uri: Uri) {
        novaFotoUri = uri
        fotoRemovida = false

        Glide.with(imgFoto).load(uri).circleCrop().into(imgFoto)

        btnTrocarFoto.text = "Trocar foto"
        btnRemoverFoto.visibility = View.VISIBLE
    }

    private fun confirmarRemoverFoto() {
        if (novaFotoUri != null) {
            // Ainda não foi salva — só desfaz a seleção local.
            novaFotoUri = null
            exibirFoto(fotoUrlAtual)
            return
        }

        if (fotoUrlAtual == null) return

        ConfirmacaoExclusaoHelper.confirmar(
            contexto = this,
            titulo = "Remover foto?",
            mensagem = "A foto deste passageiro será removida ao salvar.",
            textoConfirmar = "Sim, remover"
        ) {
            fotoRemovida = true
            fotoUrlAtual = null
            exibirFoto(null)
        }
    }

    // ---------------------------------------------------------
    // DATA DE NASCIMENTO
    // ---------------------------------------------------------

    private fun configurarDataNascimento() {

        edtNascimento.addTextChangedListener(
            MascaraUtil.inserir(
                "##/##/####",
                edtNascimento
            )
        )
    }

    // ---------------------------------------------------------
    // NECESSIDADES ESPECIAIS
    // ---------------------------------------------------------

    private fun configurarToggleNecessidades() {

        atualizarBotoesNecessidade(
            selecionado = false
        )

        btnSim.setOnClickListener {

            atualizarBotoesNecessidade(
                selecionado = true
            )
        }

        btnNao.setOnClickListener {

            atualizarBotoesNecessidade(
                selecionado = false
            )
        }
    }

    private fun atualizarBotoesNecessidade(
        selecionado: Boolean
    ) {

        temNecessidadeEspecial =
            selecionado

        btnSim.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                if (selecionado)
                    0xFFF6B12F.toInt()
                else
                    0xFFAFAFAF.toInt()
            )

        btnNao.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                if (selecionado)
                    0xFFAFAFAF.toInt()
                else
                    0xFFF6B12F.toInt()
            )

        layoutNecessidades.visibility =
            if (selecionado) {

                View.VISIBLE

            } else {

                edtNecessidades.setText("")

                View.GONE
            }
    }

    // ---------------------------------------------------------
    // BOTÕES
    // ---------------------------------------------------------

    private fun configurarBotoes() {

        btnSalvar.setOnClickListener {

            validarESalvar()
        }
    }

    // ---------------------------------------------------------
    // VALIDAÇÃO
    // ---------------------------------------------------------

    private fun validarESalvar() {

        val nome =
            edtNome.text
                ?.toString()
                ?.trim()
                ?: ""

        val nascimentoDigitado =
            edtNascimento.text
                ?.toString()
                ?.trim()
                ?: ""

        // -----------------------------------------------------
        // NOME
        // -----------------------------------------------------

        if (nome.length < 3) {

            Toast.makeText(
                this,
                "Informe o nome completo do passageiro.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // -----------------------------------------------------
        // DATA
        // -----------------------------------------------------

        if (
            nascimentoDigitado.length < 10
        ) {

            Toast.makeText(
                this,
                "Informe a data de nascimento completa (DD/MM/AAAA).",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val nascimentoIso =
            DataUtil.paraIso(
                nascimentoDigitado
            )

        if (nascimentoIso == null) {

            Toast.makeText(
                this,
                "Data de nascimento inválida.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // -----------------------------------------------------
        // IDADE
        // -----------------------------------------------------

        val idade =
            DataUtil.calcularIdade(
                nascimentoIso
            )

        if (idade < 0) {

            Toast.makeText(
                this,
                "Não foi possível calcular a idade informada.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // -----------------------------------------------------
        // MAIOR DE IDADE
        // -----------------------------------------------------

        if (idade >= 18) {

            Toast.makeText(
                this,
                "Este passageiro já é maior de idade. Peça para ele criar a própria conta.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        // -----------------------------------------------------
        // USUÁRIO
        // -----------------------------------------------------

        val uid =
            auth.currentUser?.uid

        if (uid == null) {

            Toast.makeText(
                this,
                "Sessão expirada. Faça login novamente.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // -----------------------------------------------------
        // NECESSIDADES
        // -----------------------------------------------------

        val descricaoNecessidades =
            if (temNecessidadeEspecial) {

                edtNecessidades.text
                    ?.toString()
                    ?.trim()
                    ?: ""

            } else {

                ""
            }

        val observacoes =
            edtObservacoes.text
                ?.toString()
                ?.trim()
                ?: ""

        // -----------------------------------------------------
        // PASSAGEIRO
        // -----------------------------------------------------

        val passageiroParaSalvar =
            passageiroCarregado?.copy(
                nome = nome,
                dataNascimento = nascimentoIso,
                necessidadesEspeciais = temNecessidadeEspecial,
                descricaoNecessidades = descricaoNecessidades,
                observacoes = observacoes
            ) ?: PassageiroEntity(
                id = "",
                nome = nome,
                dataNascimento = nascimentoIso,
                necessidadesEspeciais =
                    temNecessidadeEspecial,
                descricaoNecessidades =
                    descricaoNecessidades,
                observacoes =
                    observacoes,
                responsavelId =
                    uid,
            )

        val editando = passageiroCarregado != null

        btnSalvar.isEnabled = false

        lifecycleScope.launch {

            try {

                val idSalvo = repository.salvarPassageiro(
                    passageiroParaSalvar
                )
                val passageiroSalvo = passageiroParaSalvar.copy(id = idSalvo)

                if (novaFotoUri != null) {
                    // Já vem recortada e comprimida do SeletorDeFotoHelper
                    repository.atualizarFoto(passageiroSalvo, novaFotoUri!!)
                } else if (fotoRemovida) {
                    repository.removerFoto(passageiroSalvo)
                }

                Toast.makeText(
                    this@AdicionarPassageiroActivity,
                    if (editando) "Passageiro atualizado com sucesso!" else "Passageiro cadastrado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                finish()

            } catch (erro: Exception) {

                btnSalvar.isEnabled = true

                Toast.makeText(
                    this@AdicionarPassageiroActivity,
                    "Erro ao salvar passageiro: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
