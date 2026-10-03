package com.example.vaivan.ui.responsavel

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.core.util.SystemBarUtils.applyTopAndBottomGaps
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.ui.inicio.LoginActivity
import com.example.vaivan.ui.inicio.UsuarioViewModel
import com.example.vaivan.ui.responsavel.chat.ChatFragment
import com.example.vaivan.ui.responsavel.navigation.BottomNavigationController
import com.example.vaivan.ui.responsavel.navigation.NavigationItem
import com.example.vaivan.ui.responsavel.passageiros.ListaPassageirosFragment
import com.example.vaivan.ui.responsavel.perfil.PerfilFragment
import com.example.vaivan.ui.responsavel.rotas.ListaRotasFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
        val usuarioDao = database.usuarioDao()
        val repository = UsuarioRepository(usuarioDao)

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>
            ): T {
                return UsuarioViewModel(repository) as T
            }
        }

        viewModel = ViewModelProvider(this, factory)[UsuarioViewModel::class.java]

        // --------------------------------------------------
        // USUÁRIO LOGADO E SINCRONIZAÇÃO
        // --------------------------------------------------

        val uid = FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid

        if (uid != null) {

            // 1. Sincroniza Perfil do Usuário no Room
            viewModel.sincronizarUsuarioPorId(uid)

            // 2. Sincroniza Lista de Passageiros (Firestore -> Room)
            sincronizarPassageirosDoFirestore(uid, database)

            // 3. Observa alterações de Usuário no Room
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
                val intent = Intent(
                    this@HomeResponsavelActivity,
                    LoginActivity::class.java
                )

                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
                NavigationItem.PASSAGEIROS -> abrirFragment(ListaPassageirosFragment())
                NavigationItem.ROTAS -> abrirFragment(ListaRotasFragment())
                NavigationItem.ASSINATURAS -> abrirFragment(ChatFragment())
                NavigationItem.PERFIL -> abrirFragment(PerfilFragment())
            }
        }
    }

    // --------------------------------------------------
    // SINCRONIZAÇÃO FIRESTORE -> ROOM (PASSAGEIROS)
    // --------------------------------------------------

    private fun sincronizarPassageirosDoFirestore(uidResponsavel: String, database: VaivanDatabase) {
        // Tenta buscar no Firestore tanto por "responsavelId" quanto por "idResponsavel"
        FirebaseFirestore.getInstance()
            .collection("passageiros")
            .whereEqualTo("responsavelId", uidResponsavel)
            .get()
            .addOnSuccessListener { querySnapshot ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val passageiros = querySnapshot.toObjects(PassageiroEntity::class.java)
                        if (passageiros.isNotEmpty()) {
                            database.passageiroDao().upsertAll(passageiros)
                        } else {
                            // Tenta busca alternativa caso o nome do campo no Firestore seja "idResponsavel"
                            buscarPassageirosPorCampoAlternativo(uidResponsavel, database)
                        }
                    } catch (e: Exception) {
                        Log.e("HomeResponsavel", "Erro ao converter ou salvar passageiros no Room: ${e.message}", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeResponsavel", "Erro ao buscar passageiros do Firestore: ${e.message}", e)
            }
    }

    private fun buscarPassageirosPorCampoAlternativo(uidResponsavel: String, database: VaivanDatabase) {
        FirebaseFirestore.getInstance()
            .collection("passageiros")
            .whereEqualTo("idResponsavel", uidResponsavel)
            .get()
            .addOnSuccessListener { querySnapshot ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val passageiros = querySnapshot.toObjects(PassageiroEntity::class.java)
                        if (passageiros.isNotEmpty()) {
                            database.passageiroDao().upsertAll(passageiros)
                        }
                    } catch (e: Exception) {
                        Log.e("HomeResponsavel", "Erro no fallback de salvamento do Room: ${e.message}", e)
                    }
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