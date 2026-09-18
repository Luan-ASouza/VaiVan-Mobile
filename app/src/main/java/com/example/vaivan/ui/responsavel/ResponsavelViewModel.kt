package com.example.vaivan.ui.responsavel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.repository.UsuarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ResponsavelViewModel(
    private val repository: UsuarioRepository
) : ViewModel() {

    /**
     * Observa o perfil do responsável no Room.
     *
     * O Room é utilizado como cache local.
     * Sempre que os dados forem atualizados no Room,
     * a tela recebe automaticamente os novos valores.
     */
    fun observarPorId(id: String): Flow<UsuarioEntity?> {
        return repository.observarPorId(id)
    }

    /**
     * Sincroniza o perfil do usuário logado:
     *
     * Firebase/Firestore → Room
     *
     * Deve ser chamada utilizando o UID do usuário
     * autenticado pelo FirebaseAuth.
     */
    fun sincronizarPorId(id: String) {
        viewModelScope.launch {
            try {
                repository.sincronizarPorId(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Exclui o perfil do responsável.
     *
     * Remove tanto do Firestore quanto do Room,
     * conforme definido no Repository.
     */
    fun deletar(id: String) {
        viewModelScope.launch {
            try {
                repository.excluir(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
