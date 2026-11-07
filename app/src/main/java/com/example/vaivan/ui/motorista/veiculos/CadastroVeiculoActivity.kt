package com.example.vaivan.ui.motorista.veiculos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
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
    private lateinit var edtMarca: TextInputEditText
    private lateinit var edtModelo: TextInputEditText
    private lateinit var edtAno: AutoCompleteTextView
    private lateinit var edtCor: TextInputEditText
    private lateinit var edtCapacidade: TextInputEditText

    private lateinit var txtErroPlaca: TextView
    private lateinit var txtErroMarca: TextView
    private lateinit var txtErroModelo: TextView
    private lateinit var txtErroAno: TextView
    private lateinit var txtErroCor: TextView
    private lateinit var txtErroCapacidade: TextView

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
        configurarSeletorAno()
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

    private fun configurarSeletorAno() {

        val anoAtual = Calendar.getInstance()
            .get(Calendar.YEAR)

        val anos = (anoAtual + 1 downTo ANO_MINIMO)
            .map { it.toString() }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            anos
        )

        edtAno.setAdapter(adapter)

        edtAno.setOnClickListener {
            edtAno.showDropDown()
        }
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
        edtMarca.setText(veiculo.marca)
        edtModelo.setText(veiculo.modelo)
        edtAno.setText(veiculo.anoFabricacao.toString(), false)
        edtCor.setText(veiculo.cor)
        edtCapacidade.setText(veiculo.capacidadePassageiros.toString())
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
        val cor = obterTexto(edtCor)
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
            "",
            placa,
            marca,
            modelo,
            cor,
            ano,
            capacidade,
            STATUS_VEICULO,
            motoristaId,
            0L
        )

        lifecycleScope.launch {

            try {

                val veiculoIdSalvo =
                    veiculoRepository.salvarVeiculo(
                        veiculoParaSalvar
                    )

                uriCrlv?.let {
                    enviarDocumento(veiculoIdSalvo, motoristaId, TIPO_CRLV, "crlv", it)
                }

                uriAutorizacao?.let {
                    enviarDocumento(veiculoIdSalvo, motoristaId, TIPO_AUTORIZACAO, "autorizacao", it)
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

        referencia.putFile(uri).await()
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