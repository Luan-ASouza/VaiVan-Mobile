package com.example.vaivan.domain.usecase.solicitacao

import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.domain.usecase.rota.RecalcularRotaUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aceita a solicitação de um responsável:
 *  1. confere o plano do motorista (inadimplente não adiciona passageiros);
 *  2. recalcula a rota com o novo aluno (sem gravar nada ainda);
 *  3. grava TUDO de uma vez, numa transação: rota, paradas e solicitação "ACEITA".
 *     Se a solicitação já foi respondida, se não há mais vaga ou se a rota mudou no meio
 *     do caminho, nada é gravado e o motorista recebe uma mensagem clara.
 */
class AceitarSolicitacaoUseCase(
    private val solicitacaoInclusaoRepository: SolicitacaoInclusaoRepository,
    private val recalcularRotaUseCase: RecalcularRotaUseCase,
    private val rotaRepository: RotaRepository,
    private val assinaturaRepository: AssinaturaRepository = AssinaturaRepository()
) {

    suspend operator fun invoke(
        solicitacao: SolicitacaoInclusaoEntity
    ) = withContext(Dispatchers.IO) {

        // 1. Plano
        assinaturaRepository.exigirPodeAceitarPassageiro(solicitacao.motoristaId)

        // 2. Cálculo (não grava)
        val calculo = recalcularRotaUseCase.calcular(
            rotaId = solicitacao.rotaId,
            novaParada = RotaRepository.ParadaCandidata(
                passageiroId = solicitacao.passageiroId,
                nomePassageiro = solicitacao.nomePassageiro,
                localId = solicitacao.localEmbarqueId,
                nomeLocal = solicitacao.nomeLocalEmbarque,
                endereco = solicitacao.enderecoEmbarque,
                latitude = solicitacao.latitudeEmbarque,
                longitude = solicitacao.longitudeEmbarque
            )
        )

        // 3. Gravação atômica
        val respondidaEm = System.currentTimeMillis()

        rotaRepository.confirmarInclusaoNaRota(
            solicitacaoId = solicitacao.id,
            rotaAtualizada = calculo.rota,
            paradas = calculo.paradas,
            baseCalculadoEm = calculo.baseCalculadoEm,
            respondidaEm = respondidaEm
        )

        // 4. Cópia local da solicitação
        solicitacaoInclusaoRepository.registrarAceitaLocalmente(solicitacao, respondidaEm)
    }
}