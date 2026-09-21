package com.example.vaivan.domain.usecase.rota

import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.remote.routes.PontoRota
import com.example.vaivan.data.repository.RotaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecalcularRotaUseCase(
    private val rotaRepository: RotaRepository,
    private val routesClient: GoogleRoutesClient
) {

    suspend operator fun invoke(
        rotaId: String,
        novaParada: RotaRepository.ParadaCandidata
    ) = withContext(Dispatchers.IO) {

        // -----------------------------------------------------
        // 1. Busca a rota atual
        // -----------------------------------------------------

        val rotaAtual =
            rotaRepository.buscarRotaNoFirestore(
                rotaId
            )


        // -----------------------------------------------------
        // 2. Verifica capacidade
        // -----------------------------------------------------

        if (
            rotaAtual.vagasOcupadas >=
            rotaAtual.capacidadeTotal
        ) {

            throw IllegalStateException(
                "Esta rota já está com todas as vagas ocupadas."
            )
        }


        // -----------------------------------------------------
        // 3. Busca as paradas atuais
        // -----------------------------------------------------

        val paradasExistentes =
            rotaRepository.buscarParadasNoFirestore(
                rotaId
            )


        // -----------------------------------------------------
        // 4. Junta paradas antigas + nova parada
        // -----------------------------------------------------

        val todasParadas =
            paradasExistentes
                .map { parada ->

                    RotaRepository.ParadaCandidata(

                        passageiroId =
                            parada.passageiroId,

                        nomePassageiro =
                            parada.nomePassageiro,

                        localId =
                            parada.localId,

                        nomeLocal =
                            parada.nomeLocal,

                        endereco =
                            parada.endereco,

                        latitude =
                            parada.latitude,

                        longitude =
                            parada.longitude
                    )
                } + novaParada


        // -----------------------------------------------------
        // 5. Monta origem e destino
        // -----------------------------------------------------

        val origem =
            PontoRota(
                id = "origem",

                nome =
                    rotaAtual.origemNome,

                endereco = "",

                latitude =
                    rotaAtual.origemLatitude,

                longitude =
                    rotaAtual.origemLongitude
            )

        val destino =
            PontoRota(
                id = "destino",

                nome =
                    rotaAtual.destinoNome,

                endereco =
                    rotaAtual.destinoEndereco,

                latitude =
                    rotaAtual.destinoLatitude,

                longitude =
                    rotaAtual.destinoLongitude
            )


        // -----------------------------------------------------
        // 6. Converte as paradas para pontos da API
        // -----------------------------------------------------

        val pontosIntermediarios =
            todasParadas.map { parada ->

                PontoRota(

                    id =
                        parada.passageiroId,

                    nome =
                        parada.nomePassageiro,

                    endereco =
                        parada.endereco,

                    latitude =
                        parada.latitude,

                    longitude =
                        parada.longitude
                )
            }


        // -----------------------------------------------------
        // 7. Calcula a nova rota
        // -----------------------------------------------------

        val resultado =
            routesClient.calcularMelhorRota(

                origem = origem,

                destino = destino,

                paradas = pontosIntermediarios
            )


        // -----------------------------------------------------
        // 8. Aplica a ordem retornada pelo Google
        // -----------------------------------------------------

        val paradasOrdenadas =

            if (
                resultado.ordemOtimizada.isNotEmpty() &&
                resultado.ordemOtimizada.all {
                    it in todasParadas.indices
                }
            ) {

                resultado.ordemOtimizada.map { indice ->

                    todasParadas[indice]
                }

            } else {

                todasParadas
            }


        // -----------------------------------------------------
        // 9. Atualiza a rota
        // -----------------------------------------------------

        val agora =
            System.currentTimeMillis()

        val rotaAtualizada =
            rotaAtual.copy(

                vagasOcupadas =
                    rotaAtual.vagasOcupadas + 1,

                distanciaMetros =
                    resultado.distanciaMetros,

                duracaoSegundos =
                    resultado.duracaoSegundos,

                duracaoSemTrafegoSegundos =
                    resultado.duracaoSemTrafegoSegundos,

                polylineEncoded =
                    resultado.polylineEncoded,

                calculadoEm =
                    agora,

                lastUpdated =
                    agora
            )


        // -----------------------------------------------------
        // 10. Cria as entidades das paradas
        // -----------------------------------------------------

        var acumuladoSegundos =
            0

        val paradasEntities =
            paradasOrdenadas.mapIndexed { index, parada ->

                val perna =
                    resultado.pernas
                        .getOrNull(index)

                val duracaoTrecho =
                    perna?.duracaoSegundos ?: 0

                val distanciaTrecho =
                    perna?.distanciaMetros ?: 0

                acumuladoSegundos +=
                    duracaoTrecho

                val idExistente =
                    paradasExistentes
                        .firstOrNull {
                            it.passageiroId ==
                                    parada.passageiroId
                        }
                        ?.id

                ParadaRotaEntity(

                    id =
                        idExistente
                            ?: rotaRepository
                                .novoIdParada(),

                    rotaId =
                        rotaId,

                    passageiroId =
                        parada.passageiroId,

                    localId =
                        parada.localId,

                    nomePassageiro =
                        parada.nomePassageiro,

                    nomeLocal =
                        parada.nomeLocal,

                    endereco =
                        parada.endereco,

                    latitude =
                        parada.latitude,

                    longitude =
                        parada.longitude,

                    ordem =
                        index,

                    distanciaTrechoMetros =
                        distanciaTrecho,

                    duracaoTrechoSegundos =
                        duracaoTrecho,

                    horarioEstimadoMinutos =
                        acumuladoSegundos / 60,

                    lastUpdated =
                        agora
                )
            }


        // -----------------------------------------------------
        // 11. Salva tudo
        // -----------------------------------------------------

        rotaRepository.salvarRotaRecalculada(
            rotaAtualizada
        )

        rotaRepository.salvarParadasRecalculadas(
            rotaId = rotaId,
            paradas = paradasEntities
        )


        rotaAtualizada
    }
}