package com.example.vaivan.ui.comum.perfil

import android.net.Uri
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.repository.UsuarioRepository

/**
 * ViewModel compartilhado pelas telas de edição de um campo do perfil do
 * usuário (nome, sobrenome, telefone, data de nascimento, endereço, foto).
 * O fluxo (carregar → alterar → salvar) vem de [EdicaoViewModel].
 */
class EditarPerfilViewModel(
    private val repository: UsuarioRepository,
    private val uid: String?
) : EdicaoViewModel<UsuarioEntity>() {

    override val mensagemNaoEncontrado = "Não foi possível carregar seus dados."

    override suspend fun buscar(): UsuarioEntity? =
        uid?.let { repository.consultarUsuario(it) }

    override suspend fun gravar(item: UsuarioEntity) {
        repository.salvarUsuario(item)
    }

    fun atualizarFoto(uri: Uri) =
        executar("Foto atualizada!", fecharTela = false, prefixoErro = "Erro ao atualizar foto") { atual ->
            atual.copy(fotoUrl = repository.atualizarFoto(atual.id, uri))
        }

    fun removerFoto() {
        if (item.value?.fotoUrl == null) return
        executar("Foto removida.", fecharTela = false, prefixoErro = "Erro ao remover foto") { atual ->
            repository.removerFoto(atual.id)
            atual.copy(fotoUrl = null)
        }
    }
}
