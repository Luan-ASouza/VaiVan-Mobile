package com.example.trabalhograua.cadastro.responsavel.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.trabalhograua.R
import com.example.trabalhograua.cadastro.CadastroSession
import com.example.trabalhograua.cadastro.MascaraUtil
import com.example.trabalhograua.data.local.VaivanDatabase
import com.example.trabalhograua.data.local.entities.ResponsavelEntity
import com.example.trabalhograua.data.repository.ResponsavelRepository
import com.example.trabalhograua.repository.FirebaseAuthRepository
import com.example.trabalhograua.ui.responsavel.HomeResponsavelActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class EnderecoResponsavel : AppCompatActivity() {

    // CAMPOS
    private lateinit var edtCEP: TextInputEditText
    private lateinit var edtCidade: TextInputEditText
    private lateinit var edtEstado: TextInputEditText
    private lateinit var edtRua: TextInputEditText
    private lateinit var edtBairro: TextInputEditText
    private lateinit var edtComplemento: TextInputEditText
    private lateinit var edtNumero: TextInputEditText

    // LAYOUTS
    private lateinit var layoutCEP: TextInputLayout
    private lateinit var layoutCidade: TextInputLayout
    private lateinit var layoutEstado: TextInputLayout
    private lateinit var layoutRua: TextInputLayout
    private lateinit var layoutBairro: TextInputLayout
    private lateinit var layoutComplemento: TextInputLayout
    private lateinit var layoutNumero: TextInputLayout

    // TEXTO ERRO
    private lateinit var txtErroEndereco: TextView

    // BOTÃO
    private lateinit var btnContinuar: MaterialButton

    private lateinit var repository: ResponsavelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_endereco_responsavel)

        repository = ResponsavelRepository(
            VaivanDatabase.getInstance(this).responsavelDao()
        )

        // =========================
        // CAMPOS
        // =========================
        edtCEP = findViewById(R.id.edtCEP)
        edtCidade = findViewById(R.id.edtCidade)
        edtEstado = findViewById(R.id.edtEstado)
        edtRua = findViewById(R.id.edtRua)
        edtBairro = findViewById(R.id.edtBairro)
        edtComplemento = findViewById(R.id.edtComplemento)
        edtNumero = findViewById(R.id.edtNumero)

        // Força o teclado numérico no CEP programaticamente
        edtCEP.inputType = InputType.TYPE_CLASS_NUMBER

        // =========================
        // MÁSCARA CEP
        // =========================
        edtCEP.addTextChangedListener(
            MascaraUtil.inserir("#####-###", edtCEP)
        )

        // =========================
        // BUSCAR CEP AUTOMÁTICO
        // =========================
        edtCEP.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val cep = s.toString().replace("[^0-9]".toRegex(), "")
                if (cep.length == 8) {
                    buscarCEP(cep)
                } else {
                    // Se o usuário apagar o CEP e ele deixar de ter 8 dígitos,
                    // liberamos os campos novamente para edição manual por segurança.
                    configurarCamposEditaveis(true)
                }
            }
        })

        // =========================
        // LAYOUTS
        // =========================
        layoutCEP = findViewById(R.id.layoutCEP)
        layoutCidade = findViewById(R.id.layoutCidade)
        layoutEstado = findViewById(R.id.layoutEstado)
        layoutRua = findViewById(R.id.layoutRua)
        layoutBairro = findViewById(R.id.layoutBairro)
        layoutComplemento = findViewById(R.id.layoutComplemento)
        layoutNumero = findViewById(R.id.layoutNumero)

        // =========================
        // TEXTO ERRO
        // =========================
        txtErroEndereco = findViewById(R.id.txtErroEndereco)
        txtErroEndereco.visibility = View.GONE

        // =========================
        // BOTÃO
        // =========================
        btnContinuar = findViewById(R.id.btnContinuar)
        btnContinuar.setOnClickListener { validarFormulario() }
    }

    private fun buscarCEP(cep: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://viacep.com.br/ws/$cep/json/")
                val conexao = url.openConnection() as HttpURLConnection
                conexao.requestMethod = "GET"

                val reader = BufferedReader(InputStreamReader(conexao.inputStream))
                val resultado = StringBuilder()
                var linha: String?

                while (reader.readLine().also { linha = it } != null) {
                    resultado.append(linha)
                }
                reader.close()

                val json = JSONObject(resultado.toString())

                if (json.has("erro")) {
                    withContext(Dispatchers.Main) {
                        txtErroEndereco.text = "*CEP não encontrado"
                        txtErroEndereco.visibility = View.VISIBLE
                        layoutCEP.setBackgroundResource(R.drawable.bg_input_white_red)
                        configurarCamposEditaveis(true) // Libera se deu erro
                    }
                    return@launch
                }

                val cidade = json.getString("localidade")
                val estado = json.getString("uf")
                val rua = json.optString("logradouro", "")
                val bairro = json.optString("bairro", "")

                withContext(Dispatchers.Main) {
                    edtCidade.setText(cidade)
                    edtEstado.setText(estado)
                    edtRua.setText(rua)
                    edtBairro.setText(bairro)

                    txtErroEndereco.visibility = View.GONE
                    layoutCEP.setBackgroundResource(R.drawable.bg_input_white)

                    // TRAVA OS CAMPOS RETORNADOS PELA API
                    configurarCamposEditaveis(false)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    txtErroEndereco.text = "*Erro ao buscar CEP"
                    txtErroEndereco.visibility = View.VISIBLE
                    configurarCamposEditaveis(true) // Libera se a requisição falhou
                }
            }
        }
    }

    // Função auxiliar para bloquear ou liberar os inputs que a API preenche
    private fun configurarCamposEditaveis(editavel: Boolean) {
        edtCidade.isEnabled = editavel
        edtEstado.isEnabled = editavel
        edtRua.isEnabled = editavel
        edtBairro.isEnabled = editavel
    }

    private fun validarFormulario() {
        val cep = edtCEP.text?.toString()?.trim() ?: ""
        val cidade = edtCidade.text?.toString()?.trim() ?: ""
        val estado = edtEstado.text?.toString()?.trim() ?: ""
        val rua = edtRua.text?.toString()?.trim() ?: ""
        val bairro = edtBairro.text?.toString()?.trim() ?: ""
        val complemento = edtComplemento.text?.toString()?.trim() ?: ""
        val numero = edtNumero.text?.toString()?.trim() ?: ""

        var formularioValido = true

        if (TextUtils.isEmpty(cep)) {
            layoutCEP.setBackgroundResource(R.drawable.bg_input_white_red)
            formularioValido = false
        } else {
            layoutCEP.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (TextUtils.isEmpty(cidade)) {
            layoutCidade.setBackgroundResource(R.drawable.bg_input_white_red)
            formularioValido = false
        } else {
            layoutCidade.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (TextUtils.isEmpty(estado)) {
            layoutEstado.setBackgroundResource(R.drawable.bg_input_white_red)
            formularioValido = false
        } else {
            layoutEstado.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (TextUtils.isEmpty(rua)) {
            layoutRua.setBackgroundResource(R.drawable.bg_input_white_red)
            formularioValido = false
        } else {
            layoutRua.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (TextUtils.isEmpty(bairro)) {
            layoutBairro.setBackgroundResource(R.drawable.bg_input_white_red)
            formularioValido = false
        } else {
            layoutBairro.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (TextUtils.isEmpty(numero)) {
            layoutNumero.setBackgroundResource(R.drawable.bg_input_white_red)
            formularioValido = false
        } else {
            layoutNumero.setBackgroundResource(R.drawable.bg_input_white)
        }

        layoutComplemento.setBackgroundResource(R.drawable.bg_input_white)

        if (!formularioValido) {
            txtErroEndereco.text = "*Preencha todos os campos obrigatórios."
            txtErroEndereco.visibility = View.VISIBLE
        } else {
            txtErroEndereco.visibility = View.GONE

            salvarEndereco(cep, cidade, estado, rua, bairro, complemento, numero)
            salvarUsuarioAuth()
        }
    }

    private fun salvarUsuarioAuth() {
        val cadastro = CadastroSession.cadastroResponsavel
        val authRepository = FirebaseAuthRepository()

        btnContinuar.isEnabled = false
        txtErroEndereco.visibility = View.GONE

        authRepository.cadastrar(
            cadastro.email,
            cadastro.senha,
            onSuccess = { uid ->
                persistirFirestore(uid)
            },
            onError = { erro ->
                btnContinuar.isEnabled = true
                txtErroEndereco.text = erro.message ?: "*Erro ao realizar autenticação."
                txtErroEndereco.visibility = View.VISIBLE
            }
        )
    }

    private fun salvarEndereco(
        cep: String,
        cidade: String,
        estado: String,
        rua: String,
        bairro: String,
        complemento: String,
        numero: String
    ) {
        val cadastro = CadastroSession.cadastroResponsavel
        cadastro.endereco.apply {
            this.cep = cep
            this.cidade = cidade
            this.estado = estado
            this.rua = rua
            this.bairro = bairro
            this.complemento = complemento
            this.numero = numero
        }
    }

    private fun persistirFirestore(uid: String) {
        val cadastro = CadastroSession.cadastroResponsavel
        val timestampDataNascimento = cadastro.dataNascimento?.let { Timestamp(it) }

        val entity = ResponsavelEntity(
            id = uid,
            nome = cadastro.nome,
            cpf = cadastro.cpf,
            email = cadastro.email,
            telefone = cadastro.telefone,
            dataNascimento = timestampDataNascimento,
            cep = cadastro.endereco.cep,
            estado = cadastro.endereco.estado,
            cidade = cadastro.endereco.cidade,
            bairro = cadastro.endereco.bairro,
            rua = cadastro.endereco.rua,
            numero = cadastro.endereco.numero,
            complemento = cadastro.endereco.complemento ?: "",
            status = "ATIVO",
            emailConfirmado = false,
            lastUpdated = System.currentTimeMillis()
        )

        repository.salvarAsync(
            entity,
            onSuccess = {
                Toast.makeText(
                    this@EnderecoResponsavel,
                    "Cadastro realizado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                CadastroSession.limpar()

                val intent = Intent(this, HomeResponsavelActivity::class.java)
                startActivity(intent)
                finish()
            },
            onError = { e ->
                e.printStackTrace()
                txtErroEndereco.text = "*Autenticado, mas erro ao salvar dados de perfil."
                txtErroEndereco.visibility = View.VISIBLE
                btnContinuar.isEnabled = true
            }
        )
    }
}