package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.MascaraUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class EnderecoFragment :
    Fragment(R.layout.fragment_cadastro_endereco) {

    private lateinit var edtCEP: TextInputEditText
    private lateinit var edtCidade: TextInputEditText
    private lateinit var edtEstado: TextInputEditText
    private lateinit var edtRua: TextInputEditText
    private lateinit var edtBairro: TextInputEditText
    private lateinit var edtComplemento: TextInputEditText
    private lateinit var edtNumero: TextInputEditText

    private lateinit var layoutCEP: TextInputLayout
    private lateinit var layoutCidade: TextInputLayout
    private lateinit var layoutEstado: TextInputLayout
    private lateinit var layoutRua: TextInputLayout
    private lateinit var layoutBairro: TextInputLayout
    private lateinit var layoutComplemento: TextInputLayout
    private lateinit var layoutNumero: TextInputLayout

    private lateinit var txtErroEndereco: TextView
    private lateinit var btnContinuar: MaterialButton

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        inicializarViews(view)
        configurarCep()
        configurarBotao()
    }

    private fun inicializarViews(view: View) {

        edtCEP = view.findViewById(R.id.edtCEP)
        edtCidade = view.findViewById(R.id.edtCidade)
        edtEstado = view.findViewById(R.id.edtEstado)
        edtRua = view.findViewById(R.id.edtRua)
        edtBairro = view.findViewById(R.id.edtBairro)
        edtComplemento =
            view.findViewById(R.id.edtComplemento)
        edtNumero = view.findViewById(R.id.edtNumero)

        layoutCEP = view.findViewById(R.id.layoutCEP)
        layoutCidade =
            view.findViewById(R.id.layoutCidade)
        layoutEstado =
            view.findViewById(R.id.layoutEstado)
        layoutRua =
            view.findViewById(R.id.layoutRua)
        layoutBairro =
            view.findViewById(R.id.layoutBairro)
        layoutComplemento =
            view.findViewById(R.id.layoutComplemento)
        layoutNumero =
            view.findViewById(R.id.layoutNumero)

        txtErroEndereco =
            view.findViewById(R.id.txtErroEndereco)

        btnContinuar =
            view.findViewById(R.id.btnContinuar)

        edtCEP.inputType =
            InputType.TYPE_CLASS_NUMBER

        txtErroEndereco.visibility =
            View.GONE
    }

    private fun configurarCep() {

        edtCEP.addTextChangedListener(
            MascaraUtil.inserir(
                "#####-###",
                edtCEP
            )
        )

        edtCEP.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) = Unit

                override fun afterTextChanged(
                    s: Editable?
                ) {

                    val cep =
                        s.toString()
                            .replace(
                                "[^0-9]".toRegex(),
                                ""
                            )

                    if (cep.length == 8) {
                        buscarCEP(cep)
                    } else {
                        configurarCamposEditaveis(true)
                    }
                }
            }
        )
    }

    private fun buscarCEP(cep: String) {

        viewLifecycleOwner.lifecycleScope.launch(
            Dispatchers.IO
        ) {

            try {

                val url =
                    URL(
                        "https://viacep.com.br/ws/$cep/json/"
                    )

                val conexao =
                    url.openConnection()
                            as HttpURLConnection

                conexao.requestMethod = "GET"

                val reader =
                    BufferedReader(
                        InputStreamReader(
                            conexao.inputStream
                        )
                    )

                val resultado =
                    StringBuilder()

                var linha: String?

                while (
                    reader.readLine()
                        .also { linha = it } != null
                ) {
                    resultado.append(linha)
                }

                reader.close()
                conexao.disconnect()

                val json =
                    JSONObject(
                        resultado.toString()
                    )

                if (json.has("erro")) {

                    withContext(Dispatchers.Main) {

                        txtErroEndereco.text =
                            "*CEP não encontrado"

                        txtErroEndereco.visibility =
                            View.VISIBLE

                        layoutCEP.setBackgroundResource(
                            R.drawable.bg_input_white_red
                        )

                        configurarCamposEditaveis(true)
                    }

                    return@launch
                }

                val cidade =
                    json.optString(
                        "localidade",
                        ""
                    )

                val estado =
                    json.optString(
                        "uf",
                        ""
                    )

                val rua =
                    json.optString(
                        "logradouro",
                        ""
                    )

                val bairro =
                    json.optString(
                        "bairro",
                        ""
                    )

                withContext(Dispatchers.Main) {

                    edtCidade.setText(cidade)
                    edtEstado.setText(estado)
                    edtRua.setText(rua)
                    edtBairro.setText(bairro)

                    txtErroEndereco.visibility =
                        View.GONE

                    layoutCEP.setBackgroundResource(
                        R.drawable.bg_input_white
                    )

                    configurarCamposEditaveis(false)
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    txtErroEndereco.text =
                        "*Erro ao buscar CEP"

                    txtErroEndereco.visibility =
                        View.VISIBLE

                    configurarCamposEditaveis(true)
                }
            }
        }
    }

    private fun configurarCamposEditaveis(
        editavel: Boolean
    ) {

        edtCidade.isEnabled = editavel
        edtEstado.isEnabled = editavel
        edtRua.isEnabled = editavel
        edtBairro.isEnabled = editavel
    }

    private fun configurarBotao() {

        btnContinuar.setOnClickListener {
            validarFormulario()
        }
    }

    private fun validarFormulario() {

        val cep =
            edtCEP.text
                ?.toString()
                ?.trim()
                ?: ""

        val cidade =
            edtCidade.text
                ?.toString()
                ?.trim()
                ?: ""

        val estado =
            edtEstado.text
                ?.toString()
                ?.trim()
                ?: ""

        val rua =
            edtRua.text
                ?.toString()
                ?.trim()
                ?: ""

        val bairro =
            edtBairro.text
                ?.toString()
                ?.trim()
                ?: ""

        val numero =
            edtNumero.text
                ?.toString()
                ?.trim()
                ?: ""

        val complemento =
            edtComplemento.text
                ?.toString()
                ?.trim()
                ?: ""

        var valido = true

        if (TextUtils.isEmpty(cep)) {
            layoutCEP.setBackgroundResource(
                R.drawable.bg_input_white_red
            )
            valido = false
        } else {
            layoutCEP.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }

        if (TextUtils.isEmpty(cidade)) {
            layoutCidade.setBackgroundResource(
                R.drawable.bg_input_white_red
            )
            valido = false
        } else {
            layoutCidade.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }

        if (TextUtils.isEmpty(estado)) {
            layoutEstado.setBackgroundResource(
                R.drawable.bg_input_white_red
            )
            valido = false
        } else {
            layoutEstado.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }

        if (TextUtils.isEmpty(rua)) {
            layoutRua.setBackgroundResource(
                R.drawable.bg_input_white_red
            )
            valido = false
        } else {
            layoutRua.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }

        if (TextUtils.isEmpty(bairro)) {
            layoutBairro.setBackgroundResource(
                R.drawable.bg_input_white_red
            )
            valido = false
        } else {
            layoutBairro.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }

        if (TextUtils.isEmpty(numero)) {
            layoutNumero.setBackgroundResource(
                R.drawable.bg_input_white_red
            )
            valido = false
        } else {
            layoutNumero.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }

        layoutComplemento.setBackgroundResource(
            R.drawable.bg_input_white
        )

        if (!valido) {

            txtErroEndereco.text =
                "*Preencha todos os campos obrigatórios."

            txtErroEndereco.visibility =
                View.VISIBLE

            return
        }

        txtErroEndereco.visibility =
            View.GONE

        val cadastro =
            CadastroSession.cadastroUsuario

        cadastro.endereco.apply {

            this.cep = cep
            this.cidade = cidade
            this.estado = estado
            this.rua = rua
            this.bairro = bairro
            this.numero = numero
            this.complemento = complemento
        }

        (requireActivity() as CadastroActivity)
            .abrirConfirmacao()
    }
}