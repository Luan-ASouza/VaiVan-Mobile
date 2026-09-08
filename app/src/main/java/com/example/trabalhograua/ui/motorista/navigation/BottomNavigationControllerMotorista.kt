package com.example.trabalhograua.ui.motorista.navigation

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.trabalhograua.R

class BottomNavigationControllerMotorista(
    root: View
) {

    private val veiculos = root.findViewById<LinearLayout>(R.id.navVeiculosMotorista)
    private val rotas = root.findViewById<LinearLayout>(R.id.navRotasMotorista)
    private val chat = root.findViewById<LinearLayout>(R.id.navChatMotorista)
    private val perfil = root.findViewById<LinearLayout>(R.id.navPerfilMotorista)

    private val items = mapOf(
        NavigationItem.VEICULOS to veiculos,
        NavigationItem.ROTAS to rotas,
        NavigationItem.CHAT to chat,
        NavigationItem.PERFIL to perfil
    )

    private var selectedItem: NavigationItem = NavigationItem.VEICULOS

    init {
        // aplica o estado inicial (chat selecionado, igual estava no XML)
        updateSelectedBackground()
    }

    fun setOnItemSelected(listener: (NavigationItem) -> Unit) {

        veiculos.setOnClickListener {
            selectItem(NavigationItem.VEICULOS)
            listener(NavigationItem.VEICULOS)
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
            // 1. Define o fundo do LinearLayout (view) baseado na seleção
            val backgroundRes = if (item == selectedItem) {
                R.drawable.bg_bottomnav_black
            } else {
                R.drawable.bg_bottomnav_white
            }
            view.setBackgroundResource(backgroundRes)

            // 2. Encontra a ImageView e a TextView dentro desse LinearLayout
            val imageView = view.getChildAt(0) as ImageView
            val textView = view.getChildAt(1) as TextView

            // 3. Define a cor do ícone e letra baseada na seleção
            val iconColor = if (item == selectedItem) {
                ContextCompat.getColor(view.context, R.color.white)
            } else {
                ContextCompat.getColor(view.context, R.color.dark_gray)
            }
            imageView.setColorFilter(iconColor)
            textView.setTextColor(iconColor)
        }
    }
}

enum class NavigationItem {
    VEICULOS,
    ROTAS,
    CHAT,
    PERFIL
}