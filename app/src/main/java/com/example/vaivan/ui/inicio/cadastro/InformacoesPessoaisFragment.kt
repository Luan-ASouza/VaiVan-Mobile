package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Informações pessoais: telefone (só Brasil — o +55 é fixo, exibido como
 * prefixo do campo) e CPF. Nome e sobrenome já vêm da etapa 1; só aparecem
 * aqui quando faltam (cadastro retomado pelo login, ver
 * [CadastroActivity.EXTRA_JA_AUTENTICADO]). */
class InformacoesPessoaisFragment :
    Fragment(R.layout.fragment_informacoes_pessoais) {

    private val viewModel: CadastroViewModel by activityViewModels()
    private val usuarioRepository by lazy {
        UsuarioRepository(VaivanDatabase.getInstance(requireContext()).usuarioDao())
    }

    private lateinit var grupoNomeSobrenome: View

    private lateinit var edtNome: TextInputEditText
    private lateinit var edtSobrenome: TextInputEditText
    private lateinit var edtTelefone: TextInputEditText
    private lateinit var edtCpf: TextInputEditText

    private lateinit var layoutNome: TextInputLayout
    private lateinit var layoutSobrenome: TextInputLayout
    private lateinit var layoutTelefone: TextInputLayout
    private lateinit var layoutCpf: TextInputLayout

    private lateinit var txtErroNome: TextView
    private lateinit var txtErroSobrenome: TextView
    private lateinit var txtErroTelefone: TextView
    private lateinit var txtErroCpf: TextView

    private lateinit var btnContinuar: MaterialButton

    /** Nome/sobrenome ainda não informados na etapa 1 — pede aqui. */
    private var pedirNome = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarEtapaCadastro(atual = 4)
        configurarViews(view)
        preencherCamposSalvos()

        btnContinuar.setOnClickListener { validarFormulario() }
    }

    private fun configurarViews(view: View) {
        grupoNomeSobrenome = view.findViewById(R.id.grupoNomeSobrenome)

        edtNome = view.findViewById(R.id.edtNome)
        edtSobrenome = view.findViewById(R.id.edtSobrenome)
        edtTelefone = view.findViewById(R.id.edtTelefone)
        edtCpf = view.findViewById(R.id.edtCpf)

        layoutNome = view.findViewById(R.id.layoutNome)
        layoutSobrenome = view.findViewById(R.id.layoutSobrenome)
        layoutTelefone = view.findViewById(R.id.layoutTelefone)
        layoutCpf = view.findViewById(R.id.layoutCpf)

        txtErroNome = view.findViewById(R.id.txtErroNome)
        txtErroSobrenome = view.findViewById(R.id.txtErroSobrenome)
        txtErroTelefone = view.findViewById(R.id.txtErroTelefone)
        txtErroCpf = view.findViewById(R.id.txtErroCpf)

        btnContinuar = view.findViewById(R.id.btnContinuar)

        edtNome.filters = arrayOf(FILTRO_NOME_CADASTRO)
        edtSobrenome.filters = arrayOf(FILTRO_NOME_CADASTRO)
        edtTelefone.addTextChangedListener(MascaraUtil.inserir("(##) #####-####", edtTelefone))
        edtCpf.addTextChangedListener(MascaraUtil.inserir("###.###.###-##", edtCpf))
    }

    /** Reabrindo essa etapa (o usuário voltou pra ela) — repõe o que
     * já tinha sido digitado antes. */
    private fun preencherCamposSalvos() {
        val usuario = viewModel.cadastro.usuario

        pedirNome = usuario.nome.isBlank() || usuario.sobrenome.isBlank()
        grupoNomeSobrenome.visibility = if (pedirNome) View.VISIBLE else View.GONE

        edtNome.setText(usuario.nome)
        edtSobrenome.setText(usuario.sobrenome)
        edtTelefone.setText(usuario.telefone)
        edtCpf.setText(usuario.cpf)
    }

    private fun validarFormulario() {
        val nome = obterTexto(edtNome)
        val sobrenome = obterTexto(edtSobrenome)
        val telefone = obterTexto(edtTelefone)
        val cpf = obterTexto(edtCpf)

        var valido = true

        if (pedirNome) {
            valido = validarCampo(CadastroValidator.nomeValido(nome), layoutNome, txtErroNome) && valido
            valido = validarCampo(CadastroValidator.nomeValido(sobrenome), layoutSobrenome, txtErroSobrenome) && valido
        }

        valido = validarCampo(CadastroValidator.telefoneValido(telefone), layoutTelefone, txtErroTelefone) && valido

        txtErroCpf.text = "*Digite um CPF válido."
        valido = validarCampo(CadastroValidator.cpfValido(cpf), layoutCpf, txtErroCpf) && valido

        if (valido) {
            confirmarCpfDisponivel(nome = nome, sobrenome = sobrenome, telefone = telefone, cpf = cpf)
        }
    }

    /** Mostra ou limpa o erro do campo; devolve [ok]. */
    private fun validarCampo(ok: Boolean, layout: TextInputLayout, mensagem: TextView): Boolean {
        if (ok) limparErro(layout, mensagem) else mostrarErro(layout, mensagem)
        return ok
    }

    /** Checagem de duplicidade do CPF — direto no Firestore, antes de
     * avançar (não precisa de regra nova: `usuarios` já permite leitura pra
     * qualquer usuário logado). */
    private fun confirmarCpfDisponivel(
        nome: String,
        sobrenome: String,
        telefone: String,
        cpf: String
    ) {
        btnContinuar.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val cpfJaExiste = try {
                usuarioRepository.consultarUsuarioPorCpf(cpf) != null
            } catch (erro: Exception) {
                false
            }

            btnContinuar.isEnabled = true

            if (cpfJaExiste) {
                mostrarErro(layout = layoutCpf, mensagem = txtErroCpf)
                txtErroCpf.text = "*CPF já cadastrado no sistema"
                return@launch
            }

            viewModel.definirDadosPessoais(
                nome = nome,
                sobrenome = sobrenome,
                telefone = telefone,
                cpf = cpf
            )


            (requireActivity() as CadastroActivity).abrirDataNascimento()
        }
    }

    private fun mostrarErro(layout: TextInputLayout, mensagem: TextView) {
        mensagem.visibility = View.VISIBLE
        layout.setBackgroundResource(R.drawable.bg_cadastro_input_erro)
    }

    private fun limparErro(layout: TextInputLayout, mensagem: TextView) {
        mensagem.visibility = View.GONE
        layout.setBackgroundResource(R.drawable.bg_cadastro_input)
    }

    private fun obterTexto(editText: TextInputEditText): String =
        editText.text?.toString()?.trim() ?: ""
}
