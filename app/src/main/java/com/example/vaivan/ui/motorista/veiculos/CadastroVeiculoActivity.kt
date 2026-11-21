package com.example.vaivan.ui.motorista.veiculos

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.DocumentoEntity
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.data.repository.VeiculoVinculadoARotaException
import com.example.vaivan.ui.comum.tema.TemaPerfil
import com.example.vaivan.ui.comum.tema.marcarOpcao
import com.example.vaivan.ui.motorista.entrada.documentos.pintarStatusDocumento
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * Cadastro/edição de veículo, no desenho do perfil do passageiro, com duas abas:
 *  - Informações: o formulário (placa, marca/modelo, ano, lugares, cor) e o Salvar;
 *  - Documentos: CRLV e autorização com o status de cada um (pendente, em
 *    análise, aprovado, rejeitado). Tocar abre [EnvioDocumentoVeiculoActivity].
 *
 * Os documentos dependem do id do veículo: num cadastro novo, o primeiro
 * "Salvar" cria o veículo e já leva para a aba Documentos.
 */
class CadastroVeiculoActivity : BaseActivity() {

    companion object {
        const val EXTRA_VEICULO_ID = "extra_veiculo_id"

        private const val ANO_MINIMO = 1990
        private const val LIMITE_CAPACIDADE = 40

        private const val TEXTO_SALVAR = "Salvar e continuar"
        private const val TEXTO_SALVAR_EDICAO = "Salvar alterações"

        private const val STATUS_VEICULO = "PENDENTE"

        private val REGEX_PLACA =
            Pattern.compile("^[A-Z]{3}[0-9][0-9A-Z][0-9]{2}$")
    }

    private enum class Aba { INFORMACOES, DOCUMENTOS }

    private var veiculoId: String? = null
    private var veiculoCarregado: VeiculoEntity? = null

    private lateinit var edtPlaca: TextInputEditText
    private lateinit var edtMarca: AutoCompleteTextView
    private lateinit var edtModelo: AutoCompleteTextView
    private lateinit var edtAno: AutoCompleteTextView
    private lateinit var edtCor: AutoCompleteTextView
    private lateinit var edtCapacidade: TextInputEditText

    private lateinit var layoutPlaca: TextInputLayout
    private lateinit var layoutMarca: TextInputLayout
    private lateinit var layoutModelo: TextInputLayout
    private lateinit var layoutAno: TextInputLayout
    private lateinit var layoutCor: TextInputLayout
    private lateinit var layoutCapacidade: TextInputLayout

    private lateinit var txtErroPlaca: TextView
    private lateinit var txtErroMarca: TextView
    private lateinit var txtErroModelo: TextView
    private lateinit var txtErroAno: TextView
    private lateinit var txtErroCor: TextView
    private lateinit var txtErroCapacidade: TextView

    private lateinit var txtTitulo: TextView
    private lateinit var txtNomeCabecalho: TextView
    private lateinit var txtSubtitulo: TextView
    private lateinit var imgFotoVeiculo: ImageView
    private lateinit var txtStatusFoto: TextView

    private lateinit var scroll: NestedScrollView
    private lateinit var abaInformacoes: TextView
    private lateinit var abaDocumentos: TextView
    private lateinit var conteudoInformacoes: View
    private lateinit var conteudoDocumentos: View
    private lateinit var rodapeSalvar: View
    private lateinit var btnExcluir: View
    private lateinit var txtDocumentosSemVeiculo: View
    private lateinit var itemCrlv: View
    private lateinit var itemAutorizacao: View

    private lateinit var btnSalvar: MaterialButton
    private lateinit var progress: ProgressBar

    private lateinit var veiculoRepository: VeiculoRepository
    private lateinit var documentoRepository: DocumentoRepository

    private var observacaoDocumentos: Job? = null

