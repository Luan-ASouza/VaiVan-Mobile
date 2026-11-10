package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vaivan.R
import com.example.vaivan.core.util.MascaraUtil
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.CadastroRascunhoRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import android.widget.TextView

class InformacoesPessoaisFragment :
    Fragment(R.layout.fragment_informacoes_pessoais) {

    private val viewModel: CadastroViewModel by activityViewModels()
    private val usuarioRepository by lazy {
        UsuarioRepository(VaivanDatabase.getInstance(requireContext()).usuarioDao())
    }
    private val rascunhoRepository by lazy { CadastroRascunhoRepository(requireContext()) }

    private lateinit var edtNome: TextInputEditText
    private lateinit var edtSobrenome: TextInputEditText
    private lateinit var edtCpf: TextInputEditText

    private lateinit var layoutNome: TextInputLayout
    private lateinit var layoutSobrenome: TextInputLayout
    private lateinit var layoutCpf: TextInputLayout

    private lateinit var txtErroCpf: TextView
    private lateinit var txtErroNome: TextView
    private lateinit var txtErroSobrenome: TextView

    private lateinit var btnContinuar: MaterialButton

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        configurarEtapaCadastro(atual = 4)
        configurarViews(view)
        configurarNome()
        configurarCpf()
        configurarErros()
        preencherCamposSalvos()
        configurarBotao()
    }

    /** Reabrindo essa etapa (voltou ou retomou um rascunho) — repõe o que
     * já tinha sido digitado antes. */
    private fun preencherCamposSalvos() {

        val usuario = viewModel.cadastro.usuario

        edtNome.setText(usuario.nome)
        edtSobrenome.setText(usuario.sobrenome)
        edtCpf.setText(usuario.cpf)
    }

    private fun configurarViews(
        view: View
    ) {

        edtNome =
            view.findViewById(R.id.edtNome)

        edtSobrenome =
            view.findViewById(R.id.edtSobrenome)

        edtCpf =
            view.findViewById(R.id.edtCpf)

        layoutNome =
            view.findViewById(R.id.layoutNome)

        layoutSobrenome =
            view.findViewById(R.id.layoutSobrenome)

        layoutCpf =
            view.findViewById(R.id.layoutCpf)

        txtErroCpf =
            view.findViewById(R.id.txtErroCpf)

        txtErroNome =
            view.findViewById(R.id.txtErroNome)

        txtErroSobrenome =
            view.findViewById(R.id.txtErroSobrenome)

        btnContinuar =
            view.findViewById(R.id.btnContinuar)
    }

    private fun configurarNome() {

        val filtroNome =
            InputFilter { source, _, _, _, _, _ ->

                if (source.isNullOrEmpty()) {
                    return@InputFilter null
                }

                val permitido =
                    source.toString().all { caractere ->

                        caractere.isLetter() ||
                                caractere == ' ' ||
                                caractere == '-' ||
                                caractere == '\''
                    }

                if (permitido) {
                    null
                } else {
                    ""
                }
            }

        edtNome.filters = arrayOf(filtroNome)
        edtSobrenome.filters = arrayOf(filtroNome)
    }

    private fun configurarCpf() {

        edtCpf.addTextChangedListener(
            MascaraUtil.inserir(
                "###.###.###-##",
                edtCpf
            )
        )
    }

    private fun configurarErros() {

        txtErroCpf.visibility =
            View.GONE

        txtErroSobrenome.visibility =
            View.GONE

        txtErroNome.visibility =
            View.GONE
    }

    private fun configurarBotao() {

        btnContinuar.setOnClickListener {
            validarFormulario()
        }
    }

    private fun validarFormulario() {

        val nome =
            obterTexto(edtNome)

        val sobrenome =
            obterTexto(edtSobrenome)

        val cpf =
            obterTexto(edtCpf)

        var valido = true

        if (
            !CadastroValidator.nomeValido(nome)
        ) {

            mostrarErro(
                layout = layoutNome,
                mensagem = txtErroNome
            )

            valido = false

        } else {

            limparErro(
                layout = layoutNome,
                mensagem = txtErroNome
            )
        }

        if (
            !CadastroValidator.nomeValido(sobrenome)
        ) {

            mostrarErro(
                layout = layoutSobrenome,
                mensagem = txtErroSobrenome
            )

            valido = false

        } else {

            limparErro(
                layout = layoutSobrenome,
                mensagem = txtErroSobrenome
            )
        }

        if (
            !CadastroValidator.cpfValido(cpf)
        ) {

            txtErroCpf.text = "*Digite um CPF válido."

            mostrarErro(
                layout = layoutCpf,
                mensagem = txtErroCpf
            )

            valido = false

        } else {

            limparErro(
                layout = layoutCpf,
                mensagem = txtErroCpf
            )
        }

        if (valido) {

            confirmarCpfDisponivel(
                nome = nome,
                sobrenome = sobrenome,
                cpf = cpf
            )
        }
    }

    /** Checagem de duplicidade do CPF — direto no Firestore, antes de
     * avançar (não precisa de regra nova: `usuarios` já permite leitura pra
     * qualquer usuário logado). */
    private fun confirmarCpfDisponivel(
        nome: String,
        sobrenome: String,
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
                cpf = cpf
            )

            rascunhoRepository.salvar(viewModel.cadastro, CadastroActivity.ETAPA_DATA_NASCIMENTO)

            (requireActivity() as CadastroActivity).abrirDataNascimento()
        }
    }

    private fun mostrarErro(
        layout: TextInputLayout,
        mensagem: TextView
    ) {

        mensagem.visibility =
            View.VISIBLE

        layout.setBackgroundResource(
            R.drawable.bg_input_white_red
        )
    }

    private fun limparErro(
        layout: TextInputLayout,
        mensagem: TextView
    ) {

        mensagem.visibility =
            View.GONE

        layout.setBackgroundResource(
            R.drawable.bg_input_white
        )
    }

    private fun obterTexto(
        editText: TextInputEditText
    ): String {

        return editText.text
            ?.toString()
            ?.trim()
            ?: ""
    }
}
