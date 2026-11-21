package com.example.vaivan.ui.ajuda

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity
import com.example.vaivan.ui.comum.guia.GuiaPrimeiroAcesso
import com.example.vaivan.ui.comum.tema.TemaPerfil

/** Cor da Ajuda: escura para o motorista; laranja para o responsável e na tela de login. */
internal fun PerfilAjuda?.tema(): TemaPerfil =
    if (this == PerfilAjuda.MOTORISTA) TemaPerfil.MOTORISTA else TemaPerfil.RESPONSAVEL

/**
 * Tela de Ajuda: cartão para falar no WhatsApp e lista de guias passo a passo.
 *
 * Extra opcional: [EXTRA_PERFIL] ("RESPONSAVEL" ou "MOTORISTA") filtra os guias
 * e define a cor da tela. Sem o extra (tela de login), mostra todos os guias.
 */
class AjudaActivity : BaseActivity() {

    companion object {
        const val EXTRA_PERFIL = "perfil"
    }

    private var perfil: PerfilAjuda? = null
    private lateinit var tema: TemaPerfil
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        perfil = intent.getStringExtra(EXTRA_PERFIL)?.let { nome ->
            runCatching { PerfilAjuda.valueOf(nome) }.getOrNull()
        }
        tema = perfil.tema()

        setContentView(R.layout.activity_ajuda)
        aplicarGapsDeSistema()

        findViewById<View>(R.id.btnVoltarAjuda).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.imgOndaAjuda).setImageResource(tema.onda)
        findViewById<View>(R.id.cartaoWhatsApp).setBackgroundResource(tema.fundoDestaque)
        findViewById<View>(R.id.btnChamarWhatsApp).setOnClickListener {
            WhatsAppHelper.abrir(this, perfil)
        }

        container = findViewById(R.id.containerAjuda)

        // Só dentro do app (com perfil): na tela de login não há tela inicial para guiar
        perfil?.let { adicionarCartaoRevisarTour(it) }

        val perfilAtual = perfil
        if (perfilAtual == null) {
            // Tela de login: mostra tudo, separado por tipo de conta
            val todos = AjudaConteudo.guias

            adicionarSecao("Para responsáveis", todos.filter { it.perfis == setOf(PerfilAjuda.RESPONSAVEL) })
            adicionarSecao("Para motoristas", todos.filter { it.perfis == setOf(PerfilAjuda.MOTORISTA) })
            adicionarSecao("Para todos", todos.filter { it.perfis.size == 2 })
        } else {
            adicionarSecao("Guias passo a passo", AjudaConteudo.paraPerfil(perfilAtual))
        }
    }

    // ---------------------------------------------------------
    // TOUR DO APP (guia de primeiro acesso)
    // ---------------------------------------------------------

    /** Volta para a tela inicial e mostra de novo os destaques dos botões principais. */
    private fun adicionarCartaoRevisarTour(perfil: PerfilAjuda) {
        adicionarCartao(
            titulo = "Rever o tour do app",
            resumo = "Mostra de novo, na tela inicial, para que serve cada botão principal.",
            icone = R.drawable.ic_casa
        ) {
            GuiaPrimeiroAcesso.mostrarDeNovo(this, perfil)
            finish() // a tela inicial abre o guia ao voltar (onResume)
        }
    }

    // ---------------------------------------------------------
    // GUIAS
    // ---------------------------------------------------------

    private fun adicionarSecao(titulo: String, guias: List<GuiaAjuda>) {
        if (guias.isEmpty()) return

        val rotulo = layoutInflater.inflate(R.layout.item_titulo_secao, container, false) as TextView
        rotulo.text = titulo
        container.addView(rotulo)

        guias.forEach { guia ->
            adicionarCartao(guia.titulo, guia.resumo, R.drawable.ic_ajuda) {
                startActivity(
                    Intent(this, GuiaAjudaActivity::class.java).apply {
                        putExtra(GuiaAjudaActivity.EXTRA_GUIA_ID, guia.id)
                        putExtra(EXTRA_PERFIL, perfil?.name)
                    }
                )
            }
        }
    }

    private fun adicionarCartao(titulo: String, resumo: String, icone: Int, aoTocar: () -> Unit) {
        val cartao = layoutInflater.inflate(R.layout.item_ajuda, container, false)

        cartao.findViewById<TextView>(R.id.txtTituloAjuda).text = titulo
        cartao.findViewById<TextView>(R.id.txtResumoAjuda).text = resumo
        cartao.findViewById<ImageView>(R.id.imgIconeAjuda).apply {
            setImageResource(icone)
            setBackgroundResource(tema.fundoIcone)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tema.destaque))
        }
        cartao.setOnClickListener { aoTocar() }

        container.addView(cartao)
    }
}
