package com.example.trabalhograua.ui.responsavel.navigation

import android.view.View
import android.widget.LinearLayout
import com.example.trabalhograua.R

class BottomNavigationController(
    root: View
) {

    private val passageiros = root.findViewById<LinearLayout>(R.id.navPassageiros)
    private val rotas = root.findViewById<LinearLayout>(R.id.navRotas)
    private val chat = root.findViewById<LinearLayout>(R.id.navChat)
    private val perfil = root.findViewById<LinearLayout>(R.id.navPerfil)

    fun setOnItemSelected(listener: (NavigationItem) -> Unit) {

        passageiros.setOnClickListener {
            listener(NavigationItem.PASSAGEIROS)
        }

        rotas.setOnClickListener {
            listener(NavigationItem.ROTAS)
        }

        chat.setOnClickListener {
            listener(NavigationItem.CHAT)
        }

        perfil.setOnClickListener {
            listener(NavigationItem.PERFIL)
        }
    }
}

enum class NavigationItem {
    PASSAGEIROS,
    ROTAS,
    CHAT,
    PERFIL
}