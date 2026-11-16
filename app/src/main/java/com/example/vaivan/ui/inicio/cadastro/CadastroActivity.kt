package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.WindowInsetsUtil
import com.google.firebase.auth.FirebaseAuth

/**
 * Assistente de cadastro. O progresso só vive em memória (CadastroViewModel):
 * fechar o app no meio descarta o que foi digitado. Quem já criou a conta
 * retoma assim:
 * - e-mail ainda não confirmado: começa o cadastro de novo com o mesmo e-mail
 *   e a mesma senha (ver CredenciaisSenhaFragment), ou entra pelo login;
 * - e-mail confirmado mas perfil incompleto: entra pelo login, que abre esta
 *   tela com [EXTRA_JA_AUTENTICADO].
 */
class CadastroActivity : BaseActivity() {

    companion object {
        /** Usuário já autenticado (veio do login) mas sem perfil em
         * `usuarios/{uid}` — pula identificação/senha e abre na verificação
         * de e-mail (se ainda não confirmado) ou nas informações pessoais. */
        const val EXTRA_JA_AUTENTICADO = "ja_autenticado"
    }

    private val viewModel: CadastroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_cadastro)

        if (savedInstanceState == null) {
            val contaAtual = FirebaseAuth.getInstance().currentUser

            if (intent.getBooleanExtra(EXTRA_JA_AUTENTICADO, false) && contaAtual != null) {
                viewModel.cadastro.usuario = viewModel.cadastro.usuario.copy(
                    email = contaAtual.email.orEmpty(),
                    emailConfirmado = contaAtual.isEmailVerified
                )

                if (contaAtual.isEmailVerified) abrirInformacoesPessoais() else abrirVerificacaoEmail()
            } else {
                abrirIdentificacao()
            }
        }

        aplicarGapsDeSistema()

        // Com enableEdgeToEdge (BaseActivity), o resize automático do teclado
        // (windowSoftInputMode) para de funcionar — sem isso, o campo em foco
        // fica escondido atrás do teclado em qualquer etapa do formulário.
        WindowInsetsUtil.aplicarInsets(
            findViewById<FragmentContainerView>(R.id.containerCadastro),
            aplicarTopo = false
        )

        // Volta uma etapa por vez (cada trocarEtapa empilha uma transação).
        // Na primeira etapa não há nada pra voltar: sai do cadastro.
        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 1) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }
    }

    private fun trocarEtapa(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.containerCadastro, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun abrirIdentificacao() = trocarEtapa(IdentificacaoFragment())

    fun abrirCredenciaisSenha() = trocarEtapa(CredenciaisSenhaFragment())

    fun abrirVerificacaoEmail() = trocarEtapa(VerificacaoEmailFragment())

    fun abrirInformacoesPessoais() = trocarEtapa(InformacoesPessoaisFragment())

    fun abrirDataNascimento() = trocarEtapa(DataNascimentoFragment())

    fun abrirEndereco() = trocarEtapa(EnderecoFragment())

    fun abrirConsentimento() = trocarEtapa(ConsentimentoFragment())
}
