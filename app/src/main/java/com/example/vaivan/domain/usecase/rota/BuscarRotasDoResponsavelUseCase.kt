package com.example.vaivan.domain.usecase.rota

import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.UsuarioRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.data.models.RotasDoResponsavel
import kotlinx.coroutines.flow.first
import android.util.Log

class BuscarRotasDoResponsavelUseCase(
    private val passageiroRepository: PassageiroRepository,
    private val rotaRepository: RotaRepository,
    private val usuarioRepository: UsuarioRepository,
    private val veiculoRepository: VeiculoRepository
) {

    /** Dados da rota que são os mesmos para todos os filhos que estão nela. */
    private data class DadosDaRota(
        val rota: RotaEntity,
        val motorista: UsuarioEntity?,
        val veiculo: VeiculoEntity?
    )

    /**
     * Devolve UM item por filho e por viagem (uma parada = um filho em uma rota).
     * Se dois filhos estão na mesma van, aparecem dois itens.
     */
    suspend operator fun invoke(
        responsavelId: String
    ): List<RotasDoResponsavel> {

        // 1. Passageiros desse responsável.
        val passageiros: List<PassageiroEntity> =
            passageiroRepository
                .observarPassageirosDoResponsavel(
                    responsavelId
                )
                .first()

        // 2. Paradas (vínculos aluno-rota) de cada passageiro.
        val paradasDoResponsavel =
            passageiros.flatMap { passageiro ->

                rotaRepository.consultarParadasPorPassageiro(
                    passageiro.id
                )
            }

        // 3. Paradas agrupadas por rota (continua disponível em "paradas").
        val paradasPorRota =
            paradasDoResponsavel.groupBy {
                it.rotaId
            }

        // 4. Rota, motorista e veículo são buscados uma vez por rota.
        val cache = mutableMapOf<String, DadosDaRota?>()

        val itens = paradasDoResponsavel.mapNotNull { parada ->

            val dados =
                if (cache.containsKey(parada.rotaId)) {
                    cache[parada.rotaId]
                } else {
                    buscarDadosDaRota(parada.rotaId)
                        .also { cache[parada.rotaId] = it }
                }

            if (dados == null) {
                return@mapNotNull null
            }

            RotasDoResponsavel(
                rota = dados.rota,
                paradas = paradasPorRota[parada.rotaId].orEmpty(),
                motorista = dados.motorista,
                veiculo = dados.veiculo,
                parada = parada
            )
        }

        // Ordem estável: por nome do aluno e depois pelo nome da rota
        return itens.sortedWith(
            compareBy(
                { it.parada?.nomePassageiro.orEmpty().lowercase() },
                { it.rota.nome.lowercase() }
            )
        )


    }

    private suspend fun buscarDadosDaRota(
        rotaId: String
    ): DadosDaRota? {

        val rota =
            try {
                rotaRepository.buscarRotaNoFirestore(
                    rotaId
                )
            } catch (e: Exception) {
                Log.d("ROTAS_RESP", "rota $rotaId NÃO existe: ${e.message}")
                return null
            }

        Log.d("ROTAS_RESP", "rota $rotaId existe: ${rota.nome}")

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

        return DadosDaRota(
            rota = rota,
            motorista = motorista,
            veiculo = veiculo
        )
    }
}