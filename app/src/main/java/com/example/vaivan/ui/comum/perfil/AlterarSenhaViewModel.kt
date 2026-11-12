package com.example.vaivan.ui.comum.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaivan.core.validation.CadastroValidator
import com.example.vaivan.data.repository.FirebaseAuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlterarSenhaViewModel(
    private val authRepository: FirebaseAuthRepository
) : ViewModel() {

    enum class Campo { ATUAL, NOVA, CONFIRMACAO, GERAL }

    sealed interface Estado {
        data object Editando : Estado
        data object Salvando : Estado
        data object Sucesso : Estado
        data class Erro(val campo: Campo, val mensagem: String) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Editando)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    fun alterar(atual: String, nova: String, confirmacao: String) {
        if (_estado.value == Estado.Salvando) return

        // Mesma regra de senha do cadastro (CadastroValidator.senhaValida).
        val erro = when {
            atual.isEmpty() -> Estado.Erro(Campo.ATUAL, "*Informe a senha atual.")
            !CadastroValidator.senhaValida(nova) ->
                Estado.Erro(Campo.NOVA, "*Use 8 ou mais caracteres, com pelo menos um número e um símbolo.")
            nova == atual -> Estado.Erro(Campo.NOVA, "*A nova senha deve ser diferente da atual.")
            !CadastroValidator.senhasConferem(nova, confirmacao) ->
                Estado.Erro(Campo.CONFIRMACAO, "*As senhas não são iguais.")
            else -> null
        }

        if (erro != null) {
            _estado.value = erro
            return
        }

        _estado.value = Estado.Salvando

        viewModelScope.launch {
            _estado.value = try {
                authRepository.alterarSenha(atual, nova)
                Estado.Sucesso
            } catch (e: FirebaseAuthWeakPasswordException) {
                Estado.Erro(Campo.NOVA, "*Senha muito fraca. Escolha outra.")
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                Estado.Erro(Campo.ATUAL, "*Senha atual incorreta.")
            } catch (e: FirebaseAuthInvalidUserException) {
                Estado.Erro(Campo.GERAL, "Sessão expirada. Faça login novamente.")
            } catch (e: FirebaseTooManyRequestsException) {
                Estado.Erro(Campo.GERAL, "Muitas tentativas. Aguarde alguns minutos.")
            } catch (e: FirebaseNetworkException) {
                Estado.Erro(Campo.GERAL, "Sem conexão. Verifique sua internet.")
            } catch (e: Exception) {
                Estado.Erro(Campo.GERAL, "Não foi possível alterar a senha: ${e.message}")
            }
        }
    }

    /** O usuário voltou a digitar: some com a mensagem de erro. */
    fun limparErro() {
        if (_estado.value is Estado.Erro) _estado.value = Estado.Editando
    }
}
