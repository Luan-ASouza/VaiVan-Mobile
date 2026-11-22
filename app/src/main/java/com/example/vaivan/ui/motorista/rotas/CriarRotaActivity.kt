package com.example.vaivan.ui.motorista.rotas

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.TextoUtil
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.models.Escola
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.repository.AssinaturaBloqueadaException
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.domain.usecase.rota.AssociarVeiculoARotaUseCase
import com.example.vaivan.domain.usecase.veiculo.VeiculoPolicy
import com.example.vaivan.domain.usecase.rota.AtualizarTrajetoDaRotaUseCase
import com.example.vaivan.domain.usecase.rota.RecalcularRotaUseCase
import com.example.vaivan.ui.comum.perfil.acompanharEdicao
import com.example.vaivan.ui.escolas.SelecionarEscolaActivity
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.example.vaivan.ui.responsavel.passageiros.SelecaoLocalizacaoActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Criação e edição de rota no modelo do perfil: um campo por tela.
 *
 *  - **Criar** (sem extras): passo a passo pelas [Etapa]s, na ordem do enum,
 *    com "Continuar" em cada uma e "Criar rota" no fim.
 *  - **Editar** ([intentEditar]): abre só a etapa do campo tocado na aba
 *    Informações do detalhe e salva com o [EditarRotaViewModel].
 *
 * O visual mantém o escuro do motorista no topo, com o campo numa folha
 * branca minimalista (mesmos inputs das telas de edição do perfil).
 */
class CriarRotaActivity : BaseActivity() {

    /** Cada campo da rota = uma tela. A ordem é a do passo a passo. */
    enum class Etapa { NOME, SENTIDO, ESCOLA, PONTO, BAIRROS, TURNO, DIAS, VEICULO, CAPACIDADE }

    companion object {
        private const val EXTRA_ROTA_ID = "extra_rota_id"
        private const val EXTRA_ETAPA = "extra_etapa"

        /** Edita um campo só de uma rota existente. */
        fun intentEditar(context: Context, rotaId: String, etapa: Etapa): Intent =
            Intent(context, CriarRotaActivity::class.java)
                .putExtra(EXTRA_ROTA_ID, rotaId)
                .putExtra(EXTRA_ETAPA, etapa.name)
    }

    /** O que já foi preenchido (no passo a passo, ou a rota em edição). */
    private class Rascunho {
        var nome = ""
        var tipoTrajeto: String? = null
        var turno: String? = null
        val dias = mutableSetOf<String>()
        var capacidade = ""
        var escola: Escola? = null
        var origemNome: String? = null
        var origemEndereco: String? = null
        var origemLatitude: Double? = null
        var origemLongitude: Double? = null

        /** null = ainda não escolheu; "" = sem veículo por enquanto. */
        var veiculoId: String? = null

        /** chave normalizada -> nome para mostrar */
        val bairros = linkedMapOf<String, String>()

        fun carregar(rota: RotaEntity) {
            nome = rota.nome
            tipoTrajeto = rota.tipoTrajeto
            turno = rota.turno
            dias.clear()
            dias.addAll(rota.diasSemana.split(",").filter { it.isNotBlank() })
            capacidade = rota.capacidadeTotal.toString()
            escola = Escola(
                id = rota.escolaId,
                nome = rota.escolaNome.ifBlank { rota.destinoNome },
                endereco = rota.destinoEndereco,
                latitude = rota.destinoLatitude,
                longitude = rota.destinoLongitude
            )
            origemNome = rota.origemNome
            origemEndereco = rota.origemEndereco
            origemLatitude = rota.origemLatitude
            origemLongitude = rota.origemLongitude
            veiculoId = rota.veiculoId.orEmpty()
            bairros.clear()
            rota.bairrosAtendidos.forEach { bairros[it] = RotaTextos.bairro(it) }
        }

