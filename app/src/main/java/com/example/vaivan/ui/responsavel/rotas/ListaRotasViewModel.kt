package com.example.vaivan.ui.responsavel.rotas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.models.RotasDoResponsavel
import com.example.vaivan.domain.usecase.rota.BuscarRotasDoResponsavelUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ListaRotasViewModel(
    private val buscarRotasDoResponsavelUseCase:
    BuscarRotasDoResponsavelUseCase
) : ViewModel() {

    private val _rotas =
        MutableStateFlow<List<RotasDoResponsavel>>(
            emptyList()
        )

    val rotas: StateFlow<List<RotasDoResponsavel>> =
        _rotas.asStateFlow()

    fun carregarRotas(
        responsavelId: String
    ) {

        viewModelScope.launch {

            _rotas.value =
                buscarRotasDoResponsavelUseCase(
                    responsavelId
                )
        }
    }
}