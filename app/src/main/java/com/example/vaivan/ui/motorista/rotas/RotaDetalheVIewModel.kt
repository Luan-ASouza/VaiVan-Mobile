package com.example.vaivan.ui.motorista.rotas

import androidx.lifecycle.ViewModel
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.domain.usecase.solicitacao.AceitarSolicitacaoUseCase
import kotlinx.coroutines.flow.Flow

class RotaDetalheViewModel(
    private val rotaRepository: RotaRepository,
    private val solicitacaoRepository: SolicitacaoInclusaoRepository,
    private val aceitarSolicitacaoUseCase: AceitarSolicitacaoUseCase
) : ViewModel() {

    fun observarRota(
        rotaId: String
    ): Flow<RotaEntity?> {

        return rotaRepository.observarRotaPorId(
            rotaId
        )
    }

    fun observarParadas(
        rotaId: String
    ) =
        rotaRepository.observarParadas(
            rotaId
        )

    fun observarSolicitacoes(
        motoristaId: String
    ) =
        solicitacaoRepository.observarPendentesPorMotorista(
            motoristaId
        )

    suspend fun aceitarSolicitacao(
        solicitacao: SolicitacaoInclusaoEntity
    ) {

        aceitarSolicitacaoUseCase(
            solicitacao
        )
    }

    suspend fun recusarSolicitacao(
        solicitacao: SolicitacaoInclusaoEntity
    ) {

        solicitacaoRepository.marcarComoRecusada(
            solicitacao
        )
    }
}