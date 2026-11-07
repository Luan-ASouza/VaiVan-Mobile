package com.example.vaivan.ui.comum.perfil

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.ConfirmacaoExclusaoHelper
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.core.util.SeletorDeFotoHelper
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * Tela única de edição de perfil, reaproveitada por motorista e responsável
 * (UsuarioEntity é agnóstico de papel). Mesmos campos/validações/máscaras do
 * fluxo de cadastro (CadastroValidator, MascaraUtil, lookup de CEP do
 * EnderecoFragment) — só que carregando e salvando um usuário já existente.
 */
class EditarPerfilActivity : BaseActivity() {

    companion object {
        private const val IDADE_MINIMA = 18
    }

    private val auth = FirebaseAuth.getInstance()

    private val repository by lazy {
        UsuarioRepository(VaivanDatabase.getInstance(this).usuarioDao())
    }

    // Precisa ser criado em onCreate (antes da Activity chegar em STARTED) —
    // ver SeletorDeFotoHelper.
    private lateinit var seletorDeFoto: SeletorDeFotoHelper

    private lateinit var imgFoto: ShapeableImageView
    private lateinit var btnTrocarFoto: TextView
    private lateinit var btnRemoverFoto: TextView

    private lateinit var edtNome: TextInputEditText
    private lateinit var edtCpf: TextInputEditText
    private lateinit var edtTelefone: TextInputEditText
    private lateinit var edtCep: TextInputEditText
    private lateinit var edtEstado: TextInputEditText
    private lateinit var edtCidade: TextInputEditText
    private lateinit var edtBairro: TextInputEditText
    private lateinit var edtRua: TextInputEditText
    private lateinit var edtNumero: TextInputEditText
    private lateinit var edtComplemento: TextInputEditText

    private lateinit var layoutNome: TextInputLayout
    private lateinit var layoutCpf: TextInputLayout
    private lateinit var layoutTelefone: TextInputLayout
    private lateinit var layoutCep: TextInputLayout
    private lateinit var layoutEstado: TextInputLayout
    private lateinit var layoutCidade: TextInputLayout
    private lateinit var layoutBairro: TextInputLayout
    private lateinit var layoutRua: TextInputLayout
    private lateinit var layoutNumero: TextInputLayout
    private lateinit var layoutComplemento: TextInputLayout

    private lateinit var txtErroNome: TextView
    private lateinit var txtErroCpf: TextView
    private lateinit var txtErroTelefone: TextView
    private lateinit var txtErroIdade: TextView
    private lateinit var txtErroCep: TextView
    private lateinit var txtErroGeral: TextView

    private lateinit var spinnerDia: Spinner
    private lateinit var spinnerMes: Spinner
    private lateinit var spinnerAno: Spinner

    private lateinit var btnSalvar: MaterialButton
    private lateinit var progress: ProgressBar

