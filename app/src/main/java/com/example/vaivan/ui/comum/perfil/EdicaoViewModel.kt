package com.example.vaivan.ui.comum.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base das telas de "editar um campo" (perfil do usuário, perfil do
 * passageiro): carrega a entidade [T] uma vez, aplica a alteração da tela e
 * salva, expondo um [EstadoEdicao] que o [acompanharEdicao] transforma em
 * loading/toast/fechar tela.
 *
 * A entidade é sempre lida da fonte da verdade antes de editar ([buscar]),
 * porque o `salvar*` dos repositories grava o documento inteiro.
 */
abstract class EdicaoViewModel<T : Any> : ViewModel() {

    private val _item = MutableStateFlow<T?>(null)
    val item: StateFlow<T?> = _item.asStateFlow()

    private val _estado = MutableStateFlow<EstadoEdicao>(EstadoEdicao.Carregando)
    val estado: StateFlow<EstadoEdicao> = _estado.asStateFlow()

    /** Lê a entidade (null = não existe). */
    protected abstract suspend fun buscar(): T?

    /** Grava a entidade já alterada. */
    protected abstract suspend fun gravar(item: T)

    protected open val mensagemNaoEncontrado = "Não foi possível carregar os dados."

    fun carregar() {
        if (_item.value != null) return
        _estado.value = EstadoEdicao.Carregando

        viewModelScope.launch {
            try {
                val encontrado = buscar()
                if (encontrado == null) {
                    _estado.value = EstadoEdicao.FalhaCarregar(mensagemNaoEncontrado)
                } else {
                    _item.value = encontrado
                    _estado.value = EstadoEdicao.Pronto
                }
            } catch (erro: Exception) {
                _estado.value = EstadoEdicao.FalhaCarregar("Erro ao carregar: ${erro.message}")
            }
        }
    }

    /** Aplica [alteracao] sobre o item carregado e salva; a tela fecha depois. */
    fun salvar(mensagemSucesso: String, alteracao: (T) -> T) {
        executar(mensagemSucesso, fecharTela = true, prefixoErro = "Erro ao salvar") { atual ->
            alteracao(atual).also { gravar(it) }
        }
    }

    /**
     * Roda uma operação que produz o novo item (ex.: subir foto) com o estado
     * de "salvando"/resultado. [operacao] recebe o item atual e devolve o novo.
     */
    protected fun executar(
        mensagemSucesso: String,
        fecharTela: Boolean,
        prefixoErro: String,
        operacao: suspend (T) -> T
    ) {
        val atual = _item.value ?: return
        if (_estado.value == EstadoEdicao.Salvando) return

        _estado.value = EstadoEdicao.Salvando

        viewModelScope.launch {
            try {
                _item.value = operacao(atual)
                _estado.value = EstadoEdicao.Salvo(mensagemSucesso, fecharTela)
            } catch (erro: Exception) {
                _estado.value = EstadoEdicao.FalhaSalvar("$prefixoErro: ${erro.message}")
            }
        }
    }

    /** A tela já tratou o resultado (toast/erro): volta ao estado de edição. */
    fun resultadoTratado() {
        if (_item.value != null) _estado.value = EstadoEdicao.Pronto
    }
}

sealed interface EstadoEdicao {
    data object Carregando : EstadoEdicao
    data object Pronto : EstadoEdicao
    data object Salvando : EstadoEdicao

    /** [fecharTela] = a tela termina depois de salvar (campos); a tela de
     * foto continua aberta. */
    data class Salvo(val mensagem: String, val fecharTela: Boolean) : EstadoEdicao

    /** Falha ao carregar: a tela não tem o que editar e deve fechar. */
    data class FalhaCarregar(val mensagem: String) : EstadoEdicao
    data class FalhaSalvar(val mensagem: String) : EstadoEdicao
}