        /** A rota com o campo [etapa] trocado pelo valor do rascunho. */
        fun aplicarEm(rota: RotaEntity, etapa: Etapa): RotaEntity = when (etapa) {
            Etapa.NOME -> rota.copy(nome = nome)
            Etapa.SENTIDO -> rota.copy(tipoTrajeto = tipoTrajeto ?: rota.tipoTrajeto)
            Etapa.TURNO -> rota.copy(turno = turno ?: rota.turno)
            Etapa.DIAS -> rota.copy(diasSemana = dias.joinToString(","))
            Etapa.CAPACIDADE -> rota.copy(capacidadeTotal = capacidade.toIntOrNull() ?: rota.capacidadeTotal)
            Etapa.VEICULO -> rota.copy(veiculoId = veiculoId?.takeIf { it.isNotBlank() })
            Etapa.BAIRROS -> rota.copy(bairrosAtendidos = bairros.keys.toList())
            Etapa.ESCOLA -> escola?.let {
                rota.copy(
                    escolaId = it.id,
                    escolaNome = it.nome,
                    destinoNome = it.nome,
                    destinoEndereco = it.endereco,
                    destinoLatitude = it.latitude,
                    destinoLongitude = it.longitude
                )
            } ?: rota
            Etapa.PONTO -> rota.copy(
                origemNome = origemNome ?: rota.origemNome,
                origemEndereco = origemEndereco ?: rota.origemEndereco,
                origemLatitude = origemLatitude ?: rota.origemLatitude,
                origemLongitude = origemLongitude ?: rota.origemLongitude
            )
        }
    }

    private val rascunho = Rascunho()

    private val rotaIdEdicao: String? by lazy { intent.getStringExtra(EXTRA_ROTA_ID) }
    private val modoEdicao get() = rotaIdEdicao != null

    private val rotaRepository by lazy {
        val db = VaivanDatabase.getInstance(this)
        RotaRepository(
            rotaDao = db.rotaDao(),
            paradaRotaDao = db.paradaRotaDao(),
            routesClient = GoogleRoutesClient(this)
        )
    }

    private val veiculoRepository by lazy {
        VeiculoRepository(VaivanDatabase.getInstance(this).veiculoDao())
    }

    private val viewModel: EditarRotaViewModel by viewModels {
        viewModelFactory {
            initializer {
                val recalcular = RecalcularRotaUseCase(
                    rotaRepository = rotaRepository,
                    routesClient = GoogleRoutesClient(this@CriarRotaActivity)
                )
                EditarRotaViewModel(
                    rotaRepository = rotaRepository,
                    atualizarTrajetoUseCase = AtualizarTrajetoDaRotaUseCase(rotaRepository, recalcular),
                    associarVeiculoUseCase = AssociarVeiculoARotaUseCase(rotaRepository, veiculoRepository),
                    rotaId = rotaIdEdicao.orEmpty()
                )
            }
        }
    }

    /** Veículos do motorista (Firestore), carregados na etapa Veículo. */
    private var veiculosDoMotorista: List<VeiculoEntity>? = null

    private lateinit var etapa: Etapa
    private var rotaOriginal: RotaEntity? = null
    private var ocupado = false

    private lateinit var txtPasso: TextView
    private lateinit var progressPassos: ProgressBar
    private lateinit var txtTitulo: TextView
    private lateinit var txtDescricao: TextView
    private lateinit var layoutCampo: TextInputLayout
    private lateinit var edtCampo: TextInputEditText
    private lateinit var painelOpcoes: LinearLayout
    private lateinit var painelDias: View
    private lateinit var containerDias: LinearLayout
    private lateinit var painelLocal: View
    private lateinit var painelBairros: View
    private lateinit var chipGroupBairros: ChipGroup
    private lateinit var txtSemBairros: TextView
    private lateinit var txtErro: TextView
    private lateinit var txtNota: TextView
    private lateinit var btnSalvar: MaterialButton
    private lateinit var progress: ProgressBar

