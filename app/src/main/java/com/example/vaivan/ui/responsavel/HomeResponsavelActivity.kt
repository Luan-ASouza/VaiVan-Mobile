package com.example.vaivan.ui.responsavel

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.ui.responsavel.chat.ChatFragment
import com.example.vaivan.ui.responsavel.navigation.BottomNavigationController
import com.example.vaivan.ui.responsavel.navigation.NavigationItem
import com.example.vaivan.ui.responsavel.passageiros.ListaPassageirosFragment
import com.example.vaivan.ui.responsavel.perfil.PerfilFragment
import com.example.vaivan.ui.responsavel.rotas.ListaRotasFragment
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeResponsavelActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationController
    private lateinit var viewModel: ResponsavelViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_home_responsavel)

        val txtNomeDoUsuario = findViewById<TextView>(R.id.txtNomeDoUsuario)

        // --------------------------------------------------
        // DATABASE / REPOSITORY / VIEWMODEL
        // --------------------------------------------------

        val database = VaivanDatabase.getInstance(this)
        val UsuarioDao = database.UsuarioDao()
        val repository = UsuarioRepository(UsuarioDao)

        val factory = object : ViewModelProvider.Factory {

            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>
            ): T {
                return ResponsavelViewModel(repository) as T
            }
        }

        viewModel = ViewModelProvider(
            this,
            factory
        )[ResponsavelViewModel::class.java]

        // --------------------------------------------------
        // USUÁRIO LOGADO
        // --------------------------------------------------

        val uid = FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid

        if (uid != null) {

            // --------------------------------------------------
            // FIREBASE → ROOM
            // --------------------------------------------------
            //
            // Busca o perfil atualizado no Firestore
            // e salva no Room.
            //
            viewModel.sincronizarPorId(uid)

            // --------------------------------------------------
            // ROOM → TELA
            // --------------------------------------------------
            //
            // Observa o Room.
            // Quando sincronizarPorId() salvar os dados,
            // este Flow será atualizado automaticamente.
            //
            lifecycleScope.launch {

                repeatOnLifecycle(Lifecycle.State.STARTED) {

                    viewModel
                        .observarPorId(uid)
                        .collect { responsavel ->

                            if (responsavel != null) {
                                txtNomeDoUsuario.text = responsavel.nome
                            }
                        }
                }
            }
        }

        // --------------------------------------------------
        // NAVEGAÇÃO
        // --------------------------------------------------

        configurarBottomNavigation()

        // --------------------------------------------------
        // WINDOW INSETS
        // --------------------------------------------------

        WindowCompat.setDecorFitsSystemWindows(window, false)

        applyTopAndBottomGaps(
            findViewById(android.R.id.content)
        )

        // --------------------------------------------------
        // FRAGMENT INICIAL
        // --------------------------------------------------

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

                NavigationItem.PASSAGEIROS ->
                    abrirFragment(
                        ListaPassageirosFragment()
                    )

                NavigationItem.ROTAS ->
                    abrirFragment(
                        ListaRotasFragment()
                    )

                NavigationItem.CHAT ->
                    abrirFragment(
                        ChatFragment()
                    )

                NavigationItem.PERFIL ->
                    abrirFragment(
                        PerfilFragment()
                    )
            }
        }
    }

    // --------------------------------------------------
    // ABRIR FRAGMENT
    // --------------------------------------------------

    private fun abrirFragment(fragment: Fragment) {

        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,
                fragment
            )
            .commit()
    }
}
