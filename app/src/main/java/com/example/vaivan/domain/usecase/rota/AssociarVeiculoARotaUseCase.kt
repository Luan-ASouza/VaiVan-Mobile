package com.example.vaivan.domain.usecase.rota

import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.VeiculoNaoAprovadoException
import com.example.vaivan.data.repository.VeiculoRepository

/**
 * Associa um veículo a uma rota existente. Só veículo APROVADO e do próprio
 * motorista ([VeiculoRepository.exigirVeiculoAprovado], lança
 * [VeiculoNaoAprovadoException]); null tira o veículo da rota.
 * A regra do Firestore em /rotas repete a mesma trava.
 */
class AssociarVeiculoARotaUseCase(
    private val rotaRepository: RotaRepository,
    private val veiculoRepository: VeiculoRepository
) {

    suspend operator fun invoke(rotaId: String, motoristaId: String, veiculoId: String?) {
        val id = veiculoId?.takeIf { it.isNotBlank() }
        if (id != null) {
            veiculoRepository.exigirVeiculoAprovado(id, motoristaId)
        }
        rotaRepository.salvarVeiculoDaRota(rotaId, id)
    }
}