    private val adapterMarca by lazy { ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) }
    private val adapterModelo by lazy { ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) }
    private val adapterCor by lazy { AdapterCorVeiculo(this, CORES_VEICULO) }

    /** Marca+modelo são uma lista fixa (ver [VeiculosEscolares]), sem API —
     * guardado aqui pra saber qual foto de exemplo mostrar. */
    private var veiculoEscolarSelecionado: VeiculoEscolar? = null

    /** A cor é um dropdown fechado nas 6 opções de [CoresVeiculo] — não
     * texto livre, por isso guardada aqui em vez de lida direto do campo. */
    private var corSelecionada: CorVeiculo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_veiculo)

        // Topo pela status bar; o Salvar sobe com o teclado
        aplicarGapsDeSistema(aplicarBase = false)
        WindowInsetsUtil.aplicarInsets(findViewById(R.id.conteudoCadastroVeiculo), aplicarTopo = false)

        veiculoId = intent.getStringExtra(EXTRA_VEICULO_ID)

        configurarRepositories()
        configurarViews()
        configurarDropdownsVeiculo()
        configurarLimpezaDeErros()
        configurarAbas()

        atualizarFotoPreview(null)
        atualizarCabecalho()

        val id = veiculoId
        if (id != null) {
            configurarModoEdicao()
            carregarVeiculoParaEdicao(id)
            observarDocumentos(id)
        } else {
            atualizarDocumentosSemVeiculo()
        }
    }

    override fun onStart() {
        super.onStart()
        documentoRepository.iniciarSincronizacao()
    }

    override fun onStop() {
        documentoRepository.pararSincronizacao()
        super.onStop()
    }

    private fun configurarRepositories() {
        val database = VaivanDatabase.getInstance(this)
        val firestore = FirebaseFirestore.getInstance()

        // documentoDao: a exclusão também limpa CRLV/autorização do Room na hora
        veiculoRepository = VeiculoRepository(database.veiculoDao(), firestore, database.documentoDao())
        documentoRepository = DocumentoRepository(database.documentoDao(), firestore)
    }

    private fun configurarViews() {

        edtPlaca = findViewById(R.id.edtPlaca)
        edtMarca = findViewById(R.id.edtMarca)
        edtModelo = findViewById(R.id.edtModelo)
        edtAno = findViewById(R.id.edtAno)
        edtCor = findViewById(R.id.edtCor)
        edtCapacidade = findViewById(R.id.edtCapacidade)

        layoutPlaca = findViewById(R.id.layoutPlaca)
        layoutMarca = findViewById(R.id.layoutMarca)
        layoutModelo = findViewById(R.id.layoutModelo)
        layoutAno = findViewById(R.id.layoutAno)
        layoutCor = findViewById(R.id.layoutCor)
        layoutCapacidade = findViewById(R.id.layoutCapacidade)

        txtErroPlaca = findViewById(R.id.txtErroPlaca)
        txtErroMarca = findViewById(R.id.txtErroMarca)
        txtErroModelo = findViewById(R.id.txtErroModelo)
        txtErroAno = findViewById(R.id.txtErroAno)
        txtErroCor = findViewById(R.id.txtErroCor)
        txtErroCapacidade = findViewById(R.id.txtErroCapacidade)

        txtTitulo = findViewById(R.id.txtTituloVeiculo)
        txtNomeCabecalho = findViewById(R.id.txtNomeVeiculoCabecalho)
        txtSubtitulo = findViewById(R.id.txtSubtituloVeiculo)
        imgFotoVeiculo = findViewById(R.id.imgFotoVeiculo)
        txtStatusFoto = findViewById(R.id.txtStatusFoto)

        scroll = findViewById(R.id.scrollCadastroVeiculo)
        abaInformacoes = findViewById(R.id.abaInformacoesVeiculo)
        abaDocumentos = findViewById(R.id.abaDocumentosVeiculo)
        conteudoInformacoes = findViewById(R.id.conteudoInformacoesVeiculo)
        conteudoDocumentos = findViewById(R.id.conteudoDocumentosVeiculo)
        rodapeSalvar = findViewById(R.id.rodapeSalvarVeiculo)
        btnExcluir = findViewById(R.id.btnExcluirVeiculo)
        btnExcluir.setOnClickListener { confirmarExclusao() }
        txtDocumentosSemVeiculo = findViewById(R.id.txtDocumentosSemVeiculo)
        itemCrlv = findViewById(R.id.itemCrlvVeiculo)
        itemAutorizacao = findViewById(R.id.itemAutorizacaoVeiculo)

        btnSalvar = findViewById(R.id.btnSalvarEdicaoPerfil)
        progress = findViewById(R.id.progressEdicaoPerfil)

        btnSalvar.text = TEXTO_SALVAR
        // Rodapé compartilhado com as telas de perfil (laranja); no motorista o botão é escuro
        btnSalvar.setBackgroundResource(R.drawable.bg_botao_escuro)
        btnSalvar.setOnClickListener { validarESalvar() }

        findViewById<View>(R.id.btnVoltarCadastroVeiculo).setOnClickListener { finish() }

        itemCrlv.setOnClickListener { abrirEnvio(DocumentoRepository.TIPO_CRLV) }
        itemAutorizacao.setOnClickListener { abrirEnvio(DocumentoRepository.TIPO_AUTORIZACAO) }

        edtPlaca.doAfterTextChanged { atualizarCabecalho() }
    }

    // ---------------------------------------------------------
    // ABAS
    // ---------------------------------------------------------

    private fun configurarAbas() {
        abaInformacoes.setOnClickListener { selecionarAba(Aba.INFORMACOES) }
        abaDocumentos.setOnClickListener { selecionarAba(Aba.DOCUMENTOS) }
        selecionarAba(Aba.INFORMACOES)
    }

    private fun selecionarAba(aba: Aba) {
        val informacoes = aba == Aba.INFORMACOES
        marcarOpcao(
            listOf(abaInformacoes, abaDocumentos),
            if (informacoes) abaInformacoes else abaDocumentos,
            TemaPerfil.MOTORISTA
        )
        conteudoInformacoes.visibility = if (informacoes) View.VISIBLE else View.GONE
        conteudoDocumentos.visibility = if (informacoes) View.GONE else View.VISIBLE
        // O Salvar é só das informações; os documentos são enviados um a um
        rodapeSalvar.visibility = if (informacoes) View.VISIBLE else View.GONE
        // Excluir só existe para um veículo já salvo, junto das informações
        btnExcluir.visibility = if (informacoes && veiculoId != null) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------
    // DOCUMENTOS
    // ---------------------------------------------------------

    private fun abrirEnvio(tipo: String) {
        val id = veiculoId
        if (id == null) {
            Toast.makeText(this, "Salve as informações do veículo primeiro.", Toast.LENGTH_SHORT).show()
            selecionarAba(Aba.INFORMACOES)
            return
        }
        startActivity(EnvioDocumentoVeiculoActivity.intent(this, id, tipo))
    }

    /** Antes de o veículo existir: cards apagados e o aviso para salvar. */
    private fun atualizarDocumentosSemVeiculo() {
        txtDocumentosSemVeiculo.visibility = View.VISIBLE
        listOf(itemCrlv, itemAutorizacao).forEach { it.alpha = 0.5f }
        pintarDocumentos(emptyList())
    }

    private fun observarDocumentos(id: String) {
        txtDocumentosSemVeiculo.visibility = View.GONE
        listOf(itemCrlv, itemAutorizacao).forEach { it.alpha = 1f }

        observacaoDocumentos?.cancel()
        observacaoDocumentos = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                documentoRepository.observarPorVeiculo(id).collect { pintarDocumentos(it) }
            }
        }
    }

    private fun pintarDocumentos(documentos: List<DocumentoEntity>) {
        pintarStatusDocumento(
            icone = findViewById(R.id.iconeCrlvVeiculo),
            selo = findViewById(R.id.txtStatusCrlvVeiculo),
            motivo = findViewById(R.id.txtMotivoCrlvVeiculo),
            documento = documentos.find { it.tipo == DocumentoRepository.TIPO_CRLV },
            rotuloNaoEnviado = "Pendente"
        )
        pintarStatusDocumento(
            icone = findViewById(R.id.iconeAutorizacaoVeiculo),
            selo = findViewById(R.id.txtStatusAutorizacaoVeiculo),
            motivo = findViewById(R.id.txtMotivoAutorizacaoVeiculo),
            documento = documentos.find { it.tipo == DocumentoRepository.TIPO_AUTORIZACAO },
            rotuloNaoEnviado = "Pendente"
        )
    }

    // ---------------------------------------------------------
    // ERROS DOS CAMPOS
    // ---------------------------------------------------------

    /** Some com o erro de um campo assim que o usuário mexe nele. */
    private fun configurarLimpezaDeErros() {

        edtPlaca.doAfterTextChanged { mostrarErro(layoutPlaca, txtErroPlaca, false) }
        edtCapacidade.doAfterTextChanged { mostrarErro(layoutCapacidade, txtErroCapacidade, false) }
        edtMarca.doAfterTextChanged { mostrarErro(layoutMarca, txtErroMarca, false) }
        edtModelo.doAfterTextChanged { mostrarErro(layoutModelo, txtErroModelo, false) }
        edtAno.doAfterTextChanged { mostrarErro(layoutAno, txtErroAno, false) }
        edtCor.doAfterTextChanged { mostrarErro(layoutCor, txtErroCor, false) }
    }

    /** Mensagem embaixo do campo + borda vermelha (mesmo padrão das telas de perfil). */
    private fun mostrarErro(layout: TextInputLayout, mensagem: TextView, erro: Boolean) {
        mensagem.visibility = if (erro) View.VISIBLE else View.GONE
        layout.setBackgroundResource(
            if (erro) R.drawable.bg_input_white_red else R.drawable.bg_input_white
        )
    }

    // ---------------------------------------------------------
    // DROPDOWNS (MARCA -> MODELO, lista fixa local — ver VeiculosEscolares)
    // ---------------------------------------------------------

    private fun configurarDropdownsVeiculo() {

        atualizarAdapter(adapterMarca, VEICULOS_ESCOLARES.map { it.marca })
        edtMarca.setAdapter(adapterMarca)

        edtModelo.setAdapter(adapterModelo)
        edtModelo.isEnabled = false

        edtCor.setAdapter(adapterCor)

        edtMarca.setOnItemClickListener { _, _, posicao, _ ->
            val marcaEscolhida = VEICULOS_ESCOLARES.getOrNull(posicao) ?: return@setOnItemClickListener

            veiculoEscolarSelecionado = null
            edtModelo.setText("", false)
            atualizarFotoPreview(null)
            atualizarCabecalho()

            // Hoje a lista é sempre 1 modelo por marca, mas continua um
            // dropdown de verdade (não auto-preenchido) pra já suportar
            // mais de um modelo por marca no futuro sem mudar a mecânica.
            atualizarAdapter(adapterModelo, listOf(marcaEscolhida.modelo))
            edtModelo.isEnabled = true
        }

        edtModelo.setOnItemClickListener { _, _, posicao, _ ->
            val marcaTexto = obterTexto(edtMarca)
            val modeloTexto = adapterModelo.getItem(posicao) ?: return@setOnItemClickListener

            veiculoEscolarSelecionado = VEICULOS_ESCOLARES.firstOrNull {
                it.marca == marcaTexto && it.modelo == modeloTexto
            }
            atualizarFotoPreview(veiculoEscolarSelecionado)
            atualizarCabecalho()
        }

        edtCor.setOnItemClickListener { _, _, posicao, _ ->
            val cor = CORES_VEICULO.getOrNull(posicao) ?: return@setOnItemClickListener
            corSelecionada = cor
            aplicarBolinhaCor(cor)
        }

        configurarSeletorAno()
    }

    private fun configurarSeletorAno() {

        val anoAtual = Calendar.getInstance()
            .get(Calendar.YEAR)

        val anos = (anoAtual + 1 downTo ANO_MINIMO)
            .map { it.toString() }

        edtAno.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, anos)
        )

        // Campos fechados (sem digitação): tocar abre/fecha a lista.
        listOf(edtMarca, edtModelo, edtAno, edtCor).forEach { campo ->
            campo.setOnClickListener {
                if (campo.isPopupShowing) campo.dismissDropDown() else campo.showDropDown()
            }
        }
    }

    /** Nome (marca + modelo) e placa no cabeçalho, como o nome/idade no perfil do passageiro. */
    private fun atualizarCabecalho() {
        val nome = listOf(obterTexto(edtMarca), obterTexto(edtModelo))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        txtNomeCabecalho.text = nome.ifBlank { "Seu veículo" }

        val placa = obterTexto(edtPlaca).uppercase(Locale.getDefault())
        txtSubtitulo.text = placa.ifBlank { "Preencha as informações abaixo" }
    }

    /** Foto de exemplo do modelo escolhido — vem junto do app (ver
     * [VeiculosEscolares]), não é buscada por request nenhuma. */
    private fun atualizarFotoPreview(veiculo: VeiculoEscolar?) {
        if (veiculo != null) {
            carregarFoto(veiculo.foto, apagada = false)
            txtStatusFoto.text = "Foto de exemplo do modelo"
        } else {
            // Sem modelo escolhido: silhueta apagada da van, só pra não deixar o topo vazio.
            carregarFoto(R.drawable.van1_img, apagada = true)
            txtStatusFoto.text = "Escolha marca e modelo para ver a foto"
        }
    }

    /** Troca a foto do topo com um fade (Glide, como no resto do app). */
    private fun carregarFoto(@androidx.annotation.DrawableRes foto: Int, apagada: Boolean) {
        imgFotoVeiculo.animate().alpha(if (apagada) 0.35f else 1f).setDuration(250).start()

        Glide.with(imgFotoVeiculo)
            .load(foto)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(imgFotoVeiculo)
    }

    /** Mostra a bolinha de amostra da cor escolhida dentro do próprio campo
     * (fechado), não só na lista do dropdown — [GradientDrawable] novo a
     * cada chamada porque cada seleção precisa de uma cor diferente (não dá
     * pra reaproveitar uma instância compartilhada). */
    private fun aplicarBolinhaCor(cor: CorVeiculo?) {
        if (cor == null) {
            edtCor.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, setaDropdown(), null)
            return
        }

        val tamanhoPx = (18 * resources.displayMetrics.density).toInt()
        val bolinha = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(cor.corAmostra)
            setStroke((1 * resources.displayMetrics.density).toInt(), ContextCompat.getColor(this@CadastroVeiculoActivity, R.color.light_gray))
            setSize(tamanhoPx, tamanhoPx)
        }

        edtCor.setCompoundDrawablesRelativeWithIntrinsicBounds(bolinha, null, setaDropdown(), null)
    }

    private fun setaDropdown() = ContextCompat.getDrawable(this, R.drawable.ic_seta_baixo)

    private fun atualizarAdapter(adapter: ArrayAdapter<String>, itens: List<String>) {
        adapter.setNotifyOnChange(false)
        adapter.clear()
        adapter.addAll(itens)
        adapter.notifyDataSetChanged()
    }

    // ---------------------------------------------------------
    // MODO EDIÇÃO
    // ---------------------------------------------------------

    private fun configurarModoEdicao() {
        txtTitulo.text = "Editar veículo"
        btnSalvar.text = TEXTO_SALVAR_EDICAO
    }

    /** Excluir veículo (saiu do card da lista). O repository bloqueia se ele
     * estiver numa rota e, se não, apaga também CRLV e autorização. */
    private fun confirmarExclusao() {
        val id = veiculoId ?: return
        val placa = veiculoCarregado?.placa?.takeIf { it.isNotBlank() } ?: "este veículo"

        ConfirmacaoExclusaoHelper.confirmar(
            contexto = this,
            titulo = "Excluir \"$placa\"?",
            mensagem = "O veículo e os documentos enviados dele serão apagados. Essa ação não pode ser desfeita."
        ) {
            lifecycleScope.launch {
                try {
                    veiculoRepository.excluirVeiculo(id)
                    Toast.makeText(this@CadastroVeiculoActivity, "Veículo excluído.", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (erro: VeiculoVinculadoARotaException) {
                    Toast.makeText(this@CadastroVeiculoActivity, erro.message, Toast.LENGTH_LONG).show()
                } catch (erro: Exception) {
                    Toast.makeText(
                        this@CadastroVeiculoActivity,
                        "Erro ao excluir: ${erro.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun carregarVeiculoParaEdicao(id: String) {

        lifecycleScope.launch {
            try {
                val veiculo = veiculoRepository.consultarVeiculo(id)

                if (veiculo == null) {
                    Toast.makeText(
                        this@CadastroVeiculoActivity,
                        "Veículo não encontrado.",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                    return@launch
                }

                veiculoCarregado = veiculo
                preencherFormulario(veiculo)

            } catch (erro: Exception) {
                Toast.makeText(
                    this@CadastroVeiculoActivity,
                    "Erro ao carregar veículo: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
        }
    }

    private fun preencherFormulario(veiculo: VeiculoEntity) {
        edtPlaca.setText(veiculo.placa)
        edtAno.setText(veiculo.anoFabricacao.toString(), false)
        edtCapacidade.setText(veiculo.capacidadePassageiros.toString())

        edtMarca.setText(veiculo.marca, false)
        val modelosDaMarca = VEICULOS_ESCOLARES
            .filter { it.marca.equals(veiculo.marca, ignoreCase = true) }
            .map { it.modelo }
        atualizarAdapter(adapterModelo, modelosDaMarca)
        edtModelo.isEnabled = modelosDaMarca.isNotEmpty()
        edtModelo.setText(veiculo.modelo, false)

        // Dado legado (de fora da lista fixa atual) não encontra
        // correspondência aqui — o texto salvo continua exibido, só sem
        // foto de exemplo até o motorista escolher de novo.
        veiculoEscolarSelecionado = veiculoEscolarDe(veiculo)
        atualizarFotoPreview(veiculoEscolarSelecionado)

        corSelecionada = CORES_VEICULO.firstOrNull { it.nomeExibicao.equals(veiculo.cor, ignoreCase = true) }
        edtCor.setText(corSelecionada?.nomeExibicao ?: veiculo.cor, false)
        aplicarBolinhaCor(corSelecionada)

        atualizarCabecalho()
    }

    // ---------------------------------------------------------
    // SALVAR
    // ---------------------------------------------------------

    private fun validarESalvar() {

        val placa = obterTexto(edtPlaca)
            .uppercase(Locale.getDefault())
            .replace("-", "")

        val marca = obterTexto(edtMarca)
        val modelo = obterTexto(edtModelo)
        val anoTexto = obterTexto(edtAno)
        val cor = corSelecionada?.nomeExibicao.orEmpty()
        val capacidadeTexto = obterTexto(edtCapacidade)

        val ano = anoTexto.toIntOrNull() ?: -1

        val anoAtual = Calendar.getInstance()
            .get(Calendar.YEAR)

        val capacidade = capacidadeTexto.toIntOrNull() ?: -1

        val editando = veiculoCarregado != null

        // Cada campo inválido ganha mensagem + borda vermelha (todos de uma vez).
        val erroPlaca = !REGEX_PLACA.matcher(placa).matches()
        val erroMarca = marca.length < 2
        val erroModelo = modelo.length < 2
        val erroAno = ano !in ANO_MINIMO..anoAtual + 1
        val erroCor = cor.length < 2
        val erroCapacidade = capacidade !in 1..LIMITE_CAPACIDADE

        mostrarErro(layoutPlaca, txtErroPlaca, erroPlaca)
        mostrarErro(layoutMarca, txtErroMarca, erroMarca)
        mostrarErro(layoutModelo, txtErroModelo, erroModelo)
        mostrarErro(layoutAno, txtErroAno, erroAno)
        mostrarErro(layoutCor, txtErroCor, erroCor)
        mostrarErro(layoutCapacidade, txtErroCapacidade, erroCapacidade)

        val primeiroErro = listOf(
            erroPlaca to layoutPlaca,
            erroMarca to layoutMarca,
            erroModelo to layoutModelo,
            erroAno to layoutAno,
            erroCapacidade to layoutCapacidade,
            erroCor to layoutCor
        ).firstOrNull { it.first }?.second

        if (primeiroErro != null) {
            // Rola até o primeiro campo com erro.
            primeiroErro.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, primeiroErro.width, primeiroErro.height + (48 * resources.displayMetrics.density).toInt())
            )
            return
        }

        val motoristaId = FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid

        if (motoristaId == null) {
            Toast.makeText(
                this,
                "Sessão expirada. Faça login novamente.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        definirSalvando(true)

        val veiculoParaSalvar = veiculoCarregado?.copy(
            placa = placa,
            marca = marca,
            modelo = modelo,
            cor = cor,
            anoFabricacao = ano,
            capacidadePassageiros = capacidade
        ) ?: VeiculoEntity(
            id = "",
            placa = placa,
            marca = marca,
            modelo = modelo,
            cor = cor,
            anoFabricacao = ano,
            capacidadePassageiros = capacidade,
            status = STATUS_VEICULO,
            motoristaId = motoristaId,
            lastUpdated = 0L
        )

        lifecycleScope.launch {

            try {
                val veiculoIdSalvo = veiculoRepository.salvarVeiculo(veiculoParaSalvar)
                aoSalvar(veiculoParaSalvar.copy(id = veiculoIdSalvo), editando)

            } catch (erro: Exception) {

                definirSalvando(false)

                Toast.makeText(
                    this@CadastroVeiculoActivity,
                    "Erro ao salvar veículo: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Edição: volta para a lista. Cadastro novo: fica na tela, agora em modo
     * edição, e abre a aba Documentos para o envio do CRLV e da autorização. */
    private fun aoSalvar(veiculo: VeiculoEntity, editando: Boolean) {
        if (editando) {
            Toast.makeText(this, "Veículo atualizado!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        veiculoId = veiculo.id
        veiculoCarregado = veiculo
        configurarModoEdicao()
        definirSalvando(false)
        observarDocumentos(veiculo.id)
        selecionarAba(Aba.DOCUMENTOS)
        scroll.scrollTo(0, 0)

        Toast.makeText(this, "Veículo salvo! Agora envie os documentos.", Toast.LENGTH_LONG).show()
    }

    private fun obterTexto(view: TextInputEditText): String =
        view.text?.toString()?.trim().orEmpty()

    private fun obterTexto(view: AutoCompleteTextView): String =
        view.text?.toString()?.trim().orEmpty()

    private fun definirSalvando(salvando: Boolean) {
        progress.visibility = if (salvando) View.VISIBLE else View.GONE
        btnSalvar.isEnabled = !salvando
        btnSalvar.text = when {
            salvando -> ""
            veiculoCarregado != null -> TEXTO_SALVAR_EDICAO
            else -> TEXTO_SALVAR
        }
    }
}

/** Adapter do dropdown de Cor: cada linha mostra o nome em português + uma
 * bolinha pintada com [CorVeiculo.corAmostra] (ver item_dropdown_cor.xml).
 * `getFilter` sempre devolve a lista inteira — o campo é fechado
 * (inputType=none/focusable=false, igual aos outros dropdowns dessa tela),
 * então não há texto digitado pra filtrar por prefixo de verdade. */
private class AdapterCorVeiculo(
    context: Context,
    private val itens: List<CorVeiculo>
) : ArrayAdapter<CorVeiculo>(context, 0, itens) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_dropdown_cor, parent, false)

        val item = itens[position]
        view.findViewById<TextView>(R.id.txtNomeCor).text = item.nomeExibicao

        val tamanhoPx = (20 * context.resources.displayMetrics.density).toInt()
        val bolinha = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(item.corAmostra)
            setStroke((1 * context.resources.displayMetrics.density).toInt(), ContextCompat.getColor(context, R.color.light_gray))
            setSize(tamanhoPx, tamanhoPx)
        }
        view.findViewById<View>(R.id.viewBolinhaCor).background = bolinha

        return view
    }

    override fun getItem(position: Int): CorVeiculo = itens[position]

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?) = FilterResults().apply {
            values = itens
            count = itens.size
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }
    }
}
