package com.example.vaivan.ui.inicio.cadastro

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.vaivan.R
import com.example.vaivan.core.util.MascaraUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

class CadastroCredenciaisActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dados_de_acesso_responsavel)

        edtEmail = findViewById(R.id.edtEmail)
        edtTelefone = findViewById(R.id.edtTelefone)
        edtSenha = findViewById(R.id.edtSenha)
        edtConfirmarSenha = findViewById(R.id.edtConfirmarSenha)

        edtTelefone.addTextChangedListener(
            MascaraUtil.inserir("(##) #####-####", edtTelefone)
        )

        txtErroEmail = findViewById(R.id.txtErroEmail)
        txtInfoSenha = findViewById(R.id.txtInfoSenha)
        txtErroSenha = findViewById(R.id.txtErroSenha)
        txtErroTelefone = findViewById(R.id.txtErroTelefone)
        txtErroTermos = findViewById(R.id.txtErroTermos)

        btnCadastrar = findViewById(R.id.btnCadastrar)
        spinnerDDD = findViewById(R.id.spinnerDDD)
        checkTermos = findViewById(R.id.checkTermos)

        txtErroEmail.visibility = View.GONE
        txtInfoSenha.visibility = View.GONE
        txtErroSenha.visibility = View.GONE
        txtErroTelefone.visibility = View.GONE
        txtErroTermos.visibility = View.GONE

        layoutEmail = findViewById(R.id.layoutEmail)
        layoutSenha = findViewById(R.id.layoutSenha)
        layoutConfirmarSenha = findViewById(R.id.layoutConfirmarSenha)
        layoutTelefone = findViewById(R.id.layoutTelefone)

        btnCadastrar.setOnClickListener {
            validarFormulario()
        }

        val ddds = arrayOf("+55", "+1", "+351")

        val adapter = ArrayAdapter(
            this,
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
        val email = edtEmail.text?.toString()?.trim() ?: ""
        val telefone = edtTelefone.text?.toString()?.trim() ?: ""
        val senha = edtSenha.text?.toString()?.trim() ?: ""
        val confirmarSenha = edtConfirmarSenha.text?.toString()?.trim() ?: ""

        var formularioValido = true

        if (TextUtils.isEmpty(email) ||
            !Patterns.EMAIL_ADDRESS.matcher(email).matches()
        ) {
            txtErroEmail.text = "*Digite um email válido"
            txtErroEmail.visibility = View.VISIBLE
            layoutEmail.setBackgroundResource(R.drawable.bg_input_white_red)
            formularioValido = false
        } else {
            txtErroEmail.visibility = View.GONE
            layoutEmail.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (!telefoneValido(telefone)) {
            txtErroTelefone.visibility = View.VISIBLE
            layoutTelefone.setBackgroundResource(R.drawable.bg_input_white_red)
            formularioValido = false
        } else {
            txtErroTelefone.visibility = View.GONE
            layoutTelefone.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (!senhaValida(senha)) {
            txtInfoSenha.visibility = View.VISIBLE
            layoutSenha.setBackgroundResource(R.drawable.bg_input_white_red)
            formularioValido = false
        } else {
            txtInfoSenha.visibility = View.GONE
            layoutSenha.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (senha != confirmarSenha) {
            txtErroSenha.visibility = View.VISIBLE
            layoutConfirmarSenha.setBackgroundResource(R.drawable.bg_input_white_red)
            formularioValido = false
        } else {
            txtErroSenha.visibility = View.GONE
            layoutConfirmarSenha.setBackgroundResource(R.drawable.bg_input_white)
        }

        if (termosAceitos() && formularioValido) {
            verificarEmailFirebase(email, telefone, senha)
        }
    }

    @Suppress("DEPRECATION")
    private fun verificarEmailFirebase(
        email: String,
        telefone: String,
        senha: String
    ) {
        btnCadastrar.isEnabled = false

        FirebaseAuth.getInstance()
            .fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->

                btnCadastrar.isEnabled = true

                if (task.isSuccessful) {
                    val existe = !task.result.signInMethods.isNullOrEmpty()

                    if (existe) {
                        txtErroEmail.text = "*Email já cadastrado no sistema"
                        txtErroEmail.visibility = View.VISIBLE
                        layoutEmail.setBackgroundResource(
                            R.drawable.bg_input_white_red
                        )
                    } else {
                        salvarDadosTemporarios(email, telefone, senha)

                        startActivity(
                            Intent(
                                this,
                                CadastroInformacoesPessoaisActivity::class.java
                            )
                        )
                    }
                } else {
                    txtErroEmail.text =
                        "*Erro ao verificar e-mail. Tente novamente."
                    txtErroEmail.visibility = View.VISIBLE
                }
            }
    }

    private fun salvarDadosTemporarios(
        email: String,
        telefone: String,
        senha: String
    ) {
        val cadastro = CadastroSession.cadastroUsuario

        cadastro.email = email
        cadastro.telefone = telefone
        cadastro.senha = senha
    }

    private fun termosAceitos(): Boolean {
        if (!checkTermos.isChecked) {
            txtErroTermos.visibility = View.VISIBLE
            return false
        }

        txtErroTermos.visibility = View.GONE
        return true
    }

    private fun telefoneValido(telefone: String): Boolean {
        val numeros = telefone.replace(Regex("[^0-9]"), "")
        return numeros.length in 10..11
    }

    private fun senhaValida(senha: String): Boolean {
        return senha.length >= 8 &&
                senha.matches(Regex(".*\\d.*")) &&
                senha.matches(
                    Regex(".*[!@#\\$%^&*()_+=|<>?{}\\[\\]~-].*")
                )
    }
}
