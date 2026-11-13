package com.example.vaivan.ui.motorista

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vaivan.R
import com.example.vaivan.core.notificacao.DestinoNotificacao
import com.example.vaivan.core.notificacao.PermissaoNotificacao
import com.example.vaivan.ui.comum.perfil.AlterarSenhaActivity
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.ui.motorista.chat.ChatFragment
import com.example.vaivan.ui.motorista.navigation.BottomNavigationControllerMotorista
import com.example.vaivan.ui.motorista.navigation.NavigationItem
import com.example.vaivan.ui.motorista.perfil.PerfilFragment
import com.example.vaivan.ui.motorista.veiculos.ListaVeiculosFragment
import com.example.vaivan.ui.inicio.LoginActivity
import com.example.vaivan.ui.inicio.UsuarioViewModel
import com.example.vaivan.ui.comum.locais.LocaisSalvosActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy
import com.example.vaivan.ui.motorista.assinatura.AssinaturaActivity
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.example.vaivan.ui.motorista.assinatura.PagamentosMotoristaActivity
import com.example.vaivan.data.local.dao.UsuarioDao
import com.example.vaivan.ui.motorista.entrada.documentos.DocumentosMotoristaActivity
import com.example.vaivan.ui.motorista.precos.TabelaPrecoActivity

class HomeMotoristaActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationControllerMotorista
    private lateinit var viewModel: UsuarioViewModel
    private val permissaoNotificacao = PermissaoNotificacao(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_motorista)

        val txtNomeDoUsuario = findViewById<TextView>(R.id.txtNomeDoUsuario)
        val drawerTxtNomeDoUsuario = findViewById<TextView>(R.id.drawerTxtNomeDoUsuario)
        val imgAvatarHeader = findViewById<ShapeableImageView>(R.id.shapeableImageView)
        val imgAvatarDrawer = findViewById<ShapeableImageView>(R.id.drawerAvatar)
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val btnMenu = findViewById<ConstraintLayout>(R.id.btnMenu)

        viewModel = ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    val database = VaivanDatabase.getInstance(this@HomeMotoristaActivity)
                    UsuarioViewModel(UsuarioRepository(database.usuarioDao()))
                }
            }
        )[UsuarioViewModel::class.java]

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {

            // Baixa o usuário do Firestore para o Room (o logout limpa o Room,
            // então sem isso o nome real não aparece e sobra o texto de exemplo).
            viewModel.sincronizarUsuarioPorId(uid)

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.observarPorId(uid).collect { usuario ->
                        if (usuario != null) {
                            txtNomeDoUsuario.text = "${usuario.nome} ${usuario.sobrenome}"
                            drawerTxtNomeDoUsuario.text = "${usuario.nome} ${usuario.sobrenome}"

                            Glide.with(imgAvatarHeader)
                                .load(usuario.fotoUrl)
                                .placeholder(R.drawable.ic_perfil)
                                .error(R.drawable.ic_perfil)
                                .circleCrop()
                                .into(imgAvatarHeader)

                            Glide.with(imgAvatarDrawer)
                                .load(usuario.fotoUrl)
                                .placeholder(R.drawable.ic_perfil)
                                .error(R.drawable.ic_perfil)
                                .circleCrop()
                                .into(imgAvatarDrawer)
                        }
                    }
                }
            }
        }

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        configurarMenuLateral(drawerLayout)

        configurarDesconectar()

        findViewById<View>(R.id.btnDrawerDocumentos).setOnClickListener {
            drawerLayout.closeDrawers()
            startActivity(Intent(this, DocumentosMotoristaActivity::class.java))
        }

        configurarAssinatura(uid, drawerLayout)

        configurarBottomNavigation()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBarGaps(findViewById(android.R.id.content))

        if (savedInstanceState == null) {
            // Toque numa notificação pode pedir uma aba específica.
            when (intent.getStringExtra(DestinoNotificacao.EXTRA_ABA)) {
                DestinoNotificacao.ABA_ROTAS -> bottomNavigation.selecionar(NavigationItem.ROTAS)
                DestinoNotificacao.ABA_CHAT -> bottomNavigation.selecionar(NavigationItem.CHAT)
                else -> abrirFragment(ListaVeiculosFragment())
            }
        }

        permissaoNotificacao.solicitarSeNecessario()

    }

    /**
     * Itens do menu lateral
     */
    private fun configurarMenuLateral(drawerLayout: DrawerLayout) {

        findViewById<View>(R.id.btnDrawerPerfil).setOnClickListener {
            drawerLayout.closeDrawers()
            abrirFragment(PerfilFragment())
        }

        findViewById<View>(R.id.btnDrawerLocais).setOnClickListener {
            drawerLayout.closeDrawers()
            startActivity(Intent(this, LocaisSalvosActivity::class.java))
        }

        findViewById<View>(R.id.btnDrawerVeiculos).setOnClickListener {
            drawerLayout.closeDrawers()
            abrirFragment(ListaVeiculosFragment())
        }
    }

    /**
     * Botão "Desconectar" do menu lateral (mesmo fluxo do responsável):
     * limpa o Room, desconecta do Firebase e volta para o Login.
     */
    private fun configurarDesconectar() {

        val btnDesconectar = findViewById<View>(R.id.btnDrawerDesconectar)

        if (btnDesconectar == null) {
            Log.w(
                "LOGOUT",
                "btnDrawerDesconectar não existe no activity_home_motorista.xml"
            )
            return
        }

        btnDesconectar.setOnClickListener {

            lifecycleScope.launch {

                // Limpa o Room fora da Main Thread
                withContext(Dispatchers.IO) {
                    VaivanDatabase.getInstance(this@HomeMotoristaActivity).clearAllTables()
                }

                // Desconecta do Firebase
                FirebaseAuth.getInstance().signOut()

                // Volta para o Login, sem deixar telas antigas na pilha
                val intent = Intent(
                    this@HomeMotoristaActivity,
                    LoginActivity::class.java
                )

                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                startActivity(intent)
            }
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

        findViewById<View>(R.id.btnDrawerTabelaPrecos).setOnClickListener {
            drawerLayout.closeDrawers()
            startActivity(Intent(this, TabelaPrecoActivity::class.java))
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
        marcarItemDoMenuLateral(fragment)
    }

    /**
     * Destaca no menu lateral (a "aba" clara) o item da tela aberta.
     * Itens que abrem Activity própria nunca ficam marcados.
     */
    private fun marcarItemDoMenuLateral(fragment: Fragment) {
        val ativo = when (fragment) {
            is PerfilFragment -> R.id.btnDrawerPerfil
            is ListaVeiculosFragment -> R.id.btnDrawerVeiculos
            else -> null
        }
        listOf(R.id.btnDrawerPerfil, R.id.btnDrawerVeiculos).forEach { id ->
            findViewById<View>(id).isSelected = id == ativo
        }
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