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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.room.Room

import com.example.trabalhograua.R
import com.example.trabalhograua.data.local.VaivanDatabase
import com.example.trabalhograua.data.repository.ResponsavelRepository
import com.example.trabalhograua.ui.responsavel.passageiros.ListaPassageirosFragment
import com.example.trabalhograua.ui.responsavel.rotas.ListaRotasFragment
import com.example.trabalhograua.ui.responsavel.chat.ChatFragment
import com.example.trabalhograua.ui.responsavel.perfil.PerfilFragment
import com.example.trabalhograua.ui.responsavel.navigation.BottomNavigationController
import com.example.trabalhograua.ui.responsavel.navigation.NavigationItem
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeResponsavelActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationController
    private lateinit var viewModel: ResponsavelViewModel // 1. Declara o ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_responsavel)

        // 2. Mapeia o campo correto do XML para o nome
        val txtNomeDoUsuario = findViewById<TextView>(R.id.txtNomeDoUsuario)

        // 3. Inicializa o Room, o Repositório e a Factory Nativa (igual fizemos no fragment)
        // Se o nome do seu banco for diferente de AppDatabase, troque aqui.
        val database = Room.databaseBuilder(
            applicationContext,
            VaivanDatabase::class.java,
            "nome_do_seu_banco"
        ).build()

        val responsavelDao = database.responsavelDao()
        val repository = ResponsavelRepository(responsavelDao)

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ResponsavelViewModel(repository) as T
            }
        }

        // 4. Instancia o ViewModel
        viewModel = ViewModelProvider(this, factory)[ResponsavelViewModel::class.java]

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            // 5. Escuta o banco local em tempo real para atualizar o nome no cabeçalho
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.observarPorId(uid).collect { responsavel ->
                        if (responsavel != null) {
                            // Altera apenas o campo do nome, mantendo o "Olá" quieto no canto dele
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
            abrirFragment(ListaPassageirosFragment())
        }
    }

    private fun configurarBottomNavigation() {
        bottomNavigation = BottomNavigationController(findViewById(R.id.bottomNavigation))
        bottomNavigation.setOnItemSelected { item ->
            when (item) {
                NavigationItem.PASSAGEIROS -> abrirFragment(ListaPassageirosFragment())
                NavigationItem.ROTAS -> abrirFragment(ListaRotasFragment())
                NavigationItem.CHAT -> abrirFragment(ChatFragment())
                NavigationItem.PERFIL -> abrirFragment(PerfilFragment())
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
            topGap.updateLayoutParams { height = systemBars.top }
            bottomGap.updateLayoutParams { height = systemBars.bottom }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}