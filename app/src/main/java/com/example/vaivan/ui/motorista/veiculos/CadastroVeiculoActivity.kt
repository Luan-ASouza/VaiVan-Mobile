package com.example.vaivan.ui.motorista.veiculos

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.core.util.ImagemUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.DocumentoEntity
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.ui.motorista.entrada.documentos.StatusDocumentosVeiculoActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class CadastroVeiculoActivity : BaseActivity() {

    companion object {
        const val EXTRA_VEICULO_ID = "extra_veiculo_id"

        private const val ANO_MINIMO = 1990
        private const val LIMITE_CAPACIDADE = 40

        private const val TEXTO_SALVAR = "Salvar e continuar"
        private const val TEXTO_SALVAR_EDICAO = "Salvar alterações"

        private const val STATUS_VEICULO = "PENDENTE"
        private const val STATUS_DOCUMENTO = "EM_ANALISE"

        private const val TIPO_CRLV = "CRLV"
        private const val TIPO_AUTORIZACAO =
            "AUTORIZACAO_TRANSPORTE_ESCOLAR"

        private val REGEX_PLACA =
            Pattern.compile("^[A-Z]{3}[0-9][0-9A-Z][0-9]{2}$")
    }

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
    private lateinit var txtErroCrlv: TextView
    private lateinit var txtErroAutorizacao: TextView

    private lateinit var imgFotoVeiculo: ImageView
    private lateinit var txtStatusFoto: TextView

    private lateinit var btnSelecionarCrlv: View
    private lateinit var btnSelecionarAutorizacao: View
    private lateinit var btnSalvar: MaterialButton
    private lateinit var progress: ProgressBar

    private lateinit var txtNomeArquivoCrlv: TextView
    private lateinit var txtNomeArquivoAutorizacao: TextView

    private lateinit var iconCheckCrlv: ImageView
    private lateinit var iconCheckAutorizacao: ImageView

    private lateinit var veiculoRepository: VeiculoRepository
    private lateinit var documentoRepository: DocumentoRepository

    private var uriCrlv: Uri? = null
    private var uriAutorizacao: Uri? = null

    private val adapterMarca by lazy { ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) }
    private val adapterModelo by lazy { ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) }
    private val adapterCor by lazy { AdapterCorVeiculo(this, CORES_VEICULO) }

    /** Marca+modelo são uma lista fixa (ver [VeiculosEscolares]), sem API —
     * guardado aqui pra saber qual foto de exemplo mostrar. */
    private var veiculoEscolarSelecionado: VeiculoEscolar? = null

    /** A cor é um dropdown fechado nas 6 opções de [CoresVeiculo] — não
     * texto livre, por isso guardada aqui em vez de lida direto do campo. */
    private var corSelecionada: CorVeiculo? = null

    private val pickerCrlv =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                uriCrlv = it

                txtNomeArquivoCrlv.text = nomeDoArquivo(it)
                txtNomeArquivoCrlv.setTextColor(
                    getColor(R.color.text_primary)
                )

                iconCheckCrlv.visibility = View.VISIBLE
                txtErroCrlv.visibility = View.GONE
            }
        }

    private val pickerAutorizacao =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                uriAutorizacao = it

                txtNomeArquivoAutorizacao.text = nomeDoArquivo(it)
                txtNomeArquivoAutorizacao.setTextColor(
                    getColor(R.color.text_primary)
                )

                iconCheckAutorizacao.visibility = View.VISIBLE
                txtErroAutorizacao.visibility = View.GONE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_veiculo)

        veiculoId = intent.getStringExtra(EXTRA_VEICULO_ID)

        configurarRepositories()
        configurarViews()
        configurarEdgeToEdge()
        configurarDropdownsVeiculo()
        configurarListeners()
        configurarLimpezaDeErros()

        if (veiculoId != null) {
            configurarModoEdicao()
            carregarVeiculoParaEdicao(veiculoId!!)
        }
    }

    private fun configurarRepositories() {

        val database = VaivanDatabase.getInstance(this)
        val firestore = FirebaseFirestore.getInstance()

        veiculoRepository = VeiculoRepository(
            database.veiculoDao(),
            firestore
        )

        documentoRepository = DocumentoRepository(
            database.documentoDao(),
            firestore
        )
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
        txtErroCrlv = findViewById(R.id.txtErroCrlv)
        txtErroAutorizacao = findViewById(R.id.txtErroAutorizacao)

        imgFotoVeiculo = findViewById(R.id.imgFotoVeiculo)
        txtStatusFoto = findViewById(R.id.txtStatusFoto)

        btnSelecionarCrlv = findViewById(R.id.btnSelecionarCrlv)
        btnSelecionarAutorizacao = findViewById(R.id.btnSelecionarAutorizacao)
        btnSalvar = findViewById(R.id.btnSalvarEdicaoPerfil)
        progress = findViewById(R.id.progressEdicaoPerfil)

        btnSalvar.text = TEXTO_SALVAR
        findViewById<View>(R.id.btnVoltarCadastroVeiculo).setOnClickListener { finish() }

        txtNomeArquivoCrlv =
            findViewById(R.id.txtNomeArquivoCrlv)

        txtNomeArquivoAutorizacao =
            findViewById(R.id.txtNomeArquivoAutorizacao)

        iconCheckCrlv =
            findViewById(R.id.iconCheckCrlv)

        iconCheckAutorizacao =
            findViewById(R.id.iconCheckAutorizacao)
    }

    private fun configurarListeners() {

        btnSelecionarCrlv.setOnClickListener {
            pickerCrlv.launch(
                arrayOf(
                    "image/*",
                    "application/pdf"
                )
            )
        }

        btnSelecionarAutorizacao.setOnClickListener {
            pickerAutorizacao.launch(
                arrayOf(
                    "image/*",
                    "application/pdf"
                )
            )
        }

        btnSalvar.setOnClickListener {
            validarESalvar()
        }
    }

    /** Cabeçalho escuro sob a status bar (ícones claros), voltar fixo logo
     * abaixo dela e botão Salvar acima do teclado. */
    private fun configurarEdgeToEdge() {

        aplicarGapsDeSistema(aplicarBase = false)
        WindowInsetsUtil.aplicarInsets(findViewById(R.id.conteudoCadastroVeiculo), aplicarTopo = false)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val btnVoltar = findViewById<View>(R.id.btnVoltarCadastroVeiculo)
        val margemTopo = (8 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(btnVoltar) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = margemTopo + barras.top
            }
            insets
        }
    }

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

    /** Foto de exemplo do modelo escolhido — vem junto do app (ver
     * [VeiculosEscolares]), não é buscada por request nenhuma. */
    private fun atualizarFotoPreview(veiculo: VeiculoEscolar?) {
        if (veiculo != null) {
            carregarFotoNoCabecalho(veiculo.foto, apagada = false)
            txtStatusFoto.text = "${veiculo.marca} ${veiculo.modelo} · foto de exemplo"
            txtStatusFoto.setTextColor(getColor(R.color.color_primary))
        } else {
            // Sem modelo escolhido: silhueta apagada da van, só pra não deixar o cabeçalho vazio.
            carregarFotoNoCabecalho(R.drawable.van1_img, apagada = true)
            txtStatusFoto.text = "Escolha marca e modelo pra ver uma foto de exemplo"
            txtStatusFoto.setTextColor(getColor(R.color.text_on_dark_secondary))
        }
    }

    /** Troca a foto do cabeçalho com um fade (Glide, como no resto do app). */
    private fun carregarFotoNoCabecalho(@androidx.annotation.DrawableRes foto: Int, apagada: Boolean) {
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
        findViewById<TextView>(R.id.txtTituloVeiculo).text = "Editar veículo"
        findViewById<TextView>(R.id.txtSubtituloVeiculo).text = "Atualize os dados do seu veículo."

        txtNomeArquivoCrlv.text = "Manter documento atual (toque para trocar)"
        txtNomeArquivoAutorizacao.text = "Manter documento atual (toque para trocar)"

        btnSalvar.text = TEXTO_SALVAR_EDICAO
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
        veiculoEscolarSelecionado = VEICULOS_ESCOLARES.firstOrNull {
            it.marca.equals(veiculo.marca, ignoreCase = true) && it.modelo.equals(veiculo.modelo, ignoreCase = true)
        }
        atualizarFotoPreview(veiculoEscolarSelecionado)

        corSelecionada = CORES_VEICULO.firstOrNull { it.nomeExibicao.equals(veiculo.cor, ignoreCase = true) }
        edtCor.setText(corSelecionada?.nomeExibicao ?: veiculo.cor, false)
        aplicarBolinhaCor(corSelecionada)
    }

    private fun nomeDoArquivo(uri: Uri): String {

        var nome = "arquivo"

        try {
            contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->

                val index = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

                if (
                    index != -1 &&
                    cursor.moveToFirst()
                ) {
                    nome = cursor.getString(index)
                }
            }
        } catch (_: Exception) {
            // Mantém "arquivo" como nome padrão.
        }

        return nome
    }

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
        // Documentos só são obrigatórios no cadastro (na edição a troca é opcional).
        val erroCrlv = !editando && uriCrlv == null
        val erroAutorizacao = !editando && uriAutorizacao == null

        mostrarErro(layoutPlaca, txtErroPlaca, erroPlaca)
        mostrarErro(layoutMarca, txtErroMarca, erroMarca)
        mostrarErro(layoutModelo, txtErroModelo, erroModelo)
        mostrarErro(layoutAno, txtErroAno, erroAno)
        mostrarErro(layoutCor, txtErroCor, erroCor)
        mostrarErro(layoutCapacidade, txtErroCapacidade, erroCapacidade)
        txtErroCrlv.visibility = if (erroCrlv) View.VISIBLE else View.GONE
        txtErroAutorizacao.visibility = if (erroAutorizacao) View.VISIBLE else View.GONE

        val primeiroErro = listOf(
            erroPlaca to layoutPlaca,
            erroMarca to layoutMarca,
            erroModelo to layoutModelo,
            erroAno to layoutAno,
            erroCapacidade to layoutCapacidade,
            erroCor to layoutCor,
            erroCrlv to btnSelecionarCrlv,
            erroAutorizacao to btnSelecionarAutorizacao
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

                val veiculoIdSalvo =
                    veiculoRepository.salvarVeiculo(
                        veiculoParaSalvar
                    )

                // Os dois documentos sobem em paralelo (independentes entre si)
                coroutineScope {
                    uriCrlv?.let {
                        launch { enviarDocumento(veiculoIdSalvo, motoristaId, TIPO_CRLV, "crlv", it) }
                    }
                    uriAutorizacao?.let {
                        launch { enviarDocumento(veiculoIdSalvo, motoristaId, TIPO_AUTORIZACAO, "autorizacao", it) }
                    }
                }

                finalizarCadastro(veiculoIdSalvo, editando)

            } catch (erro: Exception) {

                restaurarBotao()

                Toast.makeText(
                    this@CadastroVeiculoActivity,
                    "Erro ao salvar veículo: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun obterTexto(
        view: TextInputEditText
    ): String {
        return view.text
            ?.toString()
            ?.trim()
            .orEmpty()
    }

    private fun obterTexto(
        view: AutoCompleteTextView
    ): String {
        return view.text
            ?.toString()
            ?.trim()
            .orEmpty()
    }

    /** Envia o arquivo pro Storage e registra o DocumentoEntity
     * correspondente. Reaproveitado tanto no cadastro (CRLV + autorização
     * obrigatórios) quanto na edição (troca opcional de um dos dois).
     *
     * Id determinístico (idDocumentoVeiculo): uma troca de arquivo na edição
     * substitui o documento existente — por isso, se já havia um arquivo
     * aqui, ele é removido do Storage antes do novo upload, pra não sobrar
     * órfão. */
    private suspend fun enviarDocumento(
        veiculoId: String,
        motoristaId: String,
        tipo: String,
        prefixoArquivo: String,
        uri: Uri
    ) {

        val docId = DocumentoRepository.idDocumentoVeiculo(veiculoId, tipo)

        documentoRepository.consultarPorId(docId)?.let { antigo ->
            runCatching {
                FirebaseStorage.getInstance()
                    .getReferenceFromUrl(antigo.arquivo)
                    .delete()
                    .await()
            }
        }

        val referencia = FirebaseStorage
            .getInstance()
            .reference
            .child(
                "documentos_veiculos/" +
                        "$veiculoId/${prefixoArquivo}_${System.currentTimeMillis()}"
            )

        // Foto de documento sobe comprimida (PDF vai como está).
        val ehImagem = contentResolver.getType(uri)?.startsWith("image/") == true
        if (ehImagem) {
            val comprimida = ImagemUtil.comprimir(this, uri, ImagemUtil.DOCUMENTO)
            val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
            referencia.putFile(comprimida, metadata).await()
        } else {
            referencia.putFile(uri).await()
        }
        val url = referencia.downloadUrl.await().toString()

        val data = SimpleDateFormat(
            "dd/MM/yyyy",
            Locale("pt", "BR")
        ).format(Date())

        documentoRepository.salvar(
            DocumentoEntity(
                id = docId,
                tipo = tipo,
                arquivo = url,
                dataEnvio = data,
                statusValidacao = STATUS_DOCUMENTO,
                motoristaId = motoristaId,
                veiculoId = veiculoId,
                lastUpdated = 0L
            )
        )
    }

    private fun finalizarCadastro(veiculoId: String, editando: Boolean) {

        Toast.makeText(
            this,
            if (editando) "Veículo atualizado!" else "Veículo e documentos enviados!",
            Toast.LENGTH_SHORT
        ).show()

        if (editando) {
            finish()
            return
        }

        startActivity(
            Intent(
                this,
                StatusDocumentosVeiculoActivity::class.java
            ).apply {
                putExtra("veiculoId", veiculoId)
            }
        )

        finish()
    }

    private fun restaurarBotao() {
        definirSalvando(false)
    }

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
