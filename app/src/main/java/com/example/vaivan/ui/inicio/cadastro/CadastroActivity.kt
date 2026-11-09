package com.example.vaivan.ui.inicio.cadastro

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.core.util.WindowInsetsUtil
import com.example.vaivan.data.repository.CadastroRascunhoRepository

class CadastroActivity : BaseActivity() {

    companion object {
        /** Usuário já autenticado mas sem rascunho local (ex.: rascunho
         * perdido, mas a conta no Firebase Auth ainda existe) — abre direto
         * na etapa de informações pessoais, pulando identificação/senha/
         * verificação de e-mail (já resolvidas antes). */
        const val EXTRA_JA_AUTENTICADO = "ja_autenticado"

        const val ETAPA_IDENTIFICACAO = CadastroRascunhoRepository.ETAPA_IDENTIFICACAO
        const val ETAPA_CREDENCIAIS_SENHA = CadastroRascunhoRepository.ETAPA_CREDENCIAIS_SENHA
        const val ETAPA_VERIFICACAO_EMAIL = CadastroRascunhoRepository.ETAPA_VERIFICACAO_EMAIL
        const val ETAPA_INFORMACOES_PESSOAIS = CadastroRascunhoRepository.ETAPA_INFORMACOES_PESSOAIS
        const val ETAPA_DATA_NASCIMENTO = CadastroRascunhoRepository.ETAPA_DATA_NASCIMENTO
        const val ETAPA_ENDERECO = CadastroRascunhoRepository.ETAPA_ENDERECO
        const val ETAPA_CONSENTIMENTO = CadastroRascunhoRepository.ETAPA_CONSENTIMENTO
    }

    private val viewModel: CadastroViewModel by viewModels()
    private val rascunhoRepository by lazy { CadastroRascunhoRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_cadastro)

        if (savedInstanceState == null) {
            val rascunho = rascunhoRepository.carregar()

            when {
                // Reconstrói a pilha inteira (não só abre a etapa atual) —
                // assim "voltar" continua funcionando passo a passo mesmo
                // depois do app ter sido fechado e reaberto.
                rascunho != null -> {
                    viewModel.cadastro = rascunho.cadastro
                    rascunho.historico.forEach { etapa -> abrirEtapa(etapa) }
                }
                intent.getBooleanExtra(EXTRA_JA_AUTENTICADO, false) -> abrirInformacoesPessoais()
                else -> abrirIdentificacao()
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
        // Na primeira etapa não há nada pra voltar: sai do cadastro mesmo
        // (o rascunho continua salvo, pra retomar depois).
        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 1) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }
    }

    private fun abrirEtapa(etapa: String) {
        when (etapa) {
            ETAPA_CREDENCIAIS_SENHA -> abrirCredenciaisSenha()
            ETAPA_VERIFICACAO_EMAIL -> abrirVerificacaoEmail()
            ETAPA_INFORMACOES_PESSOAIS -> abrirInformacoesPessoais()
            ETAPA_DATA_NASCIMENTO -> abrirDataNascimento()
            ETAPA_ENDERECO -> abrirEndereco()
            ETAPA_CONSENTIMENTO -> abrirConsentimento()
            else -> abrirIdentificacao()
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
