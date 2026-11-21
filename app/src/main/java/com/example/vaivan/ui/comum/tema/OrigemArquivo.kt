package com.example.vaivan.ui.comum.tema

import android.content.Context
import android.view.View
import android.widget.TextView
import com.example.vaivan.R

/** De onde vem o arquivo de um documento enviado pelo motorista. */
enum class OrigemArquivo { CAMERA, GALERIA, PDF }

/**
 * Sheet "Tirar foto / Selecionar da galeria / Selecionar PDF" das telas de
 * envio de documento (CNH, comprovante, documentos do veículo). Só as
 * [origens] passadas aparecem (ex.: a CNH física não aceita PDF).
 */
fun escolherOrigemArquivo(
    context: Context,
    titulo: String,
    origens: List<OrigemArquivo> = OrigemArquivo.entries,
    aoEscolher: (OrigemArquivo) -> Unit
) {
    val (sheet, view) = Folha.criar(context, R.layout.bottom_sheet_origem_arquivo)

    view.findViewById<TextView>(R.id.txtTituloOrigemArquivo).text = titulo

    mapOf(
        OrigemArquivo.CAMERA to R.id.opcaoOrigemCamera,
        OrigemArquivo.GALERIA to R.id.opcaoOrigemGaleria,
        OrigemArquivo.PDF to R.id.opcaoOrigemPdf
    ).forEach { (origem, id) ->
        view.findViewById<View>(id).apply {
            visibility = if (origem in origens) View.VISIBLE else View.GONE
            setOnClickListener {
                sheet.dismiss()
                aoEscolher(origem)
            }
        }
    }

    view.findViewById<View>(R.id.btnCancelarOrigemArquivo).setOnClickListener { sheet.dismiss() }

    sheet.show()
}
