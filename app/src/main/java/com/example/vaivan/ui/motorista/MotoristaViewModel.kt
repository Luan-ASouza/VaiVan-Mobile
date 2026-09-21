package com.example.vaivan.ui.motorista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.repository.UsuarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MotoristaViewModel(
    private val repository: UsuarioRepository
) : ViewModel() {

    init {
        // 1. Assim que o ViewModel é criado, ele ativa a escuta em tempo real do Firebase
        repository.iniciarSincronizacao()
    }

    /**
     * 2. Esta função expõe o Flow do banco local (Room).
     * O teu Fragment vai chamar esta função passando o UID do utilizador logado.
     */
    fun observarPorId(id: String): Flow<UsuarioEntity?> {
        return repository.observarUsuarioPorId(id)
    }

    /**
     * 3. Caso queiras dar a opção do utilizador apagar a conta ou perfil
     */
    fun deletar(id: String) {
        viewModelScope.launch {
            repository.excluirUsuario(id)
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