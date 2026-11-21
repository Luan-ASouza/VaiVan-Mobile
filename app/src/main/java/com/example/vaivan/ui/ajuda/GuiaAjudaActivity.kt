package com.example.vaivan.ui.ajuda

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.vaivan.R
import com.example.vaivan.core.base.BaseActivity

/**
 * Mostra um guia da base de conhecimento: título, passos numerados,
 * dica e um botão para falar no WhatsApp se a dúvida continuar.
 * A cor segue o perfil recebido em [AjudaActivity.EXTRA_PERFIL].
 */
class GuiaAjudaActivity : BaseActivity() {

    companion object {
        const val EXTRA_GUIA_ID = "guiaId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val guia = intent.getStringExtra(EXTRA_GUIA_ID)?.let { AjudaConteudo.porId(it) }

        if (guia == null) {
            finish()
            return
        }

        val perfil = intent.getStringExtra(AjudaActivity.EXTRA_PERFIL)?.let { nome ->
            runCatching { PerfilAjuda.valueOf(nome) }.getOrNull()
        }
        val tema = perfil.tema()
        val corDestaque = ContextCompat.getColor(this, tema.destaque)

        setContentView(R.layout.activity_guia_ajuda)
        aplicarGapsDeSistema()

        findViewById<View>(R.id.btnVoltarGuia).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.imgOndaGuia).setImageResource(tema.onda)

        // Título e resumo
        findViewById<TextView>(R.id.txtTituloGuia).text = guia.titulo
        findViewById<TextView>(R.id.txtResumoGuia).text = guia.resumo

        // Passos
        val containerPassos = findViewById<LinearLayout>(R.id.containerPassos)
        guia.passos.forEachIndexed { indice, passo ->
            val linha = layoutInflater.inflate(R.layout.item_passo_guia, containerPassos, false)
            linha.findViewById<TextView>(R.id.txtNumeroPasso).apply {
                text = (indice + 1).toString()
                backgroundTintList = ColorStateList.valueOf(corDestaque)
            }
            linha.findViewById<TextView>(R.id.txtTextoPasso).text = passo
            containerPassos.addView(linha)
        }

        // Dica
        guia.dica?.let { dica ->
            findViewById<View>(R.id.blocoDicaGuia).apply {
                setBackgroundResource(tema.fundoIcone)
                visibility = View.VISIBLE
            }
            findViewById<TextView>(R.id.txtRotuloDicaGuia)
                .setTextColor(ContextCompat.getColor(this, tema.textoDestaque))
            findViewById<TextView>(R.id.txtDicaGuia).text = dica
        }

        // WhatsApp
        findViewById<View>(R.id.btnWhatsAppGuia).apply {
            setBackgroundResource(tema.botao)
            setOnClickListener { WhatsAppHelper.abrir(this@GuiaAjudaActivity, perfil, guia.titulo) }
        }
    }
}
