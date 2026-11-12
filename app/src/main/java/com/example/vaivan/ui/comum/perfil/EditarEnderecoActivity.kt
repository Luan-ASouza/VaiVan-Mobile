package com.example.vaivan.ui.comum.perfil

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.remote.CepClient
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Edição do endereço do perfil. CEP com busca automática (ViaCEP via
 * [CepClient], o mesmo do cadastro) — os campos preenchidos pela busca ficam
 * travados até o CEP mudar. */
class EditarEnderecoActivity : BaseActivity() {

    private val viewModel: EditarPerfilViewModel by viewModels {
        viewModelFactory {
            initializer {
                val db = VaivanDatabase.getInstance(this@EditarEnderecoActivity)
                EditarPerfilViewModel(UsuarioRepository(db.usuarioDao()), FirebaseAuth.getInstance().currentUser?.uid)
            }
        }
    }

    private val cepClient = CepClient()
    private var buscaCep: Job? = null

    /** CEP já carregado do perfil: não refaz a busca (e não sobrescreve o
     * endereço salvo) só por ter preenchido o campo. */
    private var cepCarregado: String? = null

    private lateinit var edtCep: TextInputEditText
    private lateinit var edtEstado: TextInputEditText
    private lateinit var edtCidade: TextInputEditText
    private lateinit var edtBairro: TextInputEditText
    private lateinit var edtRua: TextInputEditText
    private lateinit var edtNumero: TextInputEditText
    private lateinit var edtComplemento: TextInputEditText

    private lateinit var layoutCep: TextInputLayout
    private lateinit var camposObrigatorios: List<Pair<TextInputLayout, TextInputEditText>>

