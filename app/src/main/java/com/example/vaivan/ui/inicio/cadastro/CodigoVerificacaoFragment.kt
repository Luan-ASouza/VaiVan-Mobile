package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.getValue

class CodigoVerificacaoFragment :
    Fragment(R.layout.fragment_codigo_verificacao) {

    private val viewModel: CadastroViewModel by activityViewModels()
    private lateinit var edtCodigo1: TextInputEditText
    private lateinit var edtCodigo2: TextInputEditText
    private lateinit var edtCodigo3: TextInputEditText
    private lateinit var edtCodigo4: TextInputEditText

    private lateinit var layoutCodigo1: TextInputLayout
    private lateinit var layoutCodigo2: TextInputLayout
    private lateinit var layoutCodigo3: TextInputLayout
    private lateinit var layoutCodigo4: TextInputLayout

    private lateinit var txtErroCodigo: TextView
    private lateinit var txtReenviarCodigo: TextView

    private lateinit var btnContinuar: MaterialButton

    companion object {
        private const val CODIGO_CORRETO = "1234"
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        configurarViews(view)
        configurarAutoAvanco()
        configurarListeners()
    }

    private fun configurarViews(
        view: View
    ) {

        edtCodigo1 =
            view.findViewById(R.id.edtCodigo1)

        edtCodigo2 =
            view.findViewById(R.id.edtCodigo2)

        edtCodigo3 =
            view.findViewById(R.id.edtCodigo3)

        edtCodigo4 =
            view.findViewById(R.id.edtCodigo4)

        layoutCodigo1 =
            view.findViewById(R.id.layoutCodigo1)

        layoutCodigo2 =
            view.findViewById(R.id.layoutCodigo2)

        layoutCodigo3 =
            view.findViewById(R.id.layoutCodigo3)

        layoutCodigo4 =
            view.findViewById(R.id.layoutCodigo4)

        txtErroCodigo =
            view.findViewById(R.id.txtErroCodigo)

        txtReenviarCodigo =
            view.findViewById(R.id.txtReenviarCodigo)

        btnContinuar =
            view.findViewById(R.id.btnContinuar)

        txtErroCodigo.visibility =
            View.GONE
    }

    private fun configurarAutoAvanco() {

        configurarAutoAvanco(
            atual = edtCodigo1,
            proximo = edtCodigo2
        )

        configurarAutoAvanco(
            atual = edtCodigo2,
            proximo = edtCodigo3
        )

        configurarAutoAvanco(
            atual = edtCodigo3,
            proximo = edtCodigo4
        )
    }

    private fun configurarListeners() {

        btnContinuar.setOnClickListener {
            validarCodigo()
        }

        txtReenviarCodigo.setOnClickListener {
            reenviarCodigo()
        }
    }

    private fun validarCodigo() {

        val codigoDigitado =
            obterCodigo(edtCodigo1) +
                    obterCodigo(edtCodigo2) +
                    obterCodigo(edtCodigo3) +
                    obterCodigo(edtCodigo4)

        if (codigoDigitado.isEmpty()) {

            mostrarErro()

            return
        }

        if (codigoDigitado == CODIGO_CORRETO) {

            esconderErro()
            resetarCampos()

            viewLifecycleOwner.lifecycleScope
                .launchWhenResumed {
                    viewModel.definirCodigoVerificacao()
                    abrirEndereco()
                }

        } else {

            mostrarErro()
        }
    }

    private fun reenviarCodigo() {

        esconderErro()
        resetarCampos()

        /*
         * Futuramente:
         *
         * viewModel.definirCodigoVerificacao()
         *
         * ou chamar o mecanismo real
         * de envio/confirmacao do Firebase.
         */
    }

    private fun mostrarErro() {

        txtErroCodigo.visibility =
            View.VISIBLE

        alterarBackgroundCampos(
            R.drawable.bg_input_white_red
        )
    }

    private fun esconderErro() {

        txtErroCodigo.visibility =
            View.GONE
    }

    private fun resetarCampos() {

        edtCodigo1.text?.clear()
        edtCodigo2.text?.clear()
        edtCodigo3.text?.clear()
        edtCodigo4.text?.clear()

        alterarBackgroundCampos(
            R.drawable.bg_input_white
        )

        edtCodigo1.requestFocus()
    }

    private fun alterarBackgroundCampos(
        background: Int
    ) {

        layoutCodigo1.setBackgroundResource(
            background
        )

        layoutCodigo2.setBackgroundResource(
            background
        )

        layoutCodigo3.setBackgroundResource(
            background
        )

        layoutCodigo4.setBackgroundResource(
            background
        )
    }

    private fun configurarAutoAvanco(
        atual: TextInputEditText,
        proximo: TextInputEditText
    ) {

        atual.addTextChangedListener(
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
                ) {

                    if (s?.length == 1) {
                        proximo.requestFocus()
                    }
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )
    }

    private fun obterCodigo(
        campo: TextInputEditText
    ): String {

        return campo.text
            ?.toString()
            ?.trim()
            ?: ""
    }

    private fun abrirEndereco() {

        (requireActivity() as CadastroActivity)
            .abrirEndereco()
    }
}