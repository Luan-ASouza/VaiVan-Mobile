package com.example.trabalhograua.ui.motorista

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.trabalhograua.R
import com.example.trabalhograua.data.local.VaivanDatabase
import com.example.trabalhograua.data.repository.ResponsavelRepository
import com.example.trabalhograua.ui.motorista.chat.ChatFragment
import com.example.trabalhograua.ui.motorista.navigation.BottomNavigationControllerMotorista
import com.example.trabalhograua.ui.motorista.navigation.NavigationItem
import com.example.trabalhograua.ui.motorista.perfil.PerfilFragment
import com.example.trabalhograua.ui.motorista.veiculos.ListaVeiculosFragment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeMotoristaActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationControllerMotorista
    private lateinit var viewModel: MotoristaViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_motorista)

        val txtNomeDoUsuario = findViewById<TextView>(R.id.txtNomeDoUsuario)

        // CORREÇÃO: Usar o Singleton do banco de dados para evitar erro de esquema
        val database = VaivanDatabase.getInstance(this)
        val responsavelDao = database.responsavelDao()
        val repository = ResponsavelRepository(responsavelDao)

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MotoristaViewModel(repository) as T
            }
        }

        viewModel = ViewModelProvider(this, factory)[MotoristaViewModel::class.java]

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.observarPorId(uid).collect { responsavel ->
                        if (responsavel != null) {
                            txtNomeDoUsuario.text = responsavel.nome
                        }
                    }
                }
            }
        }

        configurarBottomNavigation()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBarGaps(findViewById(android.R.id.content))

        if (savedInstanceState == null) {
            abrirFragment(ListaVeiculosFragment())
        }

    }

    private fun configurarBottomNavigation() {
        bottomNavigation = BottomNavigationControllerMotorista(findViewById(R.id.bottomNavigation))
        bottomNavigation.setOnItemSelected { item ->
            when (item) {
                NavigationItem.VEICULOS -> abrirFragment(ListaVeiculosFragment())
                NavigationItem.ROTAS -> abrirFragment(ListaVeiculosFragment())
                NavigationItem.CHAT -> abrirFragment(ChatFragment())
                NavigationItem.PERFIL -> abrirFragment(PerfilFragment())
            }
        }
    }

    //Abre o Fragment selecionado
    private fun abrirFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    //Aplica gaps em cima e embaixo de forma dinâmica
    fun applySystemBarGaps(root: View) {
        val topGap = root.findViewById<View>(R.id.topGap)
        val bottomGap = root.findViewById<View>(R.id.bottomGap)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topGap.updateLayoutParams { height = systemBars.top }
            bottomGap.updateLayoutParams { height = systemBars.bottom }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}