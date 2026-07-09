package com.example.trabalhograua.ui.responsavel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trabalhograua.data.local.entities.ResponsavelEntity
import com.example.trabalhograua.data.repository.ResponsavelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ResponsavelViewModel(
    private val repository: ResponsavelRepository
) : ViewModel() {

    init {
        // 1. Assim que o ViewModel é criado, ele ativa a escuta em tempo real do Firebase
        repository.iniciarSincronizacao()
    }

    /**
     * 2. Esta função expõe o Flow do banco local (Room).
     * O teu Fragment vai chamar esta função passando o UID do utilizador logado.
     */
    fun observarPorId(id: String): Flow<ResponsavelEntity?> {
        return repository.observarPorId(id)
    }

    /**
     * 3. Caso queiras dar a opção do utilizador apagar a conta ou perfil
     */
    fun deletar(id: String) {
        viewModelScope.launch {
            repository.excluir(id)
        }
    }

    /**
     * 4. O Android chama esta função automaticamente quando o utilizador sai da tela em definitivo.
     * Aqui desligamos o Firebase de forma segura para não haver fugas de memória.
     */
    override fun onCleared() {
        super.onCleared()
        repository.pararSincronizacao()
    }
}