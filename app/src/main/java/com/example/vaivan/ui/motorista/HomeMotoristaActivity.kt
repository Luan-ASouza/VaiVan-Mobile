package com.example.vaivan.ui.motorista

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vaivan.R
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.ui.motorista.chat.ChatFragment
import com.example.vaivan.ui.motorista.navigation.BottomNavigationControllerMotorista
import com.example.vaivan.ui.motorista.navigation.NavigationItem
import com.example.vaivan.ui.motorista.perfil.PerfilFragment
import com.example.vaivan.ui.motorista.veiculos.ListaVeiculosFragment
import com.example.vaivan.ui.inicio.UsuarioViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import android.content.Intent
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy
import com.example.vaivan.ui.motorista.assinatura.AssinaturaActivity
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.example.vaivan.ui.motorista.assinatura.PagamentosMotoristaActivity
import com.example.vaivan.data.local.dao.UsuarioDao

class HomeMotoristaActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationControllerMotorista
    private lateinit var viewModel: UsuarioViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_motorista)

        val txtNomeDoUsuario = findViewById<TextView>(R.id.txtNomeDoUsuario)
        val drawerTxtNomeDoUsuario = findViewById<TextView>(R.id.drawerTxtNomeDoUsuario)
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val btnMenu = findViewById<ConstraintLayout>(R.id.btnMenu)

        // CORREÇÃO: Usar o Singleton do banco de dados para evitar erro de esquema
        val database = VaivanDatabase.getInstance(this)
        val UsuarioDao = database.usuarioDao()
        val repository = UsuarioRepository(UsuarioDao)

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return UsuarioViewModel(repository) as T
            }
        }

        viewModel = ViewModelProvider(this, factory)[UsuarioViewModel::class.java]

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.observarPorId(uid).collect { usuario ->
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

        configurarAssinatura(uid, drawerLayout)

        configurarBottomNavigation()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBarGaps(findViewById(android.R.id.content))

        if (savedInstanceState == null) {
            abrirFragment(ListaVeiculosFragment())
        }

    }
    /**
     * Plano de assinatura: aviso no topo, atalhos no menu lateral e
     * bloqueio da área de pagamentos quando a assinatura não está em dia.
     */
    private fun configurarAssinatura(uid: String?, drawerLayout: DrawerLayout) {
        val banner = findViewById<TextView>(R.id.bannerAssinatura)
        val btnAssinatura = findViewById<View>(R.id.btnDrawerAssinatura)
        val btnPagamentos = findViewById<View>(R.id.btnDrawerPagamentos)

        val abrirAssinatura = {
            drawerLayout.closeDrawers()
            startActivity(Intent(this, AssinaturaActivity::class.java))
        }
        banner.setOnClickListener { abrirAssinatura() }
        btnAssinatura.setOnClickListener { abrirAssinatura() }
        btnPagamentos.setOnClickListener {
            drawerLayout.closeDrawers()
            startActivity(Intent(this, PagamentosMotoristaActivity::class.java))
        }

        if (uid == null) return

        val repository = AssinaturaRepository()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    repository.observarAssinatura(uid).collect { assinatura ->
                        val agora = System.currentTimeMillis()
                        val estado = AssinaturaPolicy.estado(assinatura, agora)

                        val mensagem = AssinaturaUi.mensagemDoBanner(estado, assinatura)
                        if (mensagem == null) {
                            banner.visibility = View.GONE
                        } else {
                            banner.text = mensagem
                            banner.setBackgroundColor(AssinaturaUi.corDoBanner(estado))
                            banner.visibility = View.VISIBLE
                        }

                        btnPagamentos.visibility =
                            if (AssinaturaPolicy.podeVerPagamentos(assinatura, agora)) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }
                    }
                }

                launch {
                    try {
                        repository.gerarCobrancaDeRenovacaoSeNecessario(uid)
                    } catch (_: Exception) {
                        // Sem conexão: tenta de novo na próxima abertura.
                    }
                }
            }
        }
    }
    private fun configurarBottomNavigation() {
        bottomNavigation = BottomNavigationControllerMotorista(findViewById(R.id.bottomNavigation))
        bottomNavigation.setOnItemSelected { item ->
            when (item) {
                NavigationItem.VEICULOS -> abrirFragment(ListaVeiculosFragment())
                NavigationItem.ROTAS -> abrirFragment(com.example.vaivan.ui.motorista.rotas.MinhasRotasFragment())
                NavigationItem.CHAT -> abrirFragment(ChatFragment())
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