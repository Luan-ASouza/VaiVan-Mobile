package com.example.vaivan.core

import android.app.Application
import com.example.vaivan.core.notificacao.CanaisNotificacao
import com.example.vaivan.core.notificacao.TokenDispositivo
import com.example.vaivan.core.sync.SyncManager
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VaiVanApplication : Application() {

    lateinit var syncManager: SyncManager

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val database =
            VaivanDatabase.getInstance(this)

        val usuarioRepository =
            UsuarioRepository(
                database.usuarioDao()
            )

        val passageiroRepository =
            PassageiroRepository(
                database.passageiroDao()
            )

        val documentoRepository =
            DocumentoRepository(
                database.documentoDao()
            )

        syncManager =
            SyncManager(
                usuarioRepository,
                passageiroRepository,
                documentoRepository
            )

        syncManager.iniciar()

        // Notificações: canais do Android + registro do aparelho a cada login
        CanaisNotificacao.criarTodos(this)

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                escopo.launch { TokenDispositivo.registrar() }
            }
        }
    }
}