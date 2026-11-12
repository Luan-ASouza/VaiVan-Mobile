package com.example.vaivan.ui.responsavel.passageiros.perfil

import android.content.Context
import android.net.Uri
import com.example.vaivan.data.local.VaivanDatabase
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.ui.comum.perfil.EdicaoViewModel
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel do perfil do passageiro e das telas de edição de cada campo
 * (nome, nascimento, necessidades, observações, foto). O fluxo
 * carregar → alterar → salvar vem de [EdicaoViewModel].
 */
class EditarPassageiroViewModel(
    private val repository: PassageiroRepository,
    private val passageiroId: String
) : EdicaoViewModel<PassageiroEntity>() {

    companion object {
        /** DAOs de paradas/solicitações: ao renomear o passageiro, as cópias
         * do nome no cache local também são atualizadas na hora. */
        fun criarRepository(context: Context): PassageiroRepository {
            val db = VaivanDatabase.getInstance(context)
            return PassageiroRepository(
                passageiroDao = db.passageiroDao(),
                paradaRotaDao = db.paradaRotaDao(),
                solicitacaoInclusaoDao = db.solicitacaoInclusaoDao()
            )
        }
    }

    override val mensagemNaoEncontrado = "Passageiro não encontrado."

    /** Pro perfil: acompanha o cache (Room), que o salvar já atualiza. */
    val passageiro: Flow<PassageiroEntity?> = repository.observarPassageiro(passageiroId)

    override suspend fun buscar(): PassageiroEntity? =
        passageiroId.takeIf { it.isNotBlank() }?.let { repository.consultarPassageiro(it) }

    override suspend fun gravar(item: PassageiroEntity) {
        repository.salvarPassageiro(item)
    }

    fun atualizarFoto(uri: Uri) =
        executar("Foto atualizada!", fecharTela = false, prefixoErro = "Erro ao atualizar foto") { atual ->
            atual.copy(fotoUrl = repository.atualizarFoto(atual, uri))
        }

    fun removerFoto() {
        if (item.value?.fotoUrl == null) return
        executar("Foto removida.", fecharTela = false, prefixoErro = "Erro ao remover foto") { atual ->
            repository.removerFoto(atual)
            atual.copy(fotoUrl = null)
        }
    }

    /** Exclusão em cascata já fica no PassageiroRepository. */
    suspend fun excluir() {
        repository.excluirPassageiro(passageiroId)
    }
}
