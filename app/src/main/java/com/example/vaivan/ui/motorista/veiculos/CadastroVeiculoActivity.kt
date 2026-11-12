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
import android.widget.Button
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.ImagemUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.DocumentoEntity
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.ui.motorista.entrada.documentos.StatusDocumentosVeiculoActivity
import com.google.android.material.textfield.TextInputEditText
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

class CadastroVeiculoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VEICULO_ID = "extra_veiculo_id"

        private const val ANO_MINIMO = 1990
        private const val LIMITE_CAPACIDADE = 40

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

    private lateinit var txtErroPlaca: TextView
    private lateinit var txtErroMarca: TextView
    private lateinit var txtErroModelo: TextView
    private lateinit var txtErroAno: TextView
    private lateinit var txtErroCor: TextView
    private lateinit var txtErroCapacidade: TextView

    private lateinit var imgFotoVeiculo: ImageView
    private lateinit var txtStatusFoto: TextView

    private lateinit var btnSelecionarCrlv: Button
    private lateinit var btnSelecionarAutorizacao: Button
    private lateinit var btnSalvar: Button

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
                    getColor(R.color.black)
                )

                iconCheckCrlv.visibility = View.VISIBLE
            }
        }

    private val pickerAutorizacao =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                uriAutorizacao = it

                txtNomeArquivoAutorizacao.text = nomeDoArquivo(it)
                txtNomeArquivoAutorizacao.setTextColor(
                    getColor(R.color.black)
                )

                iconCheckAutorizacao.visibility = View.VISIBLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_veiculo)

        veiculoId = intent.getStringExtra(EXTRA_VEICULO_ID)

        configurarRepositories()
        configurarViews()
        configurarDropdownsVeiculo()
        configurarListeners()
        configurarErros()

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

        txtErroPlaca = findViewById(R.id.txtErroPlaca)
        txtErroMarca = findViewById(R.id.txtErroMarca)
        txtErroModelo = findViewById(R.id.txtErroModelo)
        txtErroAno = findViewById(R.id.txtErroAno)
        txtErroCor = findViewById(R.id.txtErroCor)
        txtErroCapacidade = findViewById(R.id.txtErroCapacidade)

        imgFotoVeiculo = findViewById(R.id.imgFotoVeiculo)
        txtStatusFoto = findViewById(R.id.txtStatusFoto)

        btnSelecionarCrlv = findViewById(R.id.btnSelecionarCrlv)
        btnSelecionarAutorizacao = findViewById(R.id.btnSelecionarAutorizacao)
        btnSalvar = findViewById(R.id.btnSalvarVeiculo)

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

    private fun configurarErros() {

        listOf(
            txtErroPlaca,
            txtErroMarca,
            txtErroModelo,
            txtErroAno,
            txtErroCor,
            txtErroCapacidade
        ).forEach {
            it.visibility = View.GONE
        }
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

        edtAno.setOnClickListener {
            edtAno.showDropDown()
        }
    }

    /** Foto de exemplo do modelo escolhido — vem junto do app (ver
     * [VeiculosEscolares]), não é buscada por request nenhuma. */
    private fun atualizarFotoPreview(veiculo: VeiculoEscolar?) {
        if (veiculo != null) {
            imgFotoVeiculo.setImageResource(veiculo.foto)
            txtStatusFoto.text = "Foto de exemplo do modelo"
        } else {
            imgFotoVeiculo.setImageResource(R.drawable.ic_truck)
            txtStatusFoto.text = "Escolha marca e modelo pra ver uma foto de exemplo"
        }
    }

    /** Mostra a bolinha de amostra da cor escolhida dentro do próprio campo
     * (fechado), não só na lista do dropdown — [GradientDrawable] novo a
     * cada chamada porque cada seleção precisa de uma cor diferente (não dá
     * pra reaproveitar uma instância compartilhada). */
    private fun aplicarBolinhaCor(cor: CorVeiculo?) {
        if (cor == null) {
            edtCor.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            return
        }

        val tamanhoPx = (18 * resources.displayMetrics.density).toInt()
        val bolinha = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(cor.corAmostra)
            setStroke((1 * resources.displayMetrics.density).toInt(), ContextCompat.getColor(this@CadastroVeiculoActivity, R.color.light_gray))
            setSize(tamanhoPx, tamanhoPx)
        }

        edtCor.setCompoundDrawablesRelativeWithIntrinsicBounds(bolinha, null, null, null)
    }

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
        findViewById<TextView>(R.id.txtSubtituloVeiculo).text = "Atualize os dados do seu veículo"

        txtNomeArquivoCrlv.text = "Manter documento atual (opcional trocar)"
        txtNomeArquivoAutorizacao.text = "Manter documento atual (opcional trocar)"

        btnSalvar.text = "Salvar alterações"
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

        var valido = true

        if (!REGEX_PLACA.matcher(placa).matches()) {
            txtErroPlaca.visibility = View.VISIBLE
            valido = false
        } else {
            txtErroPlaca.visibility = View.GONE
        }

        if (marca.length < 2) {
            txtErroMarca.visibility = View.VISIBLE
            valido = false
        } else {
            txtErroMarca.visibility = View.GONE
        }

        if (modelo.length < 2) {
            txtErroModelo.visibility = View.VISIBLE
            valido = false
        } else {
            txtErroModelo.visibility = View.GONE
        }

        val ano = anoTexto.toIntOrNull() ?: -1

        val anoAtual = Calendar.getInstance()
            .get(Calendar.YEAR)

        if (ano !in ANO_MINIMO..anoAtual + 1) {
            txtErroAno.visibility = View.VISIBLE
            valido = false
        } else {
            txtErroAno.visibility = View.GONE
        }

        if (cor.length < 2) {
            txtErroCor.visibility = View.VISIBLE
            valido = false
        } else {
            txtErroCor.visibility = View.GONE
        }

        val capacidade = capacidadeTexto.toIntOrNull() ?: -1

        if (capacidade !in 1..LIMITE_CAPACIDADE) {
            txtErroCapacidade.visibility = View.VISIBLE
            valido = false
        } else {
            txtErroCapacidade.visibility = View.GONE
        }

        if (!valido) return

        val editando = veiculoCarregado != null

        if (!editando) {
            if (uriCrlv == null) {
                Toast.makeText(
                    this,
                    "Selecione o arquivo do CRLV",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            if (uriAutorizacao == null) {
                Toast.makeText(
                    this,
                    "Selecione o documento de autorização",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }
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

        btnSalvar.isEnabled = false
        btnSalvar.text = "Salvando..."

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
        btnSalvar.isEnabled = true
        btnSalvar.text = if (veiculoCarregado != null) "Salvar alterações" else "Salvar e continuar"
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