    private lateinit var txtErroCep: TextView
    private lateinit var txtErroGeral: TextView
    private lateinit var btnSalvar: MaterialButton
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_endereco)
        aplicarGapsDeSistema(aplicarBase = false)
        WindowInsetsUtil.aplicarInsets(findViewById(R.id.conteudoEdicaoPerfil), aplicarTopo = false)

        edtCep = findViewById(R.id.edtCepEndereco)
        edtEstado = findViewById(R.id.edtEstadoEndereco)
        edtCidade = findViewById(R.id.edtCidadeEndereco)
        edtBairro = findViewById(R.id.edtBairroEndereco)
        edtRua = findViewById(R.id.edtRuaEndereco)
        edtNumero = findViewById(R.id.edtNumeroEndereco)
        edtComplemento = findViewById(R.id.edtComplementoEndereco)

        layoutCep = findViewById(R.id.layoutCepEndereco)
        camposObrigatorios = listOf(
            layoutCep to edtCep,
            findViewById<TextInputLayout>(R.id.layoutEstadoEndereco) to edtEstado,
            findViewById<TextInputLayout>(R.id.layoutCidadeEndereco) to edtCidade,
            findViewById<TextInputLayout>(R.id.layoutBairroEndereco) to edtBairro,
            findViewById<TextInputLayout>(R.id.layoutRuaEndereco) to edtRua,
            findViewById<TextInputLayout>(R.id.layoutNumeroEndereco) to edtNumero
        )

        txtErroCep = findViewById(R.id.txtErroCepEndereco)
        txtErroGeral = findViewById(R.id.txtErroEndereco)
        btnSalvar = findViewById(R.id.btnSalvarEdicaoPerfil)
        progress = findViewById(R.id.progressEdicaoPerfil)

        configurarCep()
        camposObrigatorios.forEach { (layout, edt) ->
            edt.doAfterTextChanged { layout.setBackgroundResource(R.drawable.bg_input_white) }
        }
        btnSalvar.setOnClickListener { validarESalvar() }

        acompanharEdicao(
            viewModel = viewModel,
            aoMudarOcupado = { ocupado ->
                progress.visibility = if (ocupado) View.VISIBLE else View.GONE
                btnSalvar.text = if (ocupado) "" else "Salvar"
                btnSalvar.isEnabled = !ocupado
            },
            aoCarregar = { usuario -> preencher(usuario) },
            aoFalharSalvar = { mensagem -> mostrarErroGeral(mensagem) }
        )
    }

    private fun preencher(usuario: UsuarioEntity) {
        cepCarregado = usuario.cep.filter { it.isDigit() }
        edtCep.setText(usuario.cep)
        edtEstado.setText(usuario.estado)
        edtCidade.setText(usuario.cidade)
        edtBairro.setText(usuario.bairro)
        edtRua.setText(usuario.rua)
        edtNumero.setText(usuario.numero)
        edtComplemento.setText(usuario.complemento.orEmpty())
    }

    // =========================================================
    // CEP
    // =========================================================

    private fun configurarCep() {
        edtCep.addTextChangedListener(MascaraUtil.inserir("#####-###", edtCep))

        edtCep.doAfterTextChanged { texto ->
            val cep = texto?.toString()?.filter { it.isDigit() }.orEmpty()
            limparErroCep()

            when {
                cep.length != 8 -> {
                    buscaCep?.cancel()
                    travarCamposDoCep(false)
                }
                cep != cepCarregado -> buscarCep(cep)
            }
        }
    }

    private fun buscarCep(cep: String) {
        buscaCep?.cancel()
        buscaCep = lifecycleScope.launch {
            try {
                val endereco = cepClient.buscarEndereco(cep)

                if (endereco == null) {
                    mostrarErroCep("*CEP não encontrado.")
                    travarCamposDoCep(false)
                    return@launch
                }

                edtEstado.setText(endereco.estado)
                edtCidade.setText(endereco.cidade)
                edtBairro.setText(endereco.bairro)
                edtRua.setText(endereco.rua)
                // Número e complemento são da casa antiga: o usuário precisa redigitar.
                edtNumero.setText("")
                edtComplemento.setText("")
                edtNumero.requestFocus()

                travarCamposDoCep(true)

            } catch (erro: kotlinx.coroutines.CancellationException) {
                throw erro
            } catch (erro: Exception) {
                mostrarErroCep("*Erro ao buscar CEP. Preencha manualmente.")
                travarCamposDoCep(false)
            }
        }
    }

    /** Campos vindos da ViaCEP ficam travados; campo vazio na resposta (CEP
     * genérico de cidade pequena, sem rua/bairro) continua editável. */
    private fun travarCamposDoCep(travar: Boolean) {
        listOf(edtEstado, edtCidade, edtBairro, edtRua).forEach { edt ->
            edt.isEnabled = !travar || edt.text.isNullOrBlank()
        }
    }

    private fun mostrarErroCep(mensagem: String) {
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

    private fun texto(edt: TextInputEditText) = edt.text?.toString()?.trim().orEmpty()

    private fun validarESalvar() {
        var valido = true

        camposObrigatorios.forEach { (layout, edt) ->
            if (texto(edt).isBlank()) {
                layout.setBackgroundResource(R.drawable.bg_input_white_red)
                valido = false
            }
        }

        if (texto(edtCep).filter { it.isDigit() }.length != 8) {
            mostrarErroCep("*Informe um CEP com 8 dígitos.")
            valido = false
        }

        if (!valido) {
            mostrarErroGeral("*Preencha os campos destacados.")
            return
        }

        txtErroGeral.visibility = View.GONE

        val cep = texto(edtCep)
        val estado = texto(edtEstado).uppercase()
        val cidade = texto(edtCidade)
        val bairro = texto(edtBairro)
        val rua = texto(edtRua)
        val numero = texto(edtNumero)
        val complemento = texto(edtComplemento).ifBlank { null }

        viewModel.salvar("Endereço atualizado!") { usuario ->
            usuario.copy(
                cep = cep,
                estado = estado,
                cidade = cidade,
                bairro = bairro,
                rua = rua,
                numero = numero,
                complemento = complemento
            )
        }
    }

    private fun mostrarErroGeral(mensagem: String) {
        txtErroGeral.text = mensagem
        txtErroGeral.visibility = View.VISIBLE
    }
}
