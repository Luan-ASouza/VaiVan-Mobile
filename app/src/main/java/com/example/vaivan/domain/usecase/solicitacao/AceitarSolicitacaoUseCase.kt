package com.example.vaivan.domain.usecase.solicitacao

import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.domain.usecase.rota.RecalcularRotaUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AceitarSolicitacaoUseCase(
    private val solicitacaoInclusaoRepository: SolicitacaoInclusaoRepository,
    private val recalcularRotaUseCase: RecalcularRotaUseCase
) {

    suspend operator fun invoke(
        solicitacao: SolicitacaoInclusaoEntity
    ) = withContext(Dispatchers.IO) {

        recalcularRotaUseCase(
            rotaId = solicitacao.rotaId,

            novaParada =
                RotaRepository.ParadaCandidata(
                    passageiroId =
                        solicitacao.passageiroId,

                    nomePassageiro =
                        solicitacao.nomePassageiro,

                    localId =
                        solicitacao.localEmbarqueId,

                    nomeLocal =
                        solicitacao.nomeLocalEmbarque,

                    endereco =
                        solicitacao.enderecoEmbarque,

                    latitude =
                        solicitacao.latitudeEmbarque,

                    longitude =
                        solicitacao.longitudeEmbarque
                )
        )

        solicitacaoInclusaoRepository
            .marcarComoAceita(
                solicitacao
            )
    }
}