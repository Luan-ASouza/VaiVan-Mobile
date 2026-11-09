package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.remote.PaisTelefone
import com.example.vaivan.data.remote.PaisTelefoneClient
import com.example.vaivan.data.repository.CadastroRascunhoRepository
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Etapa 1 — identificação: e-mail + telefone. */
class IdentificacaoFragment : Fragment(R.layout.fragment_identificacao) {

    private val viewModel: CadastroViewModel by activityViewModels()
    private val authRepository = FirebaseAuthRepository()
    private val paisTelefoneClient = PaisTelefoneClient()
    private val rascunhoRepository by lazy { CadastroRascunhoRepository(requireContext()) }

    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtTelefone: TextInputEditText
    private lateinit var layoutEmail: TextInputLayout
    private lateinit var layoutTelefone: TextInputLayout
    private lateinit var txtErroEmail: TextView
    private lateinit var txtErroTelefone: TextView
    private lateinit var spinnerPais: Spinner
    private lateinit var btnContinuar: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarEtapaCadastro(atual = 1)
        inicializarViews(view)
        configurarTelefone()
        configurarPais()

        btnContinuar.setOnClickListener { validarFormulario() }
    }

    private fun inicializarViews(view: View) {
        edtEmail = view.findViewById(R.id.edtEmail)
        edtTelefone = view.findViewById(R.id.edtTelefone)
        layoutEmail = view.findViewById(R.id.layoutEmail)
        layoutTelefone = view.findViewById(R.id.layoutTelefone)
        txtErroEmail = view.findViewById(R.id.txtErroEmail)
        txtErroTelefone = view.findViewById(R.id.txtErroTelefone)
        spinnerPais = view.findViewById(R.id.spinnerPais)
        btnContinuar = view.findViewById(R.id.btnContinuar)

        txtErroEmail.visibility = View.GONE
        txtErroTelefone.visibility = View.GONE

        // Reabrindo essa etapa (voltou ou retomou um rascunho) — repõe o que
        // já tinha sido digitado antes.
        val usuario = viewModel.cadastro.usuario
        edtEmail.setText(usuario.email)
        edtTelefone.setText(usuario.telefone)
    }

    private fun configurarTelefone() {
        edtTelefone.addTextChangedListener(MascaraUtil.inserir("(##) #####-####", edtTelefone))
    }

    /** Busca a lista de países + código de discagem numa API pública (REST
     * Countries) em vez de uma lista fixa. Começa com Brasil sozinho (não
     * dá pra deixar o spinner vazio enquanto a API não responde) e troca
     * pela lista completa quando ela chegar; sem internet, fica só com
     * Brasil mesmo. */
    private fun configurarPais() {
        configurarSpinnerPaises(listOf(PaisTelefone("Brasil", "+55")))

        viewLifecycleOwner.lifecycleScope.launch {
            val paises = try {
                paisTelefoneClient.listarPaises()
            } catch (erro: Exception) {
                null
            }

            if (!paises.isNullOrEmpty()) {
                configurarSpinnerPaises(paises)
            }
        }
    }

    private fun configurarSpinnerPaises(paises: List<PaisTelefone>) {
        val indiceBrasil = paises.indexOfFirst { it.codigo == "+55" }.takeIf { it >= 0 } ?: 0
        val rotulos = paises.map { "${it.nome} (${it.codigo})" }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, rotulos)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPais.adapter = adapter
        spinnerPais.setSelection(indiceBrasil)
    }

    private fun validarFormulario() {
        val email = edtEmail.text?.toString()?.trim() ?: ""
        val telefone = edtTelefone.text?.toString()?.trim() ?: ""

        var valido = true

        if (!CadastroValidator.emailValido(email)) {
            mostrarErro(txtErroEmail, layoutEmail, "*Digite um email válido")
            valido = false
        } else {
            limparErro(txtErroEmail, layoutEmail)
        }

        if (!CadastroValidator.telefoneValido(telefone)) {
            mostrarErro(txtErroTelefone, layoutTelefone, "*Telefone inválido")
            valido = false
        } else {
            limparErro(txtErroTelefone, layoutTelefone)
        }

        if (valido) {
            verificarEmail(email, telefone)
        }
    }

    private fun verificarEmail(email: String, telefone: String) {
        btnContinuar.isEnabled = false

        authRepository.verificarEmail(
            email = email,
            onResult = { existe ->
                btnContinuar.isEnabled = true

                if (existe) {
                    mostrarErro(txtErroEmail, layoutEmail, "*Email já cadastrado no sistema")
                    return@verificarEmail
                }

                viewModel.definirIdentificacao(email = email, telefone = telefone)
                rascunhoRepository.salvar(viewModel.cadastro, CadastroActivity.ETAPA_CREDENCIAIS_SENHA)
                (requireActivity() as CadastroActivity).abrirCredenciaisSenha()
            },
            onError = {
                btnContinuar.isEnabled = true
                mostrarErro(txtErroEmail, layoutEmail, "*Erro ao verificar e-mail. Tente novamente.")
            }
        )
    }

    private fun mostrarErro(mensagem: TextView, layout: TextInputLayout, texto: String) {
        mensagem.text = texto
        mensagem.visibility = View.VISIBLE
        layout.setBackgroundResource(R.drawable.bg_input_white_red)
    }

    private fun limparErro(mensagem: TextView, layout: TextInputLayout) {
        mensagem.visibility = View.GONE
        layout.setBackgroundResource(R.drawable.bg_input_white)
    }
}
