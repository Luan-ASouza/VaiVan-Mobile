package com.example.vaivan.domain.usecase.rota

import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.data.models.RotasDoResponsavel
import kotlinx.coroutines.flow.first

class BuscarRotasDoResponsavelUseCase(
    private val passageiroRepository: PassageiroRepository,
    private val rotaRepository: RotaRepository,
    private val usuarioRepository: UsuarioRepository,
    private val veiculoRepository: VeiculoRepository
) {

    suspend operator fun invoke(
        responsavelId: String
    ): List<RotasDoResponsavel> {

        // 1. Pega somente os passageiros desse responsável.
        val passageiros: List<PassageiroEntity> =
            passageiroRepository
                .observarPassageirosDoResponsavel(
                    responsavelId
                )
                .first()

        // 2. Para cada passageiro, procura as paradas
        //    em que ele está associado a uma rota.
        val paradasDoResponsavel =
            passageiros.flatMap { passageiro ->

                rotaRepository.consultarParadasPorPassageiro(
                    passageiro.id
                )
            }

        // 3. Agrupa as paradas pelo ID da rota.
        val paradasPorRota =
            paradasDoResponsavel.groupBy {
                it.rotaId
            }

        // 4. Monta os dados de cada rota.
        return paradasPorRota.mapNotNull { (rotaId, paradas) ->

            val rota =
                try {
                    rotaRepository.buscarRotaNoFirestore(
                        rotaId
                    )
                } catch (e: Exception) {
                    null
                }

            if (rota == null) {
                return@mapNotNull null
            }

            // Motorista da rota.
            val motorista =
                usuarioRepository
                    .observarUsuario(
                        rota.motoristaId
                    )
                    .first()

            // Veículo da rota.
            val veiculo =
                rota.veiculoId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { veiculoId ->

                        veiculoRepository
                            .consultarVeiculo(
                                veiculoId
                            )
                    }

            RotasDoResponsavel(
                rota = rota,
                paradas = paradas,
                motorista = motorista,
                veiculo = veiculo
            )
        }
    }
}