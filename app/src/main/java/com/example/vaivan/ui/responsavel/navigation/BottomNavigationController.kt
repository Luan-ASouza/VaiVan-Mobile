package com.example.vaivan.ui.responsavel.navigation

import android.view.View
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.vaivan.R

class BottomNavigationController(
    root: View
) {

    private val passageiros = root.findViewById<LinearLayout>(R.id.navPassageiros)
    private val rotas = root.findViewById<LinearLayout>(R.id.navRotas)
    private val assinaturas = root.findViewById<LinearLayout>(R.id.navAssinaturas)

    private val items = mapOf(
        NavigationItem.PASSAGEIROS to passageiros,
        NavigationItem.ROTAS to rotas,
        NavigationItem.ASSINATURAS to assinaturas
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

        assinaturas.setOnClickListener {
            selectItem(NavigationItem.ASSINATURAS)
            listener(NavigationItem.ASSINATURAS)
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
    ASSINATURAS,
    PERFIL
}