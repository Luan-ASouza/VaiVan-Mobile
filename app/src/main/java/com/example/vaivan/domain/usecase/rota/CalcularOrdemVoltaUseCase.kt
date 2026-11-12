package com.example.vaivan.domain.usecase.rota

import android.util.Log
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.remote.routes.PontoRota
import kotlin.coroutines.cancellation.CancellationException

/**
 * Rota "ida e volta" tem uma ordem de paradas pra cada sentido: a da ida
 * (ponto de partida -> alunos -> escola, em [ParadaRotaEntity.ordem]) e a da
 * volta (escola -> alunos -> ponto de partida, em [ParadaRotaEntity.ordemVolta]).
 * Entregar na mesma sequência da busca quase nunca é o caminho mais curto
 * saindo da escola, então a volta tem sua própria otimização.
 *
 * Só CALCULA — quem grava é quem chama (aceite da solicitação ou, para rotas
 * antigas, o início da primeira volta). Devolve null se não der pra calcular
 * (sem rede, paradas demais): quem chama mantém a volta "não calculada" e
 * tenta de novo depois.
 *
 * Fim da volta: o ponto de partida do motorista, se a rota tiver um; senão, o
 * primeiro aluno da ida (em geral o mais distante da escola).
 */
class CalcularOrdemVoltaUseCase(
    private val routesClient: GoogleRoutesClient
) {

    suspend operator fun invoke(
        rota: RotaEntity,
        paradas: List<ParadaRotaEntity>
    ): List<ParadaRotaEntity>? {
        if (paradas.isEmpty()) return paradas

        val emOrdemDaIda = paradas.sortedBy { it.ordem }
        val temPontoDePartida = rota.origemLatitude != 0.0 || rota.origemLongitude != 0.0

        val ultima: ParadaRotaEntity?
        val intermediarias: List<ParadaRotaEntity>
        val destino: PontoRota
        if (temPontoDePartida) {
            ultima = null
            intermediarias = emOrdemDaIda
            destino = PontoRota(
                id = "origem",
                nome = rota.origemNome,
                endereco = rota.origemEndereco,
                latitude = rota.origemLatitude,
                longitude = rota.origemLongitude
            )
        } else {
            ultima = emOrdemDaIda.first()
            intermediarias = emOrdemDaIda.drop(1)
            destino = ultima.comoPonto()
        }

        if (intermediarias.size > GoogleRoutesClient.MAX_PARADAS_OTIMIZAVEIS) return null

        val escola = PontoRota(
            id = "escola",
            nome = rota.escolaNome.ifBlank { rota.destinoNome },
            endereco = rota.destinoEndereco,
            latitude = rota.destinoLatitude,
            longitude = rota.destinoLongitude
        )

        return try {
            val resultado = routesClient.calcularMelhorRota(
                origem = escola,
                destino = destino,
                paradas = intermediarias.map { it.comoPonto() }
            )

            val ordemValida = resultado.ordemOtimizada.size == intermediarias.size &&
                resultado.ordemOtimizada.toSet() == intermediarias.indices.toSet()
            if (!ordemValida) return null

            val ordenadas = resultado.ordemOtimizada.map { intermediarias[it] } + listOfNotNull(ultima)
            val horarios = HorariosDaRota.minutosPorParada(
                duracoesPernasSegundos = resultado.pernas.map { it.duracaoSegundos },
                quantidadeParadas = ordenadas.size
            )

            ordenadas.mapIndexed { index, parada ->
                parada.copy(
                    ordemVolta = index,
                    horarioEstimadoVoltaMinutos = horarios.getOrElse(index) { 0 }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível calcular a volta da rota ${rota.id}", e)
            null
        }
    }

    private fun ParadaRotaEntity.comoPonto() = PontoRota(
        id = passageiroId,
        nome = nomePassageiro,
        endereco = endereco,
        latitude = latitude,
        longitude = longitude
    )

    private companion object {
        const val TAG = "CalcularOrdemVolta"
    }
}
