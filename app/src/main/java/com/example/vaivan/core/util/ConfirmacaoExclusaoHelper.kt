package com.example.vaivan.core.util

import android.content.Context
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.vaivan.R
import com.example.vaivan.ui.comum.tema.Folha
import com.example.vaivan.ui.comum.tema.TemaPerfil
import com.example.vaivan.ui.comum.tema.pintarPrincipal
import com.google.android.material.button.MaterialButton

/** Mesmo bottom sheet de confirmação que MinhasRotasFragment já usa pra excluir
 * rota (bottom_sheet_confirmar_exclusao.xml) — aqui só pra não duplicar o
 * inflate/wiring em cada tela nova que precisa da mesma confirmação
 * (perfil, foto, passageiro, veículo, assinatura, recebimentos).
 *
 * Sem [tema], o botão de confirmar é vermelho (ação destrutiva). Com [tema],
 * ele ganha a cor do perfil — para confirmações que não apagam nada. */
object ConfirmacaoExclusaoHelper {

    fun confirmar(
        contexto: Context,
        titulo: String,
        mensagem: String,
        textoConfirmar: String = "Sim, excluir",
        textoCancelar: String = "Cancelar",
        tema: TemaPerfil? = null,
        aoConfirmar: () -> Unit
    ) {
        val (sheet, view) = Folha.criar(contexto, R.layout.bottom_sheet_confirmar_exclusao)

        view.findViewById<TextView>(R.id.txtConfirmarTitulo).text = titulo
        view.findViewById<TextView>(R.id.txtConfirmarMensagem).text = mensagem

        val btnConfirmar = view.findViewById<MaterialButton>(R.id.btnConfirmarExcluir)
        btnConfirmar.text = textoConfirmar
        tema?.let { btnConfirmar.pintarPrincipal(it) }

        view.findViewById<MaterialButton>(R.id.btnCancelarExcluir).apply {
            text = textoCancelar
            backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(contexto, R.color.background_screen))
            setTextColor(ContextCompat.getColor(contexto, R.color.text_primary))
            setOnClickListener { sheet.dismiss() }
        }

        btnConfirmar.setOnClickListener {
            sheet.dismiss()
            aoConfirmar()
        }

        sheet.show()
    }
}
