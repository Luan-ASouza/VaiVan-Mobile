package com.example.vaivan.ui.inicio.cadastro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.repository.UsuarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class UsuarioViewModel(
    private val repository: UsuarioRepository
) : ViewModel() {

    fun observarPorId(id: String): Flow<UsuarioEntity?> {
        return repository.observarUsuarioPorId(id)
    }

    fun sincronizarUsuarioPorId(id: String) {
        viewModelScope.launch {
            try {
                repository.sincronizarUsuarioPorId(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deletar(id: String) {
        viewModelScope.launch {
            try {
                repository.excluirUsuario(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


}