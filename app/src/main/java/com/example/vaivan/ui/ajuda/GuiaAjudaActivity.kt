package com.example.vaivan.ui.ajuda

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
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

/**
 * Mostra um guia da base de conhecimento: título, passos numerados,
 * dica e um botão para falar no WhatsApp se a dúvida continuar.
 */
class GuiaAjudaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GUIA_ID = "guiaId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val guia = intent.getStringExtra(EXTRA_GUIA_ID)?.let { AjudaConteudo.porId(it) }

        if (guia == null) {
            finish()
            return
        }

        val perfil = intent.getStringExtra(AjudaActivity.EXTRA_PERFIL)?.let { nome ->
            runCatching { PerfilAjuda.valueOf(nome) }.getOrNull()
        }

        setContentView(montarTela(guia, perfil))
    }

    private fun montarTela(guia: GuiaAjuda, perfil: PerfilAjuda?): View {

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

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

        // Título e resumo
        conteudo.addView(
            TextView(this).apply {
                text = guia.titulo
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
            }
        )

        conteudo.addView(
            TextView(this).apply {
                text = guia.resumo
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
                setPadding(0, dp(6), 0, dp(20))
            }
        )

        // Passos
        guia.passos.forEachIndexed { indice, passo ->
            conteudo.addView(criarPasso(indice + 1, passo))
        }

        // Dica
        guia.dica?.let { dica ->
            val cartao = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_beige_round_20)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }

            cartao.addView(
                TextView(this).apply {
                    text = "Dica"
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.orange))
                }
            )

            cartao.addView(
                TextView(this).apply {
                    text = dica
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
                    setPadding(0, dp(4), 0, 0)
                }
            )

            conteudo.addView(cartao)
        }

        // WhatsApp
        conteudo.addView(
            AppCompatButton(this).apply {
                text = "Ainda com dúvida? Falar no WhatsApp"
                isAllCaps = false
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_button_orange)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(24) }
                setOnClickListener {
                    WhatsAppHelper.abrir(this@GuiaAjudaActivity, perfil, guia.titulo)
                }
            }
        )

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

    private fun criarPasso(numero: Int, texto: String): View {

        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }

        linha.addView(
            TextView(this).apply {
                text = numero.toString()
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_button_orange)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginEnd = dp(12)
                }
            }
        )

        linha.addView(
            TextView(this).apply {
                text = texto
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )

        return linha
    }
}