    private lateinit var selecaoPontoLauncher: ActivityResultLauncher<Intent>
    private lateinit var selecaoEscolaLauncher: ActivityResultLauncher<Intent>
    private lateinit var selecaoBairroLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_criar_rota)
        aplicarGapsDeSistema(aplicarBase = false)
        WindowInsetsUtil.aplicarInsets(findViewById(R.id.conteudoEdicaoPerfil), aplicarTopo = false)
        // Topo escuro: ícones claros na status bar
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        txtPasso = findViewById(R.id.txtPassoCriarRota)
        progressPassos = findViewById(R.id.progressPassosCriarRota)
        txtTitulo = findViewById(R.id.txtTituloCampo)
        txtDescricao = findViewById(R.id.txtDescricaoCampo)
        layoutCampo = findViewById(R.id.layoutCampo)
        edtCampo = findViewById(R.id.edtCampo)
        painelOpcoes = findViewById(R.id.painelOpcoesCriarRota)
        painelDias = findViewById(R.id.painelDiasCriarRota)
        containerDias = findViewById(R.id.containerDiasCriarRota)
        painelLocal = findViewById(R.id.painelLocalCriarRota)
        painelBairros = findViewById(R.id.painelBairrosCriarRota)
        chipGroupBairros = findViewById(R.id.chipGroupBairrosCriarRota)
        txtSemBairros = findViewById(R.id.txtSemBairrosCriarRota)
        txtErro = findViewById(R.id.txtErroCampo)
        txtNota = findViewById(R.id.txtNotaCriarRota)
        btnSalvar = findViewById(R.id.btnSalvarEdicaoPerfil)
        progress = findViewById(R.id.progressEdicaoPerfil)

        // Botão no escuro do motorista (o rodapé compartilhado é laranja)
        btnSalvar.setBackgroundResource(R.drawable.bg_botao_escuro)

        configurarLaunchers()
        montarDias()

        edtCampo.doAfterTextChanged { limparErro() }
        btnSalvar.setOnClickListener { confirmarEtapa() }

        findViewById<View>(R.id.btnEscolherLocalCriarRota).setOnClickListener {
            if (etapa == Etapa.ESCOLA) {
                selecaoEscolaLauncher.launch(Intent(this, SelecionarEscolaActivity::class.java))
            } else {
                selecaoPontoLauncher.launch(Intent(this, SelecaoLocalizacaoActivity::class.java))
            }
        }
        findViewById<View>(R.id.btnAdicionarBairroCriarRota).setOnClickListener {
            selecaoBairroLauncher.launch(Intent(this, SelecaoLocalizacaoActivity::class.java))
        }
        findViewById<View>(R.id.btnDiasUteisCriarRota).setOnClickListener {
            rascunho.dias.clear()
            rascunho.dias.addAll(RotaTextos.DIAS.take(5))
            atualizarDias()
        }
        findViewById<View>(R.id.btnTodosDiasCriarRota).setOnClickListener {
            rascunho.dias.clear()
            rascunho.dias.addAll(RotaTextos.DIAS)
            atualizarDias()
        }

        if (modoEdicao) {
            iniciarEdicao()
        } else {
            iniciarCriacao()
        }
    }

    // ---------------------------------------------------------
    // MODOS
    // ---------------------------------------------------------

    private fun iniciarCriacao() {
        progressPassos.max = Etapa.entries.size
        mostrarEtapa(Etapa.entries.first())

        // Voltar = passo anterior; no primeiro passo, sai da tela
        val voltar = {
            val indice = etapa.ordinal
            if (indice == 0 || ocupado) finish() else mostrarEtapa(Etapa.entries[indice - 1])
        }
        findViewById<View>(R.id.btnVoltarEdicaoPerfil).setOnClickListener { voltar() }
        onBackPressedDispatcher.addCallback(this) { voltar() }
    }

    private fun iniciarEdicao() {
        val etapaEditada = intent.getStringExtra(EXTRA_ETAPA)
            ?.let { nome -> Etapa.entries.firstOrNull { it.name == nome } }
            ?: run { finish(); return }

        txtPasso.visibility = View.GONE
        progressPassos.visibility = View.GONE
        etapa = etapaEditada
        btnSalvar.isEnabled = false

        acompanharEdicao(
            viewModel = viewModel,
            aoMudarOcupado = { ocupado ->
                this.ocupado = ocupado
                progress.visibility = if (ocupado) View.VISIBLE else View.GONE
                btnSalvar.text = if (ocupado) "" else "Salvar"
                btnSalvar.isEnabled = !ocupado && rotaOriginal != null
            },
            aoCarregar = { rota ->
                rotaOriginal = rota
                rascunho.carregar(rota)
                mostrarEtapa(etapaEditada)
                btnSalvar.isEnabled = true
            },
            aoFalharSalvar = { mensagem -> mostrarErro(mensagem) }
        )
    }

    // ---------------------------------------------------------
    // MOSTRAR UMA ETAPA
    // ---------------------------------------------------------

    private fun mostrarEtapa(nova: Etapa) {
        etapa = nova
        limparErro()

        if (!modoEdicao) {
            txtPasso.text = "Passo ${nova.ordinal + 1} de ${Etapa.entries.size}"
            progressPassos.progress = nova.ordinal + 1
            btnSalvar.text = if (nova == Etapa.entries.last()) "Criar rota" else "Continuar"
        } else {
            btnSalvar.text = "Salvar"
        }

        txtTitulo.text = titulo(nova)
        txtDescricao.text = descricao(nova)

        layoutCampo.visibility = View.GONE
        painelOpcoes.visibility = View.GONE
        painelDias.visibility = View.GONE
        painelLocal.visibility = View.GONE
        painelBairros.visibility = View.GONE
        txtNota.visibility = View.GONE

        when (nova) {
            Etapa.NOME -> mostrarTexto(
                rascunho.nome,
                "Ex.: La Salle - Manhã",
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            )

            Etapa.CAPACIDADE -> {
                mostrarTexto(rascunho.capacidade, "Ex.: 15", InputType.TYPE_CLASS_NUMBER)
                rotaOriginal?.takeIf { it.vagasOcupadas > 0 }?.let {
                    mostrarNota("A rota já tem ${it.vagasOcupadas} aluno(s); a capacidade não pode ficar abaixo disso.")
                }
            }

            Etapa.SENTIDO -> mostrarOpcoes(
                RotaTextos.SENTIDOS.map { it to RotaTextos.sentido(it) },
                selecionada = rascunho.tipoTrajeto,
                descricao = { RotaTextos.descricaoSentido(it) }
            ) { escolhida -> rascunho.tipoTrajeto = escolhida }

            Etapa.TURNO -> mostrarOpcoes(
                RotaTextos.TURNOS.map { it to RotaTextos.turno(it) },
                selecionada = rascunho.turno,
                descricao = { null }
            ) { escolhida -> rascunho.turno = escolhida }

            Etapa.DIAS -> {
                painelDias.visibility = View.VISIBLE
                atualizarDias()
            }

            Etapa.ESCOLA -> {
                mostrarLocal()
                if (modoEdicao) mostrarNota("Trocar a escola recalcula o trajeto da rota.")
            }

            Etapa.PONTO -> {
                mostrarLocal()
                if (modoEdicao) mostrarNota("Trocar o ponto recalcula o trajeto da rota.")
            }

            Etapa.BAIRROS -> {
                painelBairros.visibility = View.VISIBLE
                atualizarBairros()
            }

            Etapa.VEICULO -> mostrarVeiculos()
        }
    }

    /** Só veículos aprovados aparecem para escolha (VeiculoPolicy). */
    private fun mostrarVeiculos() {
        val carregados = veiculosDoMotorista
        if (carregados == null) {
            mostrarNota("Carregando seus veículos…")
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            lifecycleScope.launch {
                veiculosDoMotorista = runCatching { veiculoRepository.consultarVeiculosDoMotorista(uid) }
                    .getOrElse { emptyList() }
                if (etapa == Etapa.VEICULO) mostrarEtapa(Etapa.VEICULO)
            }
            return
        }

        val aprovados = carregados.filter { VeiculoPolicy.podeSerAssociadoARota(it) }
        val opcoes = aprovados.map { it.id to rotuloVeiculo(it) } + ("" to "Definir depois")

        mostrarOpcoes(
            opcoes,
            selecionada = rascunho.veiculoId,
            descricao = { id ->
                aprovados.firstOrNull { it.id == id }
                    ?.let { "${it.capacidadePassageiros} lugares" }
                    ?: "A rota fica sem veículo por enquanto."
            }
        ) { escolhido ->
            rascunho.veiculoId = escolhido
            // Sugere a capacidade do veículo, se ainda não foi informada
            aprovados.firstOrNull { it.id == escolhido }?.let { veiculo ->
                if (rascunho.capacidade.isBlank()) rascunho.capacidade = veiculo.capacidadePassageiros.toString()
            }
        }

        val aguardando = carregados.size - aprovados.size
        when {
            carregados.isEmpty() ->
                mostrarNota("Você ainda não cadastrou veículos. Cadastre em Veículos e envie os documentos.")
            aguardando > 0 ->
                mostrarNota("$aguardando veículo(s) aguardando aprovação dos documentos não aparecem aqui.")
        }
    }

    private fun rotuloVeiculo(veiculo: VeiculoEntity): String =
        listOf("${veiculo.marca} ${veiculo.modelo}".trim(), veiculo.placa)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

    private fun titulo(etapa: Etapa): String = when (etapa) {
        Etapa.NOME -> "Nome da rota"
        Etapa.SENTIDO -> "Sentido"
        Etapa.ESCOLA -> "Escola"
        Etapa.PONTO -> RotaTextos.rotuloPonto(rascunho.tipoTrajeto.orEmpty())
        Etapa.BAIRROS -> "Bairros atendidos"
        Etapa.TURNO -> "Turno"
        Etapa.DIAS -> "Dias da semana"
        Etapa.CAPACIDADE -> "Capacidade"
        Etapa.VEICULO -> "Veículo"
    }

    private fun descricao(etapa: Etapa): String = when (etapa) {
        Etapa.NOME -> "Como a rota aparece para você e para os responsáveis."
        Etapa.SENTIDO -> "A van leva os alunos até a escola, traz de volta ou faz os dois?"
        Etapa.ESCOLA -> "A escola atendida por esta rota."
        Etapa.PONTO -> when (rascunho.tipoTrajeto) {
            "VOLTA" -> "Onde a van deixa os alunos na volta da escola."
            "IDA" -> "De onde a van sai para buscar os alunos e levar à escola."
            else -> "De onde a van sai na ida e para onde volta depois da escola."
        }
        Etapa.BAIRROS -> "Os responsáveis desses bairros encontram a sua rota na busca."
        Etapa.TURNO -> "Em que período a rota acontece."
        Etapa.DIAS -> "Em quais dias a van faz esta rota."
        Etapa.CAPACIDADE -> "Quantos alunos cabem na van nesta rota."
        Etapa.VEICULO -> "A van que faz esta rota. Só aparecem veículos com os documentos aprovados."
    }

    private fun mostrarTexto(valor: String, dica: String, tipo: Int) {
        layoutCampo.visibility = View.VISIBLE
        edtCampo.inputType = tipo
        edtCampo.hint = dica
        edtCampo.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        edtCampo.setText(valor)
        edtCampo.setSelection(edtCampo.text?.length ?: 0)
        edtCampo.requestFocus()
    }

    private fun mostrarNota(texto: String) {
        txtNota.text = texto
        txtNota.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------
    // ESCOLHA ÚNICA (sentido, turno)
    // ---------------------------------------------------------

    private fun mostrarOpcoes(
        opcoes: List<Pair<String, String>>,
        selecionada: String?,
        descricao: (String) -> String?,
        aoEscolher: (String) -> Unit
    ) {
        painelOpcoes.visibility = View.VISIBLE
        painelOpcoes.removeAllViews()

        val inflater = LayoutInflater.from(this)
        val itens = opcoes.map { (chave, rotulo) ->
            val item = inflater.inflate(R.layout.item_opcao_rota, painelOpcoes, false)
            item.findViewById<TextView>(R.id.txtTituloOpcaoRota).text = rotulo
            item.findViewById<TextView>(R.id.txtDescricaoOpcaoRota).apply {
                val texto = descricao(chave)
                text = texto.orEmpty()
                visibility = if (texto.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            painelOpcoes.addView(item)
            chave to item
        }

        fun marcar(chave: String?) {
            itens.forEach { (c, item) ->
                val marcado = c == chave
                item.isSelected = marcado
                item.findViewById<ImageView>(R.id.imgCheckOpcaoRota).visibility =
                    if (marcado) View.VISIBLE else View.INVISIBLE
            }
        }

        itens.forEach { (chave, item) ->
            item.setOnClickListener {
                aoEscolher(chave)
                marcar(chave)
                limparErro()
            }
        }
        marcar(selecionada)
    }

    // ---------------------------------------------------------
    // DIAS DA SEMANA
    // ---------------------------------------------------------

    private fun montarDias() {
        val dp = resources.displayMetrics.density
        RotaTextos.DIAS.forEachIndexed { indice, dia ->
            val toggle = TextView(this).apply {
                tag = dia
                text = RotaTextos.diaCurto(dia)
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_dia_toggle)
                setOnClickListener {
                    if (!rascunho.dias.add(dia)) rascunho.dias.remove(dia)
                    atualizarDias()
                }
            }
            containerDias.addView(
                toggle,
                LinearLayout.LayoutParams(0, (42 * dp).toInt(), 1f).apply {
                    if (indice > 0) marginStart = (6 * dp).toInt()
                }
            )
        }
    }

    private fun atualizarDias() {
        limparErro()
        for (i in 0 until containerDias.childCount) {
            val toggle = containerDias.getChildAt(i) as TextView
            val marcado = (toggle.tag as String) in rascunho.dias
            toggle.isSelected = marcado
            toggle.setTextColor(
                ContextCompat.getColor(this, if (marcado) R.color.text_on_dark else R.color.text_primary)
            )
        }
    }

    // ---------------------------------------------------------
    // LOCAL (escola, ponto) E BAIRROS
    // ---------------------------------------------------------

    private fun mostrarLocal() {
        painelLocal.visibility = View.VISIBLE

        val txtLocal = findViewById<TextView>(R.id.txtLocalCriarRota)
        val txtEndereco = findViewById<TextView>(R.id.txtEnderecoLocalCriarRota)
        val img = findViewById<ImageView>(R.id.imgLocalCriarRota)
        val botao = findViewById<MaterialButton>(R.id.btnEscolherLocalCriarRota)

        if (etapa == Etapa.ESCOLA) {
            val escola = rascunho.escola
            img.setImageResource(R.drawable.ic_escola)
            txtLocal.text = escola?.nome ?: "Nenhuma escola escolhida"
            txtEndereco.text = escola?.endereco.orEmpty()
            botao.text = if (escola == null) "Buscar escola" else "Trocar escola"
        } else {
            val endereco = rascunho.origemEndereco
            img.setImageResource(R.drawable.ic_local)
            txtLocal.text = rascunho.origemNome?.takeIf { it.isNotBlank() && it != endereco }
                ?: if (endereco == null) "Nenhum ponto escolhido" else "Ponto escolhido"
            txtEndereco.text = endereco.orEmpty()
            botao.text = if (endereco == null) "Escolher no mapa" else "Trocar no mapa"
        }
        txtEndereco.visibility = if (txtEndereco.text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun atualizarBairros() {
        limparErro()
        chipGroupBairros.removeAllViews()
        rascunho.bairros.forEach { (chave, nome) ->
            val chip = Chip(this).apply {
                text = nome
                isCloseIconVisible = true
                chipBackgroundColor = ContextCompat.getColorStateList(this@CriarRotaActivity, R.color.rota_tile)
                setTextColor(ContextCompat.getColor(this@CriarRotaActivity, R.color.text_primary))
                setOnCloseIconClickListener {
                    rascunho.bairros.remove(chave)
                    atualizarBairros()
                }
            }
            chipGroupBairros.addView(chip)
        }
        txtSemBairros.visibility = if (rascunho.bairros.isEmpty()) View.VISIBLE else View.GONE
        chipGroupBairros.visibility = if (rascunho.bairros.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun configurarLaunchers() {
        selecaoPontoLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val dados = extrairLocal(result) ?: return@registerForActivityResult
            rascunho.origemEndereco = dados.first
            rascunho.origemLatitude = dados.second
            rascunho.origemLongitude = dados.third
            rascunho.origemNome = result.data
                ?.getStringExtra(SelecaoLocalizacaoActivity.EXTRA_BAIRRO)
                .orEmpty()
                .ifBlank { dados.first }
            limparErro()
            mostrarLocal()
        }

        // Busca na lista oficial de escolas (não permite cadastrar)
        selecaoEscolaLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            SelecionarEscolaActivity.extrairEscola(result.data)?.let {
                rascunho.escola = it
                limparErro()
                mostrarLocal()
            }
        }

        selecaoBairroLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            val bairro = data.getStringExtra(SelecaoLocalizacaoActivity.EXTRA_BAIRRO).orEmpty()
            val cidade = data.getStringExtra(SelecaoLocalizacaoActivity.EXTRA_CIDADE).orEmpty()

            if (bairro.isBlank()) {
                Toast.makeText(
                    this,
                    "Não foi possível identificar o bairro. Toque em outro ponto dele.",
                    Toast.LENGTH_LONG
                ).show()
                return@registerForActivityResult
            }

            rascunho.bairros[TextoUtil.chaveBairro(bairro, cidade)] = bairro
            atualizarBairros()
        }
    }

    private fun extrairLocal(result: ActivityResult): Triple<String, Double, Double>? {
        if (result.resultCode != Activity.RESULT_OK) return null
        val data = result.data ?: return null
        val endereco = data.getStringExtra(SelecaoLocalizacaoActivity.EXTRA_ENDERECO)
        val lat = data.getDoubleExtra(SelecaoLocalizacaoActivity.EXTRA_LATITUDE, Double.NaN)
        val lng = data.getDoubleExtra(SelecaoLocalizacaoActivity.EXTRA_LONGITUDE, Double.NaN)
        if (endereco.isNullOrBlank() || lat.isNaN() || lng.isNaN()) return null
        return Triple(endereco, lat, lng)
    }

    // ---------------------------------------------------------
    // CONTINUAR / SALVAR
    // ---------------------------------------------------------

    private fun confirmarEtapa() {
        if (ocupado) return

        // Os campos de texto só vão pro rascunho ao confirmar
        when (etapa) {
            Etapa.NOME -> rascunho.nome = edtCampo.text?.toString()?.trim().orEmpty()
            Etapa.CAPACIDADE -> rascunho.capacidade = edtCampo.text?.toString()?.trim().orEmpty()
            else -> Unit
        }

        erroDaEtapa(etapa)?.let {
            mostrarErro(it)
            return
        }

        when {
            modoEdicao -> viewModel.salvar("Rota atualizada!") { rota ->
                rascunho.aplicarEm(rota, etapa)
            }

            etapa == Etapa.entries.last() -> criarRota()

            else -> mostrarEtapa(Etapa.entries[etapa.ordinal + 1])
        }
    }

    /** Mensagem de erro do campo, ou null se está tudo certo. */
    private fun erroDaEtapa(etapa: Etapa): String? = when (etapa) {
        Etapa.NOME -> if (rascunho.nome.length < 3) "*Informe um nome com pelo menos 3 letras." else null
        Etapa.SENTIDO -> if (rascunho.tipoTrajeto == null) "*Escolha o sentido da rota." else null
        Etapa.ESCOLA -> if (rascunho.escola == null) "*Escolha a escola." else null
        Etapa.PONTO -> if (rascunho.origemEndereco == null) "*Escolha o ponto no mapa." else null
        Etapa.BAIRROS -> if (rascunho.bairros.isEmpty()) "*Adicione ao menos um bairro." else null
        Etapa.TURNO -> if (rascunho.turno == null) "*Escolha o turno." else null
        Etapa.DIAS -> if (rascunho.dias.isEmpty()) "*Escolha ao menos um dia." else null
        Etapa.VEICULO -> if (rascunho.veiculoId == null) "*Escolha o veículo ou \"Definir depois\"." else null
        Etapa.CAPACIDADE -> {
            val capacidade = rascunho.capacidade.toIntOrNull()
            val ocupadas = rotaOriginal?.vagasOcupadas ?: 0
            when {
                capacidade == null || capacidade <= 0 -> "*Informe quantos alunos cabem na van."
                capacidade < ocupadas -> "*A rota já tem $ocupadas aluno(s)."
                else -> null
            }
        }
    }

    private fun criarRota() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sessão expirada.", Toast.LENGTH_SHORT).show()
            return
        }

        // Todas as etapas já foram validadas no caminho; confere de novo
        Etapa.entries.firstNotNullOfOrNull { e -> erroDaEtapa(e)?.let { e } }?.let { pendente ->
            mostrarEtapa(pendente)
            mostrarErro(erroDaEtapa(pendente).orEmpty())
            return
        }

        val escola = rascunho.escola ?: return
        val origemEndereco = rascunho.origemEndereco ?: return

        definirOcupado(true)

        lifecycleScope.launch {
            try {
                // Plano em dia e dentro do limite de rotas
                AssinaturaRepository().exigirPodeCriarRota(uid)

                // Só veículo aprovado vai para a rota (a regra do Firestore repete a trava)
                val veiculoId = rascunho.veiculoId?.takeIf { it.isNotBlank() }
                if (veiculoId != null) veiculoRepository.exigirVeiculoAprovado(veiculoId, uid)

                val rota = rotaRepository.criarRota(
                    motoristaId = uid,
                    veiculoId = veiculoId,
                    nome = rascunho.nome,
                    tipoTrajeto = rascunho.tipoTrajeto ?: "IDA",
                    origemNome = rascunho.origemNome ?: origemEndereco,
                    origemEndereco = origemEndereco,
                    origemLatitude = rascunho.origemLatitude ?: 0.0,
                    origemLongitude = rascunho.origemLongitude ?: 0.0,
                    destinoNome = escola.nome,
                    destinoEndereco = escola.endereco,
                    destinoLatitude = escola.latitude,
                    destinoLongitude = escola.longitude,
                    turno = rascunho.turno ?: "MANHA",
                    diasSemana = rascunho.dias.toList(),
                    capacidadeTotal = rascunho.capacidade.toInt(),
                    escolaId = escola.id,
                    escolaNome = escola.nome,
                    bairrosAtendidos = rascunho.bairros.keys.toList()
                )

                Toast.makeText(this@CriarRotaActivity, "Rota criada!", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this@CriarRotaActivity, RotaDetalheActivity::class.java)
                        .putExtra(RotaDetalheActivity.EXTRA_ROTA_ID, rota.id)
                )
                finish()
            } catch (e: AssinaturaBloqueadaException) {
                definirOcupado(false)
                AssinaturaUi.mostrarBloqueio(this@CriarRotaActivity, e.message ?: "Assinatura necessária.")
            } catch (e: Exception) {
                definirOcupado(false)
                mostrarErro("Não foi possível criar a rota: ${e.message}")
            }
        }
    }

    private fun definirOcupado(valor: Boolean) {
        ocupado = valor
        progress.visibility = if (valor) View.VISIBLE else View.GONE
        btnSalvar.text = if (valor) "" else "Criar rota"
        btnSalvar.isEnabled = !valor
    }

    private fun mostrarErro(mensagem: String) {
        txtErro.text = mensagem
        txtErro.visibility = View.VISIBLE
        if (layoutCampo.visibility == View.VISIBLE) layoutCampo.setBackgroundResource(R.drawable.bg_input_white_red)
    }

    private fun limparErro() {
        txtErro.visibility = View.GONE
        layoutCampo.setBackgroundResource(R.drawable.bg_input_white)
    }
}
