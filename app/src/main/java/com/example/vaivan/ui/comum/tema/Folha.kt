package com.example.vaivan.ui.comum.tema

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import com.example.vaivan.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

/**
 * Bottom sheet no padrão do app (fundo bg_sheet_rounded + alça no topo, como
 * bottom_sheet_confirmar_exclusao): infla [layout], deixa o contêiner do
 * Material transparente (para os cantos arredondados aparecerem) e abre já
 * expandido. Quem chama preenche a View e chama `show()`.
 */
object Folha {

    fun criar(context: Context, @LayoutRes layout: Int): Pair<BottomSheetDialog, View> {
        val sheet = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(layout, null)
        sheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        return sheet to view
    }
}

/** Marca [selecionada] entre [opcoes] (pílulas com o estilo OpcaoFormaPagamento
 * ou abas): a marcada ganha a cor do perfil, as outras voltam a bg_aba. */
fun marcarOpcao(opcoes: List<View>, selecionada: View, tema: TemaPerfil) {
    opcoes.forEach { opcao ->
        val marcada = opcao === selecionada
        opcao.setBackgroundResource(if (marcada) tema.abaSelecionada else R.drawable.bg_aba)
        (opcao as? android.widget.TextView)?.setTextColor(
            ContextCompat.getColor(opcao.context, if (marcada) R.color.text_on_dark else R.color.text_primary)
        )
    }
}

/** Botão principal de um sheet na cor do perfil (laranja ou escuro). */
fun MaterialButton.pintarPrincipal(tema: TemaPerfil) {
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tema.destaque))
    setTextColor(ContextCompat.getColor(context, R.color.text_on_dark))
}
