package com.example.vaivan.ui.responsavel.passageiros

import androidx.lifecycle.AndroidViewModel
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ListaPassageirosViewModel(
    application: android.app.Application
) : AndroidViewModel(application) {

    private val repository =
        PassageiroRepository(
            VaivanDatabase
                .getInstance(application)
                .passageiroDao()
        )

    private val uid =
        FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid

    val passageiros: Flow<List<PassageiroEntity>> =
        if (uid != null) {
            repository.observarPassageirosDoResponsavel(uid)
        } else {
            flowOf(emptyList())
        }
}