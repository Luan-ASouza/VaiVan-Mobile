package com.example.vaivan.ui.comum.perfil

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.example.vaivan.R

/**
 * Uma linha do item_perfil_campo (rótulo à esquerda, valor + seta à direita),
 * incluída com `<include android:id="@+id/[idInclude]">`. Usada no perfil do
 * usuário (PerfilCampos) e no perfil do passageiro.
 */
class LinhaCampoPerfil(raiz: View, idInclude: Int, rotulo: String, editavel: Boolean = true) {

    private val linha: View = raiz.findViewById(idInclude)
    private val txtValor: TextView = linha.findViewById(R.id.txtValorCampo)
    private val txtStatus: TextView = linha.findViewById(R.id.txtStatusCampo)

    init {
        linha.findViewById<TextView>(R.id.txtRotuloCampo).text = rotulo
        if (!editavel) {
            linha.findViewById<ImageView>(R.id.imgSetaCampo).visibility = View.INVISIBLE
            linha.isClickable = false
            linha.isFocusable = false
            linha.background = null
        }
    }

    fun aoTocar(acao: () -> Unit) = linha.setOnClickListener { acao() }

    fun valor(texto: String) {
        txtValor.text = texto
    }

    fun status(texto: String, @ColorRes corRes: Int) {
        txtStatus.text = texto
        txtStatus.setTextColor(ContextCompat.getColor(txtStatus.context, corRes))
        txtStatus.visibility = View.VISIBLE
    }
}
