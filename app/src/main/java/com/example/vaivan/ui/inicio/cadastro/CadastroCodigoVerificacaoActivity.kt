package com.example.vaivan.ui.inicio.cadastro

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.vaivan.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CadastroCodigoVerificacaoActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_validacao_email_responsavel)

        configurarViews()
        configurarAutoAvanco()
        configurarListeners()
    }

    private fun configurarViews() {

        // CAMPOS
        edtCodigo1 = findViewById(R.id.edtCodigo1)
        edtCodigo2 = findViewById(R.id.edtCodigo2)
        edtCodigo3 = findViewById(R.id.edtCodigo3)
        edtCodigo4 = findViewById(R.id.edtCodigo4)

        // LAYOUTS
        layoutCodigo1 = findViewById(R.id.layoutCodigo1)
        layoutCodigo2 = findViewById(R.id.layoutCodigo2)
        layoutCodigo3 = findViewById(R.id.layoutCodigo3)
        layoutCodigo4 = findViewById(R.id.layoutCodigo4)

        // TEXTOS
        txtErroCodigo = findViewById(R.id.txtErroCodigo)
        txtReenviarCodigo = findViewById(R.id.txtReenviarCodigo)

        // BOTÃO
        btnContinuar = findViewById(R.id.btnContinuar)

        txtErroCodigo.visibility = View.GONE
    }

    private fun configurarAutoAvanco() {

        configurarAutoAvanco(edtCodigo1, edtCodigo2)
        configurarAutoAvanco(edtCodigo2, edtCodigo3)
        configurarAutoAvanco(edtCodigo3, edtCodigo4)
    }

    private fun configurarListeners() {

        btnContinuar.setOnClickListener {
            validarCodigo()
        }

        txtReenviarCodigo.setOnClickListener {
            txtErroCodigo.visibility = View.GONE
            resetarCampos()

            // Futuramente:
            // reenviar email
            // chamar API
            // Firebase etc.
        }
    }

    private fun validarCodigo() {

        val codigoDigitado =
            obterCodigo(edtCodigo1) +
                    obterCodigo(edtCodigo2) +
                    obterCodigo(edtCodigo3) +
                    obterCodigo(edtCodigo4)

        if (codigoDigitado == CODIGO_CORRETO) {

            txtErroCodigo.visibility = View.GONE

            resetarCampos()

            startActivity(
                Intent(
                    this,
                    CadastroEnderecoActivity::class.java
                )
            )

            finish()

        } else {

            txtErroCodigo.visibility = View.VISIBLE

            mostrarErroCampos()
        }
    }

    private fun obterCodigo(
        campo: TextInputEditText
    ): String {

        return campo.text
            ?.toString()
            ?.trim()
            ?: ""
    }

    private fun mostrarErroCampos() {

        alterarBackgroundCampos(
            R.drawable.bg_input_white_red
        )
    }

    private fun resetarCampos() {

        alterarBackgroundCampos(
            R.drawable.bg_input_white
        )
    }

    private fun alterarBackgroundCampos(
        background: Int
    ) {

        layoutCodigo1.setBackgroundResource(background)
        layoutCodigo2.setBackgroundResource(background)
        layoutCodigo3.setBackgroundResource(background)
        layoutCodigo4.setBackgroundResource(background)
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
                ) {
                }

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
                ) {
                }
            }
        )
    }
}