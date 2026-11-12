package com.example.vaivan.ui.comum.perfil

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Troca de senha (perfil e menu lateral de motorista/responsável). */
class AlterarSenhaActivity : BaseActivity() {

    private val viewModel: AlterarSenhaViewModel by viewModels {
        viewModelFactory {
            initializer { AlterarSenhaViewModel(FirebaseAuthRepository()) }
        }
    }

    private class CampoSenha(
        val layout: TextInputLayout,
        val edt: TextInputEditText,
        val erro: TextView
    ) {
        val texto get() = edt.text?.toString().orEmpty()

        fun mostrarErro(mensagem: String?) {
            erro.text = mensagem
            erro.visibility = if (mensagem == null) View.GONE else View.VISIBLE
            layout.setBackgroundResource(
                if (mensagem == null) R.drawable.bg_input_white else R.drawable.bg_input_white_red
            )
        }
    }

    private lateinit var campos: Map<AlterarSenhaViewModel.Campo, CampoSenha>
    private lateinit var txtErroGeral: TextView
    private lateinit var btnSalvar: MaterialButton
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alterar_senha)
        aplicarGapsDeSistema(aplicarBase = false)
        WindowInsetsUtil.aplicarInsets(findViewById(R.id.conteudoEdicaoPerfil), aplicarTopo = false)

        campos = mapOf(
            AlterarSenhaViewModel.Campo.ATUAL to CampoSenha(
                findViewById(R.id.layoutSenhaAtual), findViewById(R.id.edtSenhaAtual), findViewById(R.id.txtErroSenhaAtual)
            ),
            AlterarSenhaViewModel.Campo.NOVA to CampoSenha(
                findViewById(R.id.layoutNovaSenha), findViewById(R.id.edtNovaSenha), findViewById(R.id.txtErroNovaSenha)
            ),
            AlterarSenhaViewModel.Campo.CONFIRMACAO to CampoSenha(
                findViewById(R.id.layoutConfirmarSenha), findViewById(R.id.edtConfirmarSenha), findViewById(R.id.txtErroConfirmarSenha)
            )
        )
        txtErroGeral = findViewById(R.id.txtErroGeralSenha)
        btnSalvar = findViewById(R.id.btnSalvarEdicaoPerfil)
        progress = findViewById(R.id.progressEdicaoPerfil)

        findViewById<ImageButton>(R.id.btnVoltarEdicaoPerfil).setOnClickListener { finish() }

        campos.values.forEach { campo ->
            campo.edt.doAfterTextChanged {
                viewModel.limparErro()
                atualizarBotao()
            }
        }

        campos.getValue(AlterarSenhaViewModel.Campo.CONFIRMACAO).edt
            .setOnEditorActionListener { _, acao, _ ->
                if (acao == EditorInfo.IME_ACTION_DONE && btnSalvar.isEnabled) {
                    salvar()
                    true
                } else {
                    false
                }
            }

        btnSalvar.setOnClickListener { salvar() }
        atualizarBotao()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { renderizar(it) }
            }
        }
    }

    private fun salvar() {
        viewModel.alterar(
            atual = campos.getValue(AlterarSenhaViewModel.Campo.ATUAL).texto,
            nova = campos.getValue(AlterarSenhaViewModel.Campo.NOVA).texto,
            confirmacao = campos.getValue(AlterarSenhaViewModel.Campo.CONFIRMACAO).texto
        )
    }

    private fun renderizar(estado: AlterarSenhaViewModel.Estado) {
        val salvando = estado == AlterarSenhaViewModel.Estado.Salvando
        progress.visibility = if (salvando) View.VISIBLE else View.GONE
        btnSalvar.text = if (salvando) "" else "Salvar"
        campos.values.forEach { it.edt.isEnabled = !salvando }
        atualizarBotao()

        val erro = estado as? AlterarSenhaViewModel.Estado.Erro
        campos.forEach { (chave, campo) ->
            campo.mostrarErro(erro?.mensagem?.takeIf { erro.campo == chave })
        }
        txtErroGeral.text = erro?.mensagem
        txtErroGeral.visibility =
            if (erro?.campo == AlterarSenhaViewModel.Campo.GERAL) View.VISIBLE else View.GONE

        if (estado == AlterarSenhaViewModel.Estado.Sucesso) {
            Toast.makeText(this, "Senha alterada com sucesso!", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /** Só habilita com os três campos preenchidos. */
    private fun atualizarBotao() {
        val salvando = viewModel.estado.value == AlterarSenhaViewModel.Estado.Salvando
        val preenchidos = campos.values.all { it.texto.isNotEmpty() }
        btnSalvar.isEnabled = preenchidos && !salvando
        btnSalvar.alpha = if (preenchidos || salvando) 1f else 0.5f
    }
}
