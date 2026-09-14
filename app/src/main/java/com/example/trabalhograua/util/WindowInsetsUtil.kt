package com.example.trabalhograua.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object WindowInsetsUtil {

    fun aplicarInsets(
        view: View,
        aplicarTopo: Boolean = true,
        aplicarInferior: Boolean = true
    ) {

        ViewCompat.setOnApplyWindowInsetsListener(view) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            val tecladoAberto = insets.isVisible(
                WindowInsetsCompat.Type.ime()
            )

            val bottomInset = if (tecladoAberto) {
                ime.bottom
            } else {
                systemBars.bottom
            }

            val top = if (aplicarTopo) {
                systemBars.top
            } else {
                view.paddingTop
            }

            val bottom = if (aplicarInferior) {
                bottomInset
            } else {
                view.paddingBottom
            }

            view.setPadding(
                view.paddingLeft,
                top,
                view.paddingRight,
                bottom
            )

            insets
        }
    }
}