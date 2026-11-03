package com.example.vaivan.domain.usecase.viagem

import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.ViagemRepository

/**
 * Reúne o que uma viagem precisa para começar: busca a rota e os passageiros
 * com parada confirmada (ParadaRotaEntity) direto no Firebase — não confia no
 * cache do Room para esse momento, já que precisa do estado mais atual antes
 * de criar a viagem — e delega a criação ao ViagemRepository.
 */
class IniciarViagemUseCase(
    private val rotaRepository: RotaRepository,
    private val passageiroRepository: PassageiroRepository,
    private val viagemRepository: ViagemRepository
) {
    suspend operator fun invoke(
        rotaId: String,
        motoristaId: String,
        direcao: String
    ): ViagemEntity {

        // Se o motorista já tem uma viagem em andamento/pausada (ex.: a tela de
        // navegação foi fechada e reaberta), retoma ela em vez de começar do
        // zero — senão o progresso de embarque já confirmado se perde.
        viagemRepository.consultarViagemAtivaDoMotorista(motoristaId)?.let { ativa ->
            return ativa
        }

        val rota = rotaRepository.consultarRota(rotaId)
            ?: throw IllegalStateException("Rota não encontrada.")

        if (rota.motoristaId != motoristaId) {
            throw IllegalStateException("Esta rota não pertence a este motorista.")
        }

        val paradas = rotaRepository.consultarParadas(rotaId)

        if (paradas.isEmpty()) {
            throw IllegalStateException("Nenhum passageiro confirmado nesta rota ainda.")
        }

        // Denormaliza o responsável de cada passageiro, para as regras do
        // Firestore conseguirem restringir a leitura sem consulta cruzada.
        val responsavelPorPassageiro = paradas
            .map { it.passageiroId }
            .distinct()
            .mapNotNull { passageiroId ->
                val responsavelId = passageiroRepository.consultarPassageiro(passageiroId)?.responsavelId
                if (responsavelId.isNullOrBlank()) null else passageiroId to responsavelId
            }
            .toMap()

        return viagemRepository.iniciarViagem(
            rota = rota,
            paradasRota = paradas,
            motoristaId = motoristaId,
            veiculoId = rota.veiculoId,
            direcao = direcao,
            responsavelPorPassageiro = responsavelPorPassageiro
        )
    }
}
