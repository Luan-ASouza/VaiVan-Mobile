package com.example.vaivan.ui.motorista.rotas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.repository.RotaRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MinhasRotasViewModel(
    private val rotaRepository: RotaRepository
) : ViewModel() {

    private val motoristaId =
        FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid

    val rotas: StateFlow<List<RotaEntity>> =

        if (motoristaId != null) {

            rotaRepository
                .observarRotasPorMotorista(
                    motoristaId
                )
                .stateIn(
                    scope = viewModelScope,
                    started =
                        SharingStarted.WhileSubscribed(5_000),
                    initialValue =
                        emptyList()
                )

        } else {

            kotlinx.coroutines.flow
                .MutableStateFlow(
                    emptyList()
                )
        }
}