    private var usuarioAtual: UsuarioEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_perfil)

        seletorDeFoto = SeletorDeFotoHelper(this) { uri -> onFotoEscolhida(uri) }

        inicializarViews()
        configurarNome()
        configurarCpf()
        configurarTelefone()
        configurarCep()
        configurarSpinnersData()
        configurarBotoes()
        carregarUsuario()

        aplicarGapsDeSistema()
    }

    private fun inicializarViews() {
        findViewById<ImageView>(R.id.btnFecharEditarPerfil).setOnClickListener { finish() }

        imgFoto = findViewById(R.id.imgFotoEditarPerfil)
        btnTrocarFoto = findViewById(R.id.btnTrocarFoto)
        btnRemoverFoto = findViewById(R.id.btnRemoverFoto)

        edtNome = findViewById(R.id.edtNomeEditarPerfil)
        edtCpf = findViewById(R.id.edtCpfEditarPerfil)
        edtTelefone = findViewById(R.id.edtTelefoneEditarPerfil)
        edtCep = findViewById(R.id.edtCepEditarPerfil)
        edtEstado = findViewById(R.id.edtEstadoEditarPerfil)
        edtCidade = findViewById(R.id.edtCidadeEditarPerfil)
        edtBairro = findViewById(R.id.edtBairroEditarPerfil)
        edtRua = findViewById(R.id.edtRuaEditarPerfil)
        edtNumero = findViewById(R.id.edtNumeroEditarPerfil)
        edtComplemento = findViewById(R.id.edtComplementoEditarPerfil)

        layoutNome = findViewById(R.id.layoutNomeEditarPerfil)
        layoutCpf = findViewById(R.id.layoutCpfEditarPerfil)
        layoutTelefone = findViewById(R.id.layoutTelefoneEditarPerfil)
        layoutCep = findViewById(R.id.layoutCepEditarPerfil)
        layoutEstado = findViewById(R.id.layoutEstadoEditarPerfil)
        layoutCidade = findViewById(R.id.layoutCidadeEditarPerfil)
        layoutBairro = findViewById(R.id.layoutBairroEditarPerfil)
        layoutRua = findViewById(R.id.layoutRuaEditarPerfil)
        layoutNumero = findViewById(R.id.layoutNumeroEditarPerfil)
        layoutComplemento = findViewById(R.id.layoutComplementoEditarPerfil)

        txtErroNome = findViewById(R.id.txtErroNomeEditarPerfil)
        txtErroCpf = findViewById(R.id.txtErroCpfEditarPerfil)
        txtErroTelefone = findViewById(R.id.txtErroTelefoneEditarPerfil)
        txtErroIdade = findViewById(R.id.txtErroIdadeEditarPerfil)
        txtErroCep = findViewById(R.id.txtErroCepEditarPerfil)
        txtErroGeral = findViewById(R.id.txtErroEditarPerfil)

        spinnerDia = findViewById(R.id.spinnerDiaEditarPerfil)
        spinnerMes = findViewById(R.id.spinnerMesEditarPerfil)
        spinnerAno = findViewById(R.id.spinnerAnoEditarPerfil)

        btnSalvar = findViewById(R.id.btnSalvarEditarPerfil)
        progress = findViewById(R.id.progressEditarPerfil)
    }

    // =========================================================
    // CARREGAMENTO
    // =========================================================

    private fun carregarUsuario() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        mostrarCarregando(true)

        lifecycleScope.launch {
            try {
                val usuario = repository.consultarUsuario(uid)
                if (usuario == null) {
                    Toast.makeText(this@EditarPerfilActivity, "Não foi possível carregar seus dados.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                usuarioAtual = usuario
                preencherFormulario(usuario)

            } catch (erro: Exception) {
                Toast.makeText(this@EditarPerfilActivity, "Erro ao carregar perfil: ${erro.message}", Toast.LENGTH_LONG).show()
                finish()
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun preencherFormulario(usuario: UsuarioEntity) {
        edtNome.setText(usuario.nome)
        edtCpf.setText(usuario.cpf)
        edtTelefone.setText(usuario.telefone)
        edtCep.setText(usuario.cep)
        edtEstado.setText(usuario.estado)
        edtCidade.setText(usuario.cidade)
        edtBairro.setText(usuario.bairro)
        edtRua.setText(usuario.rua)
        edtNumero.setText(usuario.numero)
        edtComplemento.setText(usuario.complemento ?: "")

        selecionarDataNascimento(usuario.dataNascimento)
        exibirFoto(usuario.fotoUrl)
    }

    private fun selecionarDataNascimento(dataNascimento: String?) {
        if (dataNascimento.isNullOrBlank()) return

        val partes = dataNascimento.split("/")
        if (partes.size != 3) return

        val ano = partes[0].toIntOrNull() ?: return
        val mes = partes[1].toIntOrNull() ?: return
        val dia = partes[2].toIntOrNull() ?: return

        val anoAdapter = spinnerAno.adapter as? ArrayAdapter<*>
        val posicaoAno = anoAdapter?.let { adapter ->
            (0 until adapter.count).firstOrNull { adapter.getItem(it).toString() == ano.toString() }
        }

        if (posicaoAno != null) spinnerAno.setSelection(posicaoAno)
        spinnerMes.setSelection((mes - 1).coerceIn(0, 11))
        spinnerDia.setSelection((dia - 1).coerceIn(0, 30))
    }

    // =========================================================
    // FOTO
    // =========================================================

    private fun exibirFoto(fotoUrl: String?) {
        Glide.with(imgFoto)
            .load(fotoUrl)
            .placeholder(R.drawable.ic_perfil)
            .error(R.drawable.ic_perfil)
            .circleCrop()
            .into(imgFoto)
    }

    private fun onFotoEscolhida(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return

        Glide.with(imgFoto).load(uri).circleCrop().into(imgFoto)

        mostrarCarregandoFoto(true)

        lifecycleScope.launch {
            try {
                val url = repository.atualizarFoto(uid, uri)
                usuarioAtual = usuarioAtual?.copy(fotoUrl = url)

                Toast.makeText(this@EditarPerfilActivity, "Foto atualizada!", Toast.LENGTH_SHORT).show()

            } catch (erro: Exception) {
                Toast.makeText(this@EditarPerfilActivity, "Erro ao atualizar foto: ${erro.message}", Toast.LENGTH_LONG).show()
                exibirFoto(usuarioAtual?.fotoUrl)

            } finally {
                mostrarCarregandoFoto(false)
            }
        }
    }

    private fun confirmarRemoverFoto() {
        val uid = auth.currentUser?.uid ?: return
        if (usuarioAtual?.fotoUrl == null) return

        ConfirmacaoExclusaoHelper.confirmar(
            contexto = this,
            titulo = "Remover foto?",
            mensagem = "Sua foto de perfil atual será removida.",
            textoConfirmar = "Sim, remover"
        ) {
            removerFoto(uid)
        }
    }

    private fun removerFoto(uid: String) {
        mostrarCarregandoFoto(true)

        lifecycleScope.launch {
            try {
                repository.removerFoto(uid)
                usuarioAtual = usuarioAtual?.copy(fotoUrl = null)
                exibirFoto(null)

                Toast.makeText(this@EditarPerfilActivity, "Foto removida.", Toast.LENGTH_SHORT).show()

            } catch (erro: Exception) {
                Toast.makeText(this@EditarPerfilActivity, "Erro ao remover foto: ${erro.message}", Toast.LENGTH_LONG).show()

            } finally {
                mostrarCarregandoFoto(false)
            }
        }
    }

    private fun mostrarCarregandoFoto(carregando: Boolean) {
        btnTrocarFoto.isEnabled = !carregando
        btnRemoverFoto.isEnabled = !carregando
    }

    // =========================================================
    // CAMPOS — MÁSCARAS E FILTROS
    // =========================================================

    private fun configurarNome() {
        edtNome.filters = arrayOf(
            InputFilter { source, _, _, _, _, _ ->
                if (source.isNullOrEmpty()) return@InputFilter null

                val permitido = source.toString().all { caractere ->
                    caractere.isLetter() || caractere == ' ' || caractere == '-' || caractere == '\''
                }

                if (permitido) null else ""
            }
        )
    }

    private fun configurarCpf() {
        edtCpf.addTextChangedListener(MascaraUtil.inserir("###.###.###-##", edtCpf))
    }

    private fun configurarTelefone() {
        edtTelefone.addTextChangedListener(MascaraUtil.inserir("(##) #####-####", edtTelefone))
    }

    private fun configurarSpinnersData() {
        val dias = Array(31) { (it + 1).toString() }

        val meses = arrayOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

        val anoAtual = Calendar.getInstance().get(Calendar.YEAR)
        val anos = Array(100) { (anoAtual - it).toString() }

        configurarSpinner(spinnerDia, dias)
        configurarSpinner(spinnerMes, meses)
        configurarSpinner(spinnerAno, anos)
    }

    private fun configurarSpinner(spinner: Spinner, itens: Array<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itens)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    // =========================================================
    // CEP
    // =========================================================

    private fun configurarCep() {
        edtCep.addTextChangedListener(MascaraUtil.inserir("#####-###", edtCep))

        edtCep.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val cep = s?.toString()?.filter { it.isDigit() } ?: ""
                if (cep.length == 8) {
                    buscarCep(cep)
                } else {
                    configurarCamposEnderecoEditaveis(editavel = true)
                }
            }
        })
    }

    private fun buscarCep(cep: String) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var conexao: HttpURLConnection? = null

            try {
                val url = URL("https://viacep.com.br/ws/$cep/json/")
                conexao = url.openConnection() as HttpURLConnection
                conexao.requestMethod = "GET"
                conexao.connectTimeout = 5000
                conexao.readTimeout = 5000

                val reader = BufferedReader(InputStreamReader(conexao.inputStream))
                val resultado = StringBuilder()
                var linha: String?

                while (reader.readLine().also { linha = it } != null) {
                    resultado.append(linha)
                }
                reader.close()

                val json = JSONObject(resultado.toString())

                if (json.optBoolean("erro", false)) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        marcarErroCep("*CEP não encontrado.")
                        configurarCamposEnderecoEditaveis(editavel = true)
                    }
                    return@launch
                }

                val cidade = json.optString("localidade", "")
                val estado = json.optString("uf", "")
                val rua = json.optString("logradouro", "")
                val bairro = json.optString("bairro", "")

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    edtCidade.setText(cidade)
                    edtEstado.setText(estado)
                    edtRua.setText(rua)
                    edtBairro.setText(bairro)

                    limparErroCep()
                    configurarCamposEnderecoEditaveis(editavel = false)
                }

            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    marcarErroCep("*Erro ao buscar CEP.")
                    configurarCamposEnderecoEditaveis(editavel = true)
                }
            } finally {
                conexao?.disconnect()
            }
        }
    }

    private fun configurarCamposEnderecoEditaveis(editavel: Boolean) {
        edtCidade.isEnabled = editavel
        edtEstado.isEnabled = editavel
        edtRua.isEnabled = editavel
        edtBairro.isEnabled = editavel
    }

    private fun marcarErroCep(mensagem: String) {
        txtErroCep.text = mensagem
        txtErroCep.visibility = View.VISIBLE
        layoutCep.setBackgroundResource(R.drawable.bg_input_white_red)
    }

    private fun limparErroCep() {
        txtErroCep.visibility = View.GONE
        layoutCep.setBackgroundResource(R.drawable.bg_input_white)
    }

    // =========================================================
    // SALVAR
    // =========================================================

    private fun configurarBotoes() {
        btnSalvar.setOnClickListener { validarESalvar() }
        btnTrocarFoto.setOnClickListener { seletorDeFoto.abrirSeletor() }
        btnRemoverFoto.setOnClickListener { confirmarRemoverFoto() }
    }

    private fun validarESalvar() {
        val usuario = usuarioAtual ?: return

        val nome = obterTexto(edtNome)
        val cpf = obterTexto(edtCpf)
        val telefone = obterTexto(edtTelefone)
        val cep = obterTexto(edtCep)
        val estado = obterTexto(edtEstado)
        val cidade = obterTexto(edtCidade)
        val bairro = obterTexto(edtBairro)
        val rua = obterTexto(edtRua)
        val numero = obterTexto(edtNumero)
        val complemento = obterTexto(edtComplemento)

        var valido = true

        if (!CadastroValidator.nomeValido(nome)) {
            mostrarErroCampo(layoutNome, txtErroNome)
            valido = false
        } else {
            limparErroCampo(layoutNome, txtErroNome)
        }

        if (!CadastroValidator.cpfValido(cpf)) {
            mostrarErroCampo(layoutCpf, txtErroCpf)
            valido = false
        } else {
            limparErroCampo(layoutCpf, txtErroCpf)
        }

        if (!CadastroValidator.telefoneValido(telefone)) {
            mostrarErroCampo(layoutTelefone, txtErroTelefone)
            valido = false
        } else {
            limparErroCampo(layoutTelefone, txtErroTelefone)
        }

        val dataNascimento = obterDataNascimento()

        if (dataNascimento == null || !CadastroValidator.idadeValida(dataNascimento, IDADE_MINIMA)) {
            txtErroIdade.visibility = View.VISIBLE
            valido = false
        } else {
            txtErroIdade.visibility = View.GONE
        }

        if (cep.isBlank()) {
            marcarErroCampoEndereco(layoutCep)
            valido = false
        } else {
            limparErroCampoEndereco(layoutCep)
        }

        if (estado.isBlank()) {
            marcarErroCampoEndereco(layoutEstado)
            valido = false
        } else {
            limparErroCampoEndereco(layoutEstado)
        }

        if (cidade.isBlank()) {
            marcarErroCampoEndereco(layoutCidade)
            valido = false
        } else {
            limparErroCampoEndereco(layoutCidade)
        }

        if (bairro.isBlank()) {
            marcarErroCampoEndereco(layoutBairro)
            valido = false
        } else {
            limparErroCampoEndereco(layoutBairro)
        }

        if (rua.isBlank()) {
            marcarErroCampoEndereco(layoutRua)
            valido = false
        } else {
            limparErroCampoEndereco(layoutRua)
        }

        if (numero.isBlank()) {
            marcarErroCampoEndereco(layoutNumero)
            valido = false
        } else {
            limparErroCampoEndereco(layoutNumero)
        }

        limparErroCampoEndereco(layoutComplemento)

        if (!valido || dataNascimento == null) {
            txtErroGeral.text = "*Revise os campos destacados."
            txtErroGeral.visibility = View.VISIBLE
            return
        }

        txtErroGeral.visibility = View.GONE

        val ano = dataNascimento.get(Calendar.YEAR)
        val mes = dataNascimento.get(Calendar.MONTH) + 1
        val dia = dataNascimento.get(Calendar.DAY_OF_MONTH)
        val dataFormatada = String.format("%04d/%02d/%02d", ano, mes, dia)

        val usuarioAtualizado = usuario.copy(
            nome = nome,
            cpf = cpf,
            telefone = telefone,
            dataNascimento = dataFormatada,
            cep = cep,
            estado = estado,
            cidade = cidade,
            bairro = bairro,
            rua = rua,
            numero = numero,
            complemento = complemento
        )

        salvar(usuarioAtualizado)
    }

    private fun salvar(usuario: UsuarioEntity) {
        mostrarCarregando(true)

        lifecycleScope.launch {
            try {
                repository.salvarUsuario(usuario)
                usuarioAtual = usuario

                Toast.makeText(this@EditarPerfilActivity, "Perfil atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                finish()

            } catch (erro: Exception) {
                txtErroGeral.text = "*Erro ao salvar: ${erro.message}"
                txtErroGeral.visibility = View.VISIBLE

            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun obterDataNascimento(): Calendar? {
        val dia = spinnerDia.selectedItem?.toString()?.toIntOrNull() ?: return null
        val mes = spinnerMes.selectedItemPosition
        val ano = spinnerAno.selectedItem?.toString()?.toIntOrNull() ?: return null

        if (!CadastroValidator.dataValida(dia = dia, mes = mes, ano = ano)) return null

        return try {
            val calendario = Calendar.getInstance()
            calendario.isLenient = false
            calendario.set(ano, mes, dia, 0, 0, 0)
            calendario.set(Calendar.MILLISECOND, 0)
            calendario.time
            calendario
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        progress.visibility = if (carregando) View.VISIBLE else View.GONE
        btnSalvar.isEnabled = !carregando
    }

    private fun obterTexto(editText: TextInputEditText): String =
        editText.text?.toString()?.trim() ?: ""

    private fun mostrarErroCampo(layout: TextInputLayout, mensagem: TextView) {
        mensagem.visibility = View.VISIBLE
        layout.setBackgroundResource(R.drawable.bg_input_white_red)
    }

    private fun limparErroCampo(layout: TextInputLayout, mensagem: TextView) {
        mensagem.visibility = View.GONE
        layout.setBackgroundResource(R.drawable.bg_input_white)
    }

    private fun marcarErroCampoEndereco(layout: TextInputLayout) {
        layout.setBackgroundResource(R.drawable.bg_input_white_red)
    }

    private fun limparErroCampoEndereco(layout: TextInputLayout) {
        layout.setBackgroundResource(R.drawable.bg_input_white)
    }
}
