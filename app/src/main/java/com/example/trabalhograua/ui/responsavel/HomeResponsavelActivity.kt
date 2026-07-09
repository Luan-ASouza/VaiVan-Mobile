package com.example.trabalhograua.ui.responsavel

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

import com.example.trabalhograua.R
import com.example.trabalhograua.repository.FirebaseRepository
import com.example.trabalhograua.ui.responsavel.passageiros.ListaPassageirosFragment
import com.example.trabalhograua.ui.responsavel.rotas.ListaRotasFragment
import com.example.trabalhograua.ui.responsavel.chat.ChatFragment
import com.example.trabalhograua.ui.responsavel.perfil.PerfilFragment
import com.example.trabalhograua.ui.responsavel.navigation.BottomNavigationController
import com.example.trabalhograua.ui.responsavel.navigation.NavigationItem
import com.google.firebase.auth.FirebaseAuth

class HomeResponsavelActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_responsavel)

        val txtBoasVindas = findViewById<TextView>(R.id.txtBemVinda)

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {

            FirebaseRepository().buscarUsuario(

                uid,

                { usuario ->

                    txtBoasVindas.text = "Olá ${usuario.nome}"

                },

                {

                }

            )
        }

        configurarBottomNavigation()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        applySystemBarGaps(findViewById(android.R.id.content))

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
                    abrirFragment(ListaRotasFragment())
                }

                NavigationItem.CHAT -> {
                    abrirFragment(ChatFragment())
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

    fun applySystemBarGaps(root: View) {
        val topGap = root.findViewById<View>(R.id.topGap)
        val bottomGap = root.findViewById<View>(R.id.bottomGap)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            topGap.updateLayoutParams {
                height = systemBars.top
            }

            bottomGap.updateLayoutParams {
                height = systemBars.bottom
            }

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

}

