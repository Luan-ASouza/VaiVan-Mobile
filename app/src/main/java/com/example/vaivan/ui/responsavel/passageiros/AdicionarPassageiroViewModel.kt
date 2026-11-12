package com.example.vaivan.ui.responsavel.passageiros

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.PassageiroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Cadastro de passageiro: salva e, se houver, sobe a foto (galeria ou avatar). */
class AdicionarPassageiroViewModel(
    private val repository: PassageiroRepository
) : ViewModel() {

    sealed interface Estado {
        data object Editando : Estado
        data object Salvando : Estado
        /** [aviso]: o passageiro foi salvo, mas algo secundário falhou (foto). */
        data class Cadastrado(val aviso: String? = null) : Estado
        data class Erro(val mensagem: String) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Editando)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    fun cadastrar(passageiro: PassageiroEntity, foto: Uri?) {
        if (_estado.value == Estado.Salvando) return
        _estado.value = Estado.Salvando

        viewModelScope.launch {
            val id = try {
                repository.salvarPassageiro(passageiro)
            } catch (e: Exception) {
                _estado.value = Estado.Erro("Erro ao salvar passageiro: ${e.message}")
                return@launch
            }

            // Já salvo: se só a foto falhar, não volta pro formulário (tentar de
            // novo duplicaria o passageiro) — dá pra trocar a foto no perfil dele.
            // A foto já vem recortada/comprimida (galeria) ou renderizada (avatar).
            _estado.value = try {
                foto?.let { repository.atualizarFoto(passageiro.copy(id = id), it) }
                Estado.Cadastrado()
            } catch (e: Exception) {
                Estado.Cadastrado(aviso = "Passageiro cadastrado, mas a foto não foi enviada. Tente de novo no perfil dele.")
            }
        }
    }

    fun erroTratado() {
        if (_estado.value is Estado.Erro) _estado.value = Estado.Editando
    }
}
