package com.example.trabalhograua.ui.responsavel

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.trabalhograua.R
import com.example.trabalhograua.ui.responsavel.passageiros.ListaPassageirosFragment
//import com.example.trabalhograua.ui.responsavel.rotas.ListaPassageirosFragment
//import com.example.trabalhograua.ui.responsavel.chat.ChatFragment
import com.example.trabalhograua.ui.responsavel.perfil.PerfilFragment
import com.example.trabalhograua.ui.responsavel.navigation.BottomNavigationController
import com.example.trabalhograua.ui.responsavel.navigation.NavigationItem

class HomeResponsavelActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_responsavel)

        configurarBottomNavigation()

        // Carrega a tela inicial apenas na primeira criação da Activity
        if (savedInstanceState == null) {
            abrirFragment(ListaPassageirosFragment())
        }
    }

    private fun configurarBottomNavigation() {

        bottomNavigation = BottomNavigationController(
            findViewById(R.id.bottomNavigation)
        )

        bottomNavigation.setOnItemSelected { item ->

            when (item) {

                NavigationItem.PASSAGEIROS -> {
                    abrirFragment(ListaPassageirosFragment())
                }

                NavigationItem.ROTAS -> {
                    abrirFragment(PerfilFragment())
                }

                NavigationItem.CHAT -> {
                    abrirFragment(ListaPassageirosFragment())
                }

                NavigationItem.PERFIL -> {
                    abrirFragment(PerfilFragment())
                }
            }

        }
    }

    private fun abrirFragment(fragment: Fragment) {

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

    }
}