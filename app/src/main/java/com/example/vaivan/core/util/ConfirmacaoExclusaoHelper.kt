package com.example.vaivan.core.util

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.example.vaivan.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

/** Mesmo bottom sheet de confirmação que MinhasRotasFragment já usa pra excluir
 * rota (bottom_sheet_confirmar_exclusao.xml) — aqui só pra não duplicar o
 * inflate/wiring em cada tela nova que precisa da mesma confirmação
 * (perfil, foto, passageiro, veículo). */
object ConfirmacaoExclusaoHelper {

    fun confirmar(
        contexto: Context,
        titulo: String,
        mensagem: String,
        textoConfirmar: String = "Sim, excluir",
        aoConfirmar: () -> Unit
    ) {
        val sheet = BottomSheetDialog(contexto)
        val view = LayoutInflater.from(contexto)
            .inflate(R.layout.bottom_sheet_confirmar_exclusao, null)

        sheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        view.findViewById<TextView>(R.id.txtConfirmarTitulo).text = titulo
        view.findViewById<TextView>(R.id.txtConfirmarMensagem).text = mensagem
        view.findViewById<MaterialButton>(R.id.btnConfirmarExcluir).text = textoConfirmar

        view.findViewById<View>(R.id.btnCancelarExcluir).setOnClickListener {
            sheet.dismiss()
        }

        view.findViewById<View>(R.id.btnConfirmarExcluir).setOnClickListener {
            sheet.dismiss()
            aoConfirmar()
        }

        sheet.show()
    }
}
