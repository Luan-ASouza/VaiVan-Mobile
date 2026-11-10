package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.data.remote.CepClient
import com.example.vaivan.data.repository.CadastroRascunhoRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class EnderecoFragment :
    Fragment(R.layout.fragment_cadastro_endereco) {

    private val viewModel: CadastroViewModel by activityViewModels()
    private val cepClient = CepClient()
    private val rascunhoRepository by lazy { CadastroRascunhoRepository(requireContext()) }

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
        super.onViewCreated(
            view,
            savedInstanceState
        )

        configurarEtapaCadastro(atual = 6)
        inicializarViews(view)
        preencherCamposSalvos()
        configurarCep()
        configurarBotao()
    }

    /** Reabrindo essa etapa (voltou ou retomou um rascunho) — repõe o que já
     * tinha sido digitado/buscado antes. Chamado ANTES de [configurarCep]
     * de propósito: não precisa disparar uma busca de CEP de novo pra algo
     * que já foi resolvido. */
    private fun preencherCamposSalvos() {

        val usuario = viewModel.cadastro.usuario

        edtCEP.setText(usuario.cep)
        edtCidade.setText(usuario.cidade)
        edtEstado.setText(usuario.estado)
        edtRua.setText(usuario.rua)
        edtBairro.setText(usuario.bairro)
        edtNumero.setText(usuario.numero)
        edtComplemento.setText(usuario.complemento)

        val cepJaResolvido = usuario.cidade.isNotBlank() || usuario.estado.isNotBlank() ||
            usuario.rua.isNotBlank() || usuario.bairro.isNotBlank()

        if (cepJaResolvido) {
            configurarCamposEditaveis(editavel = false)
        }
    }

    private fun inicializarViews(
        view: View
    ) {

        edtCEP =
            view.findViewById(R.id.edtCEP)

        edtCidade =
            view.findViewById(R.id.edtCidade)

        edtEstado =
            view.findViewById(R.id.edtEstado)

        edtRua =
            view.findViewById(R.id.edtRua)

        edtBairro =
            view.findViewById(R.id.edtBairro)

        edtComplemento =
            view.findViewById(R.id.edtComplemento)

        edtNumero =
            view.findViewById(R.id.edtNumero)

        layoutCEP =
            view.findViewById(R.id.layoutCEP)

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
                        s?.toString()
                            ?.filter { it.isDigit() }
                            ?: ""

                    if (cep.length == 8) {

                        buscarCEP(cep)

                    } else {

                        configurarCamposEditaveis(
                            editavel = true
                        )
                    }
                }
            }
        )
    }

    private fun buscarCEP(
        cep: String
    ) {

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                val endereco =
                    cepClient.buscarEndereco(cep)

                if (endereco == null) {

                    mostrarErroEndereco(
                        "*CEP não encontrado"
                    )

                    configurarCamposEditaveis(
                        editavel = true
                    )

                    return@launch
                }

                edtCidade.setText(endereco.cidade)
                edtEstado.setText(endereco.estado)
                edtRua.setText(endereco.rua)
                edtBairro.setText(endereco.bairro)

                txtErroEndereco.visibility =
                    View.GONE

                layoutCEP.setBackgroundResource(
                    R.drawable.bg_input_white
                )

                configurarCamposEditaveis(
                    editavel = false
                )

            } catch (
                e: Exception
            ) {

                mostrarErroEndereco(
                    "*Erro ao buscar CEP"
                )

                configurarCamposEditaveis(
                    editavel = true
                )
            }
        }
    }

    private fun mostrarErroEndereco(
        mensagem: String
    ) {

        txtErroEndereco.text =
            mensagem

        txtErroEndereco.visibility =
            View.VISIBLE

        layoutCEP.setBackgroundResource(
            R.drawable.bg_input_white_red
        )
    }

    private fun configurarCamposEditaveis(
        editavel: Boolean
    ) {

        edtCidade.isEnabled =
            editavel

        edtEstado.isEnabled =
            editavel

        edtRua.isEnabled =
            editavel

        edtBairro.isEnabled =
            editavel
    }

    private fun configurarBotao() {

        btnContinuar.setOnClickListener {
            validarFormulario()
        }
    }

    private fun validarFormulario() {

        val cep =
            obterTexto(edtCEP)

        val cidade =
            obterTexto(edtCidade)

        val estado =
            obterTexto(edtEstado)

        val rua =
            obterTexto(edtRua)

        val bairro =
            obterTexto(edtBairro)

        val numero =
            obterTexto(edtNumero)

        val complemento =
            obterTexto(edtComplemento)

        var valido = true

        if (cep.isBlank()) {

            marcarErro(layoutCEP)

            valido = false

        } else {

            limparErro(layoutCEP)
        }

        if (cidade.isBlank()) {

            marcarErro(layoutCidade)

            valido = false

        } else {

            limparErro(layoutCidade)
        }

        if (estado.isBlank()) {

            marcarErro(layoutEstado)

            valido = false

        } else {

            limparErro(layoutEstado)
        }

        if (rua.isBlank()) {

            marcarErro(layoutRua)

            valido = false

        } else {

            limparErro(layoutRua)
        }

        if (bairro.isBlank()) {

            marcarErro(layoutBairro)

            valido = false

        } else {

            limparErro(layoutBairro)
        }

        if (numero.isBlank()) {

            marcarErro(layoutNumero)

            valido = false

        } else {

            limparErro(layoutNumero)
        }

        limparErro(layoutComplemento)

        if (!valido) {

            txtErroEndereco.text =
                "*Preencha todos os campos obrigatórios."

            txtErroEndereco.visibility =
                View.VISIBLE

            return
        }

        txtErroEndereco.visibility =
            View.GONE

        viewModel.definirEndereco(
            cep = cep,
            estado = estado,
            cidade = cidade,
            bairro = bairro,
            rua = rua,
            numero = numero,
            complemento = complemento
        )

        rascunhoRepository.salvar(viewModel.cadastro, CadastroActivity.ETAPA_CONSENTIMENTO)

        (requireActivity() as CadastroActivity)
            .abrirConsentimento()
    }

    private fun obterTexto(
        editText: TextInputEditText
    ): String {

        return editText.text
            ?.toString()
            ?.trim()
            ?: ""
    }

    private fun marcarErro(
        layout: TextInputLayout
    ) {

        layout.setBackgroundResource(
            R.drawable.bg_input_white_red
        )
    }

    private fun limparErro(
        layout: TextInputLayout
    ) {

        layout.setBackgroundResource(
            R.drawable.bg_input_white
        )
    }
}