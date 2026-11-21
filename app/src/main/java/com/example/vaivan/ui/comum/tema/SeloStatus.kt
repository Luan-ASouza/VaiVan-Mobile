package com.example.vaivan.ui.comum.tema

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.example.vaivan.R

/**
 * Tom do selo de status (pílula com fundo claro e texto forte, como o selo
 * da viagem nos transportes). A pílula usa bg_pilula_branca no layout.
 */
enum class TomSelo(@ColorRes val fundo: Int, @ColorRes val texto: Int) {
    SUCESSO(R.color.status_success_fundo, R.color.status_success),
    ALERTA(R.color.status_warning_fundo, R.color.status_warning_texto),
    PERIGO(R.color.status_danger_fundo, R.color.status_danger),
    NEUTRO(R.color.status_neutro_fundo, R.color.status_neutro_texto)
}

/** Pinta este TextView como selo no [tom] (fundo via backgroundTint). */
fun TextView.aplicarSelo(texto: String, tom: TomSelo) {
    text = texto
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tom.fundo))
    setTextColor(ContextCompat.getColor(context, tom.texto))
}
