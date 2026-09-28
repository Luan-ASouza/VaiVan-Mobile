package com.example.vaivan.ui.responsavel

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
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
import com.example.vaivan.ui.inicio.LoginActivity
import com.example.vaivan.ui.inicio.cadastro.UsuarioViewModel
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeResponsavelActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationController
    private lateinit var viewModel: UsuarioViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_home_responsavel)

        val txtNomeDoUsuario = findViewById<TextView>(R.id.txtNomeDoUsuario)
        val drawerTxtNomeDoUsuario = findViewById<TextView>(R.id.drawerTxtNomeDoUsuario)
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val btnMenu = findViewById<ConstraintLayout>(R.id.btnMenu)
        val btnDesconectar = findViewById<LinearLayout>(R.id.btnDrawerDesconectar)

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
                return UsuarioViewModel(repository) as T
            }
        }

        viewModel = ViewModelProvider(this, factory)[UsuarioViewModel::class.java]

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
            viewModel.sincronizarUsuarioPorId(uid)

            // --------------------------------------------------
            // ROOM → TELA
            // --------------------------------------------------
            //
            // Observa o Room.
            // Quando sincronizarUsuarioPorId() salvar os dados,
            // este Flow será atualizado automaticamente.
            //
            lifecycleScope.launch {

                repeatOnLifecycle(Lifecycle.State.STARTED) {

                    viewModel
                        .observarPorId(uid)
                        .collect { usuario ->

                            if (usuario != null) {
                                txtNomeDoUsuario.text = usuario.nome
                                drawerTxtNomeDoUsuario.text = usuario.nome
                            }
                        }
                }
            }
        }

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        btnDesconectar.setOnClickListener {

            lifecycleScope.launch {

                /*
                 * Limpa o Room fora da Main Thread.
                 */
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                }

                /*
                 * Desconecta do Firebase.
                 */
                FirebaseAuth
                    .getInstance()
                    .signOut()

                /*
                 * Volta para o Login.
                 */
                val intent =
                    Intent(
                        this@HomeResponsavelActivity,
                        LoginActivity::class.java
                    )

                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK

                startActivity(intent)
            }
        }

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

                NavigationItem.ASSINATURAS ->
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
