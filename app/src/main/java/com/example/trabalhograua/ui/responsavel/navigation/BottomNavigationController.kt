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

    private val items = mapOf(
        NavigationItem.PASSAGEIROS to passageiros,
        NavigationItem.ROTAS to rotas,
        NavigationItem.CHAT to chat,
        NavigationItem.PERFIL to perfil
    )

    private var selectedItem: NavigationItem = NavigationItem.PASSAGEIROS

    init {
        // aplica o estado inicial (chat selecionado, igual estava no XML)
        updateSelectedBackground()
    }

    fun setOnItemSelected(listener: (NavigationItem) -> Unit) {

        passageiros.setOnClickListener {
            selectItem(NavigationItem.PASSAGEIROS)
            listener(NavigationItem.PASSAGEIROS)
        }

        rotas.setOnClickListener {
            selectItem(NavigationItem.ROTAS)
            listener(NavigationItem.ROTAS)
        }

        chat.setOnClickListener {
            selectItem(NavigationItem.CHAT)
            listener(NavigationItem.CHAT)
        }

        perfil.setOnClickListener {
            selectItem(NavigationItem.PERFIL)
            listener(NavigationItem.PERFIL)
        }
    }

    private fun selectItem(item: NavigationItem) {
        selectedItem = item
        updateSelectedBackground()
    }

    private fun updateSelectedBackground() {
        items.forEach { (item, view) ->
            view.setBackgroundResource(
                if (item == selectedItem) R.drawable.bg_bottomnav_orange
                else R.drawable.bg_bottomnav_white
            )
        }
    }
}

enum class NavigationItem {
    PASSAGEIROS,
    ROTAS,
    CHAT,
    PERFIL
}