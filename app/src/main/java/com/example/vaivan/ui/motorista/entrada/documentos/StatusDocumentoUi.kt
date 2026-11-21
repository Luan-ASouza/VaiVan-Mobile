package com.example.vaivan.ui.motorista.entrada.documentos

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.vaivan.R
import com.example.vaivan.data.local.entities.DocumentoEntity
import com.example.vaivan.data.models.StatusDocumento
import com.example.vaivan.ui.comum.tema.TomSelo
import com.example.vaivan.ui.comum.tema.aplicarSelo

/**
 * Pinta um card de documento (ícone no quadrinho + selo + motivo da recusa),
 * igual em "Meus documentos" e nos documentos do veículo.
 *
 * [rotuloNaoEnviado]: texto do selo quando ainda não há envio (null = sem selo).
 */
fun pintarStatusDocumento(
    icone: ImageView,
    selo: TextView,
    motivo: TextView,
    documento: DocumentoEntity?,
    rotuloNaoEnviado: String? = null
): StatusDocumento {
    val status = documento?.let { StatusDocumento.fromString(it.statusValidacao) }
        ?: StatusDocumento.NAO_ENVIADO
    val contexto = icone.context

    // O motivo só aparece quando o documento foi rejeitado
    val motivoRejeicao = documento?.motivoRejeicao
    if (status == StatusDocumento.REJEITADO && !motivoRejeicao.isNullOrBlank()) {
        motivo.text = "Motivo: $motivoRejeicao"
        motivo.visibility = View.VISIBLE
    } else {
        motivo.visibility = View.GONE
    }

    val (iconeRes, rotulo, tom) = when (status) {
        StatusDocumento.NAO_ENVIADO, StatusDocumento.PENDENTE ->
            Triple(R.drawable.ic_enviar_arquivo, rotuloNaoEnviado, if (rotuloNaoEnviado != null) TomSelo.NEUTRO else null)
        StatusDocumento.EM_ANALISE -> Triple(R.drawable.ic_analise, "Em análise", TomSelo.ALERTA)
        StatusDocumento.APROVADO -> Triple(R.drawable.ic_aprovado, "Aprovado", TomSelo.SUCESSO)
        StatusDocumento.REJEITADO -> Triple(R.drawable.ic_rejeitado, "Rejeitado · reenviar documento", TomSelo.PERIGO)
    }

    // O quadrinho e o ícone tomam a cor do status (ainda não enviado = escuro neutro)
    val tomIcone = if (status == StatusDocumento.NAO_ENVIADO || status == StatusDocumento.PENDENTE) null else tom
    icone.setImageResource(iconeRes)
    icone.backgroundTintList = tomIcone?.let { ColorStateList.valueOf(ContextCompat.getColor(contexto, it.fundo)) }
    icone.imageTintList = ColorStateList.valueOf(
        ContextCompat.getColor(contexto, tomIcone?.texto ?: R.color.rota_destaque)
    )

    if (rotulo == null || tom == null) {
        selo.visibility = View.GONE
    } else {
        selo.aplicarSelo(rotulo, tom)
        selo.visibility = View.VISIBLE
    }

    return status
}
