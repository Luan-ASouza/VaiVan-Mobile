package com.example.vaivan.ui.ajuda

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.vaivan.R
import com.example.vaivan.ui.comum.guia.GuiaPrimeiroAcesso

/** Converte dp em pixels (usado nas duas telas de ajuda). */
internal fun Context.dp(valor: Int): Int =
    (valor * resources.displayMetrics.density).toInt()

/**
 * Tela de Ajuda: botão para falar no WhatsApp e lista de guias passo a passo.
 *
 * Extra opcional: [EXTRA_PERFIL] ("RESPONSAVEL" ou "MOTORISTA") filtra os guias.
 * Sem o extra (tela de login), mostra todos os guias.
 */
class AjudaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PERFIL = "perfil"
    }

    private var perfil: PerfilAjuda? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        perfil = intent.getStringExtra(EXTRA_PERFIL)?.let { nome ->
            runCatching { PerfilAjuda.valueOf(nome) }.getOrNull()
        }

        setContentView(montarTela())
    }

    private fun montarTela(): View {

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Respeita a barra de status e a de navegação
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        raiz.addView(
            TextView(this).apply {
                text = "← Voltar"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
                setPadding(dp(20), dp(16), dp(20), dp(8))
                setOnClickListener { finish() }
            }
        )

        val conteudo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(24))
        }

        conteudo.addView(
            TextView(this).apply {
                text = "Ajuda"
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
                setPadding(0, 0, 0, dp(16))
            }
        )

        conteudo.addView(criarCartaoWhatsApp())

        // Só dentro do app (com perfil): na tela de login não há tela inicial para guiar
        perfil?.let { conteudo.addView(criarCartaoRevisarTour(it)) }

        if (perfil == null) {
            // Tela de login: mostra tudo, separado por tipo de conta
            val todos = AjudaConteudo.guias

            adicionarSecao(conteudo, "Para responsáveis", todos.filter { it.perfis == setOf(PerfilAjuda.RESPONSAVEL) })
            adicionarSecao(conteudo, "Para motoristas", todos.filter { it.perfis == setOf(PerfilAjuda.MOTORISTA) })
            adicionarSecao(conteudo, "Para todos", todos.filter { it.perfis.size == 2 })
        } else {
            adicionarSecao(conteudo, "Guias passo a passo", AjudaConteudo.paraPerfil(perfil))
        }

        raiz.addView(
            ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                addView(conteudo)
            }
        )

        return raiz
    }

    // ---------------------------------------------------------
    // WHATSAPP
    // ---------------------------------------------------------

    private fun criarCartaoWhatsApp(): View {

        val cartao = criarCartao()

        cartao.addView(
            TextView(this).apply {
                text = "Precisa de ajuda?"
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
            }
        )

        cartao.addView(
            TextView(this).apply {
                text = "Fale com a nossa equipe pelo WhatsApp."
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
                setPadding(0, dp(4), 0, dp(12))
            }
        )

        cartao.addView(
            AppCompatButton(this).apply {
                text = "Chamar no WhatsApp"
                isAllCaps = false
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_button_orange)
                setOnClickListener { WhatsAppHelper.abrir(this@AjudaActivity, perfil) }
            }
        )

        return cartao
    }

    // ---------------------------------------------------------
    // TOUR DO APP (guia de primeiro acesso)
    // ---------------------------------------------------------

    /** Volta para a tela inicial e mostra de novo os destaques dos botões principais. */
    private fun criarCartaoRevisarTour(perfil: PerfilAjuda): View {

        val cartao = criarCartao().apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                GuiaPrimeiroAcesso.mostrarDeNovo(this@AjudaActivity, perfil)
                finish() // a tela inicial abre o guia ao voltar (onResume)
            }
        }

        cartao.addView(
            TextView(this).apply {
                text = "Rever o tour do app"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
            }
        )

        cartao.addView(
            TextView(this).apply {
                text = "Mostra de novo, na tela inicial, para que serve cada botão principal."
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
                setPadding(0, dp(4), 0, 0)
            }
        )

        return cartao
    }

    // ---------------------------------------------------------
    // GUIAS
    // ---------------------------------------------------------

    private fun adicionarSecao(
        conteudo: LinearLayout,
        titulo: String,
        guias: List<GuiaAjuda>
    ) {
        if (guias.isEmpty()) return

        conteudo.addView(
            TextView(this).apply {
                text = titulo
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
                setPadding(0, dp(12), 0, dp(8))
            }
        )

        guias.forEach { guia ->
            conteudo.addView(criarCartaoDoGuia(guia))
        }
    }

    private fun criarCartaoDoGuia(guia: GuiaAjuda): View {

        val cartao = criarCartao().apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(
                    Intent(this@AjudaActivity, GuiaAjudaActivity::class.java).apply {
                        putExtra(GuiaAjudaActivity.EXTRA_GUIA_ID, guia.id)
                        putExtra(EXTRA_PERFIL, perfil?.name)
                    }
                )
            }
        }

        cartao.addView(
            TextView(this).apply {
                text = guia.titulo
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
            }
        )

        cartao.addView(
            TextView(this).apply {
                text = guia.resumo
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
                setPadding(0, dp(4), 0, 0)
            }
        )

        return cartao
    }

    private fun criarCartao(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_beige_round_20)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
}