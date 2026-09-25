package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.vaivan.R
import com.example.vaivan.core.util.MascaraUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

class CredenciaisFragment :
    Fragment(R.layout.fragment_credenciais) {

    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtTelefone: TextInputEditText
    private lateinit var edtSenha: TextInputEditText
    private lateinit var edtConfirmarSenha: TextInputEditText

    private lateinit var txtErroEmail: TextView
    private lateinit var txtInfoSenha: TextView
    private lateinit var txtErroSenha: TextView
    private lateinit var txtErroTermos: TextView
    private lateinit var txtErroTelefone: TextView

    private lateinit var spinnerDDD: Spinner
    private lateinit var checkTermos: CheckBox

    private lateinit var layoutEmail: TextInputLayout
    private lateinit var layoutSenha: TextInputLayout
    private lateinit var layoutConfirmarSenha: TextInputLayout
    private lateinit var layoutTelefone: TextInputLayout

    private lateinit var btnCadastrar: MaterialButton

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        edtEmail =
            view.findViewById(R.id.edtEmail)

        edtTelefone =
            view.findViewById(R.id.edtTelefone)

        edtSenha =
            view.findViewById(R.id.edtSenha)

        edtConfirmarSenha =
            view.findViewById(R.id.edtConfirmarSenha)


        txtErroEmail =
            view.findViewById(R.id.txtErroEmail)

        txtInfoSenha =
            view.findViewById(R.id.txtInfoSenha)

        txtErroSenha =
            view.findViewById(R.id.txtErroSenha)

        txtErroTelefone =
            view.findViewById(R.id.txtErroTelefone)

        txtErroTermos =
            view.findViewById(R.id.txtErroTermos)


        btnCadastrar =
            view.findViewById(R.id.btnCadastrar)

        spinnerDDD =
            view.findViewById(R.id.spinnerDDD)

        checkTermos =
            view.findViewById(R.id.checkTermos)


        layoutEmail =
            view.findViewById(R.id.layoutEmail)

        layoutSenha =
            view.findViewById(R.id.layoutSenha)

        layoutConfirmarSenha =
            view.findViewById(R.id.layoutConfirmarSenha)

        layoutTelefone =
            view.findViewById(R.id.layoutTelefone)


        // Máscara do telefone

        edtTelefone.addTextChangedListener(
            MascaraUtil.inserir(
                "(##) #####-####",
                edtTelefone
            )
        )


        // Esconde mensagens inicialmente

        txtErroEmail.visibility = View.GONE
        txtInfoSenha.visibility = View.GONE
        txtErroSenha.visibility = View.GONE
        txtErroTelefone.visibility = View.GONE
        txtErroTermos.visibility = View.GONE


        // Botão

        btnCadastrar.setOnClickListener {
            validarFormulario()
        }


        // DDD / código do país

        val ddds =
            arrayOf(
                "+55",
                "+1",
                "+351"
            )

        val adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                ddds
            )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerDDD.adapter = adapter
        spinnerDDD.setSelection(0)
    }


    private fun validarFormulario() {

        val email =
            edtEmail.text
                ?.toString()
                ?.trim()
                ?: ""

        val telefone =
            edtTelefone.text
                ?.toString()
                ?.trim()
                ?: ""

        val senha =
            edtSenha.text
                ?.toString()
                ?.trim()
                ?: ""

        val confirmarSenha =
            edtConfirmarSenha.text
                ?.toString()
                ?.trim()
                ?: ""


        var formularioValido = true


        // EMAIL

        if (
            TextUtils.isEmpty(email) ||
            !Patterns.EMAIL_ADDRESS
                .matcher(email)
                .matches()
        ) {

            txtErroEmail.text =
                "*Digite um email válido"

            txtErroEmail.visibility =
                View.VISIBLE

            layoutEmail.setBackgroundResource(
                R.drawable.bg_input_white_red
            )

            formularioValido = false

        } else {

            txtErroEmail.visibility =
                View.GONE

            layoutEmail.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }


        // TELEFONE

        if (!telefoneValido(telefone)) {

            txtErroTelefone.visibility =
                View.VISIBLE

            layoutTelefone.setBackgroundResource(
                R.drawable.bg_input_white_red
            )

            formularioValido = false

        } else {

            txtErroTelefone.visibility =
                View.GONE

            layoutTelefone.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }


        // SENHA

        if (!senhaValida(senha)) {

            txtInfoSenha.visibility =
                View.VISIBLE

            layoutSenha.setBackgroundResource(
                R.drawable.bg_input_white_red
            )

            formularioValido = false

        } else {

            txtInfoSenha.visibility =
                View.GONE

            layoutSenha.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }


        // CONFIRMAÇÃO DA SENHA

        if (senha != confirmarSenha) {

            txtErroSenha.visibility =
                View.VISIBLE

            layoutConfirmarSenha.setBackgroundResource(
                R.drawable.bg_input_white_red
            )

            formularioValido = false

        } else {

            txtErroSenha.visibility =
                View.GONE

            layoutConfirmarSenha.setBackgroundResource(
                R.drawable.bg_input_white
            )
        }


        // TERMOS

        val termosValidos =
            termosAceitos()


        if (
            formularioValido &&
            termosValidos
        ) {

            verificarEmailFirebase(
                email,
                telefone,
                senha
            )
        }
    }


    @Suppress("DEPRECATION")
    private fun verificarEmailFirebase(
        email: String,
        telefone: String,
        senha: String
    ) {

        btnCadastrar.isEnabled = false

        FirebaseAuth
            .getInstance()
            .fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->

                btnCadastrar.isEnabled = true

                if (task.isSuccessful) {

                    val existe =
                        !task.result
                            .signInMethods
                            .isNullOrEmpty()


                    if (existe) {

                        txtErroEmail.text =
                            "*Email já cadastrado no sistema"

                        txtErroEmail.visibility =
                            View.VISIBLE

                        layoutEmail.setBackgroundResource(
                            R.drawable.bg_input_white_red
                        )

                    } else {

                        salvarDadosTemporarios(
                            email,
                            telefone,
                            senha
                        )

                        abrirProximaEtapa()
                    }

                } else {

                    txtErroEmail.text =
                        "*Erro ao verificar e-mail. Tente novamente."

                    txtErroEmail.visibility =
                        View.VISIBLE
                }
            }
    }


    private fun salvarDadosTemporarios(
        email: String,
        telefone: String,
        senha: String
    ) {

        val cadastro =
            CadastroSession.cadastroUsuario

        cadastro.email =
            email

        cadastro.telefone =
            telefone

        cadastro.senha =
            senha
    }


    private fun abrirProximaEtapa() {

        (requireActivity() as CadastroActivity)
            .abrirInformacoesPessoais()
    }


    private fun termosAceitos(): Boolean {

        if (!checkTermos.isChecked) {

            txtErroTermos.visibility =
                View.VISIBLE

            return false
        }

        txtErroTermos.visibility =
            View.GONE

        return true
    }


    private fun telefoneValido(
        telefone: String
    ): Boolean {

        val numeros =
            telefone.replace(
                Regex("[^0-9]"),
                ""
            )

        return numeros.length in 10..11
    }


    private fun senhaValida(
        senha: String
    ): Boolean {

        return senha.length >= 8 &&
                senha.matches(
                    Regex(".*\\d.*")
                ) &&
                senha.matches(
                    Regex(
                        ".*[!@#\\$%^&*()_+=|<>?{}\\[\\]~-].*"
                    )
                )
    }
}