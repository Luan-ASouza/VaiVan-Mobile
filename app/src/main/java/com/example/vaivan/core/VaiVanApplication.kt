package com.example.vaivan.core

import android.app.Application
import com.example.vaivan.core.sync.SyncManager
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.UsuarioRepository

class VaiVanApplication : Application() {

    lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()

        val database = VaivanDatabase.getInstance(this)

        val usuarioRepository =
            UsuarioRepository(database.UsuarioDao())

        val passageiroRepository =
            PassageiroRepository(database.passageiroDao())

        syncManager = SyncManager(
            usuarioRepository,
            passageiroRepository
        )
    }
}