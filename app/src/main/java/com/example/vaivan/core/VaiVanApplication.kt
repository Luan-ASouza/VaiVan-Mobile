package com.example.vaivan.core

import android.app.Application
import com.example.vaivan.core.sync.SyncManager
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.UsuarioRepository

class VaiVanApplication : Application() {

    lateinit var syncManager: SyncManager

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
    }
}