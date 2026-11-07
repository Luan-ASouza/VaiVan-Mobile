package com.example.vaivan.ui.responsavel.passageiros

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class ListaPassageirosViewModel(
    private val repository: PassageiroRepository
) : ViewModel() {

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

    /** Exclusão em cascata (paradas/solicitações pendentes/foto) já fica no
     * PassageiroRepository.excluirPassageiro — aqui é só a ponte pra UI. */
    suspend fun excluirPassageiro(id: String) {
        repository.excluirPassageiro(id)
    }
}
