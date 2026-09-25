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
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class CadastroVeiculoActivity : AppCompatActivity() {

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

    companion object {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_veiculo)

        configurarRepositories()
        configurarViews()
        configurarSeletorAno()
        configurarListeners()
        configurarErros()
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

        val crlv = uriCrlv

        if (crlv == null) {
            Toast.makeText(
                this,
                "Selecione o arquivo do CRLV",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val autorizacao = uriAutorizacao

        if (autorizacao == null) {
            Toast.makeText(
                this,
                "Selecione o documento de autorização",
                Toast.LENGTH_SHORT
            ).show()

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

        btnSalvar.isEnabled = false
        btnSalvar.text = "Salvando..."

        val veiculo = VeiculoEntity(
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

                val veiculoId =
                    veiculoRepository.salvarVeiculo(
                        veiculo
                    )

                enviarCrlv(
                    veiculoId,
                    motoristaId,
                    crlv
                )

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

    private fun enviarCrlv(
        veiculoId: String,
        motoristaId: String,
        uri: Uri
    ) {

        val referencia = FirebaseStorage
            .getInstance()
            .reference
            .child(
                "documentos_veiculos/" +
                        "$veiculoId/crlv_${System.currentTimeMillis()}"
            )

        enviarArquivo(
            referencia,
            uri,
            onSucesso = { url ->
                salvarDocumento(
                    veiculoId = veiculoId,
                    motoristaId = motoristaId,
                    tipo = TIPO_CRLV,
                    url = url,
                    aoSalvar = {
                        enviarAutorizacao(
                            veiculoId,
                            motoristaId,
                            uriAutorizacao ?: return@salvarDocumento
                        )
                    }
                )
            }
        )
    }

    private fun enviarAutorizacao(
        veiculoId: String,
        motoristaId: String,
        uri: Uri
    ) {

        val referencia = FirebaseStorage
            .getInstance()
            .reference
            .child(
                "documentos_veiculos/" +
                        "$veiculoId/autorizacao_${System.currentTimeMillis()}"
            )

        enviarArquivo(
            referencia,
            uri,
            onSucesso = { url ->
                salvarDocumento(
                    veiculoId = veiculoId,
                    motoristaId = motoristaId,
                    tipo = TIPO_AUTORIZACAO,
                    url = url,
                    aoSalvar = {
                        finalizarCadastro(veiculoId)
                    }
                )
            }
        )
    }

    private fun enviarArquivo(
        referencia: StorageReference,
        uri: Uri,
        onSucesso: (String) -> Unit
    ) {

        val usuario = FirebaseAuth
            .getInstance()
            .currentUser

        if (usuario == null) {
            tratarErroUpload(
                IllegalStateException(
                    "Usuário não está autenticado."
                )
            )
            return
        }

        android.util.Log.d(
            "STORAGE",
            "UID: ${usuario.uid}"
        )

        android.util.Log.d(
            "STORAGE",
            "Caminho: ${referencia.path}"
        )

        referencia
            .putFile(uri)
            .addOnSuccessListener {
                referencia
                    .downloadUrl
                    .addOnSuccessListener { url ->
                        onSucesso(url.toString())
                    }
                    .addOnFailureListener(::tratarErroUpload)
            }
            .addOnFailureListener(::tratarErroUpload)
    }

    private fun salvarDocumento(
        veiculoId: String,
        motoristaId: String,
        tipo: String,
        url: String,
        aoSalvar: () -> Unit
    ) {

        val data = SimpleDateFormat(
            "dd/MM/yyyy",
            Locale("pt", "BR")
        ).format(Date())

        val documento = DocumentoEntity(
            "",
            tipo,
            url,
            data,
            STATUS_DOCUMENTO,
            motoristaId,
            veiculoId,
            0L
        )

        documentoRepository.salvarAsync(
            documento,
            {
                aoSalvar()
                null
            },
            { erro ->
                restaurarBotao()

                Toast.makeText(
                    this,
                    "Erro ao salvar documento: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()

                null
            }
        )
    }

    private fun finalizarCadastro(veiculoId: String) {

        Toast.makeText(
            this,
            "Veículo e documentos enviados!",
            Toast.LENGTH_SHORT
        ).show()

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
        btnSalvar.text = "Salvar e continuar"
    }

    private fun tratarErroUpload(erro: Exception) {

        restaurarBotao()

        Toast.makeText(
            this,
            "Erro ao enviar documento: ${erro.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}