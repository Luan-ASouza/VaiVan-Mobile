package com.example.vaivan.ui.comum.chat

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.vaivan.R

/** Selo "Motorista" (laranja) ou "Responsável" (rosa) ao lado do nome do contato. */
fun TextView.aplicarSeloContato(ehMotorista: Boolean) {
    text = if (ehMotorista) "Motorista" else "Responsável"
    setBackgroundResource(if (ehMotorista) R.drawable.bg_selo_motorista else R.drawable.bg_selo_responsavel)
    setTextColor(
        ContextCompat.getColor(
            context,
            if (ehMotorista) R.color.chat_selo_motorista_texto else R.color.chat_selo_responsavel_texto
        )
    )
    visibility = View.VISIBLE
}
