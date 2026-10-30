package com.example.vaivan.core.sync

import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class SyncManager(
    private val usuarioRepository: UsuarioRepository,
    private val passageiroRepository: PassageiroRepository,
    private val documentoRepository: DocumentoRepository
) {

    private val listeners =
        mutableListOf<ListenerRegistration>()

    fun iniciar() {

        parar()

        val usuarioId =
            FirebaseAuth
                .getInstance()
                .currentUser
                ?.uid
                ?: return

        listeners +=
            usuarioRepository
                .iniciarSincronizacaoPorId(usuarioId)

        listeners +=
            passageiroRepository
                .iniciarSincronizacaoDosPassageirosDoResponsavel(usuarioId)

        documentoRepository.iniciarSincronizacao()

        // futuramente:
        // listeners += rotaRepository.iniciarSincronizacao(usuarioId)
    }

    fun parar() {

        listeners.forEach {
            it.remove()
        }

        listeners.clear()

        documentoRepository.pararSincronizacao()
    }
}