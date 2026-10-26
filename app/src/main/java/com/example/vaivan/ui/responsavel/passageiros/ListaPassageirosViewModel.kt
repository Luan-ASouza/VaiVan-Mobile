package com.example.vaivan.ui.responsavel.passageiros

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class ListaPassageirosViewModel(
    application: Application
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

    init {
        viewModelScope.launch {
            passageiros.collect { lista ->
                Log.d(
                    "ROOM_DEBUG",
                    "Passageiros do responsável no Room: ${lista.size}"
                )

                lista.forEach { passageiro ->
                    Log.d(
                        "ROOM_DEBUG",
                        "id=${passageiro.id}, " +
                                "nome=${passageiro.nome}, " +
                                "responsavelId=${passageiro.responsavelId}"
                    )
                }
            }
        }
    }

    val passageiros: Flow<List<PassageiroEntity>> =
        if (uid != null) {
            repository.observarPassageirosDoResponsavel(uid)
        } else {
            flowOf(emptyList())
        }
}