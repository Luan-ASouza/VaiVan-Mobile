package com.example.vaivan.ui.comum.locais

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.core.util.TextoUtil
import com.example.vaivan.data.local.entities.LocalEntity
import com.example.vaivan.data.repository.LocalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LocaisSalvosViewModel(
    private val repository: LocalRepository,
    usuarioId: String
) : ViewModel() {

    /** null = ainda não chegou nada do cache (não mostra "lista vazia" à toa). */
    private val todos: StateFlow<List<LocalEntity>?> =
        repository.observarLocais(usuarioId)
            .map<List<LocalEntity>, List<LocalEntity>?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _busca = MutableStateFlow("")
    val busca: StateFlow<String> = _busca.asStateFlow()

    /** Locais filtrados pela busca (nome ou endereço, sem acento/maiúscula). */
    val locais: StateFlow<List<LocalEntity>?> =
        combine(todos, _busca) { lista, busca ->
            val termo = TextoUtil.normalizar(busca)
            if (lista == null || termo.isEmpty()) {
                lista
            } else {
                lista.filter {
                    TextoUtil.normalizar(it.nome).contains(termo) ||
                        TextoUtil.normalizar(it.endereco).contains(termo)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Há algum local salvo (independente da busca) — decide a mensagem de vazio. */
    val temLocais: Boolean get() = !todos.value.isNullOrEmpty()

    init {
        repository.iniciarSincronizacao(usuarioId)
    }

    fun buscar(texto: String) {
        _busca.value = texto
    }

    override fun onCleared() {
        repository.pararSincronizacao()
        super.onCleared()
    }
}
