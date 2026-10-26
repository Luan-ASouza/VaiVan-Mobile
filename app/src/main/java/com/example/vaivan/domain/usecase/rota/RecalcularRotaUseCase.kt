package com.example.vaivan.domain.usecase.rota

import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.remote.routes.GoogleRoutesClient
import com.example.vaivan.data.remote.routes.PontoRota
import com.example.vaivan.data.repository.RotaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recalcula a rota com mais um aluno. Este caso de uso apenas CALCULA: quem grava
 * (de forma atômica, junto com a aceitação da solicitação) é o RotaRepository.
 */
class RecalcularRotaUseCase(
    private val rotaRepository: RotaRepository,
    private val routesClient: GoogleRoutesClient
) {

    /**
     * @param baseCalculadoEm valor de "calculadoEm" da rota no momento da leitura; a gravação só
     *        é aceita se a rota não tiver mudado desde então.
     */
    class Resultado(
        val rota: RotaEntity,
        val paradas: List<ParadaRotaEntity>,
        val baseCalculadoEm: Long
    )

    suspend fun calcular(
        rotaId: String,
        novaParada: RotaRepository.ParadaCandidata
    ): Resultado = withContext(Dispatchers.IO) {

        // 1. Rota atual
        val rotaAtual = rotaRepository.buscarRotaNoFirestore(rotaId)

        // 2. Capacidade
        if (rotaAtual.vagasOcupadas >= rotaAtual.capacidadeTotal) {
            throw IllegalStateException("Esta rota já está com todas as vagas ocupadas.")
        }

        // 3. Paradas atuais (na ordem da rota)
        val paradasExistentes = rotaRepository
            .buscarParadasNoFirestore(rotaId)
            .sortedBy { it.ordem }

        if (paradasExistentes.any { it.passageiroId == novaParada.passageiroId }) {
            throw IllegalStateException("Este passageiro já está nesta rota.")
        }

        // 4. Todas as paradas, antigas + nova
        val todasParadas = paradasExistentes.map { parada ->
            RotaRepository.ParadaCandidata(
                passageiroId = parada.passageiroId,
                nomePassageiro = parada.nomePassageiro,
                localId = parada.localId,
                nomeLocal = parada.nomeLocal,
                endereco = parada.endereco,
                latitude = parada.latitude,
                longitude = parada.longitude
            )
        } + novaParada

        // 5. Pontos de partida e chegada.
        //    IDA / IDA_E_VOLTA: ponto de partida do motorista -> alunos -> escola
        //    VOLTA:             escola -> alunos -> ponto de partida do motorista
        //    Rotas antigas, sem ponto de partida gravado, usam o primeiro aluno como saída.
        val temPontoDePartida =
            rotaAtual.origemLatitude != 0.0 || rotaAtual.origemLongitude != 0.0

        val pontoDePartidaDoMotorista = PontoRota(
            id = "origem",
            nome = rotaAtual.origemNome,
            endereco = rotaAtual.origemEndereco,
            latitude = rotaAtual.origemLatitude,
            longitude = rotaAtual.origemLongitude
        )

        val escola = PontoRota(
            id = "destino",
            nome = rotaAtual.destinoNome,
            endereco = rotaAtual.destinoEndereco,
            latitude = rotaAtual.destinoLatitude,
            longitude = rotaAtual.destinoLongitude
        )

        val ehVolta = rotaAtual.tipoTrajeto == "VOLTA"

        val primeiraParadaEhASaida = !temPontoDePartida

        val origem: PontoRota
        val destino: PontoRota
        val candidatasIntermediarias: List<RotaRepository.ParadaCandidata>

        if (primeiraParadaEhASaida) {
            val primeira = todasParadas.first()
            origem = PontoRota(
                id = primeira.passageiroId,
                nome = primeira.nomePassageiro,
                endereco = primeira.endereco,
                latitude = primeira.latitude,
                longitude = primeira.longitude
            )
            destino = escola
            candidatasIntermediarias = todasParadas.drop(1)
        } else if (ehVolta) {
            origem = escola
            destino = pontoDePartidaDoMotorista
            candidatasIntermediarias = todasParadas
        } else {
            origem = pontoDePartidaDoMotorista
            destino = escola
            candidatasIntermediarias = todasParadas
        }

        // 6. Pontos intermediários para a API
        val pontosIntermediarios = candidatasIntermediarias.map { parada ->
            PontoRota(
                id = parada.passageiroId,
                nome = parada.nomePassageiro,
                endereco = parada.endereco,
                latitude = parada.latitude,
                longitude = parada.longitude
            )
        }

        // 7. Calcula
        val resultado = routesClient.calcularMelhorRota(
            origem = origem,
            destino = destino,
            paradas = pontosIntermediarios
        )

        // 8. Aplica a ordem otimizada devolvida pela Google
        val ordemValida =
            resultado.ordemOtimizada.size == candidatasIntermediarias.size &&
                    resultado.ordemOtimizada.all { it in candidatasIntermediarias.indices }

        val intermediariasEmOrdem =
            if (ordemValida && candidatasIntermediarias.isNotEmpty()) {
                resultado.ordemOtimizada.map { candidatasIntermediarias[it] }
            } else {
                candidatasIntermediarias
            }

        val paradasOrdenadas =
            if (primeiraParadaEhASaida) {
                listOf(todasParadas.first()) + intermediariasEmOrdem
            } else {
                intermediariasEmOrdem
            }

        // 9. Horários de embarque (minutos desde a saída)
        val horarios = HorariosDaRota.minutosPorParada(
            duracoesPernasSegundos = resultado.pernas.map { it.duracaoSegundos },
            quantidadeParadas = paradasOrdenadas.size,
            primeiraParadaEhASaida = primeiraParadaEhASaida
        )

        val agora = System.currentTimeMillis()

        val rotaAtualizada = rotaAtual.copy(
            vagasOcupadas = rotaAtual.vagasOcupadas + 1,
            distanciaMetros = resultado.distanciaMetros,
            duracaoSegundos = resultado.duracaoSegundos,
            duracaoSemTrafegoSegundos = resultado.duracaoSemTrafegoSegundos,
            polylineEncoded = resultado.polylineEncoded,
            calculadoEm = agora,
            lastUpdated = agora
        )

        // 10. Entidades das paradas
        val paradasEntities = paradasOrdenadas.mapIndexed { index, parada ->

            val indicePerna = if (primeiraParadaEhASaida) index - 1 else index
            val perna = resultado.pernas.getOrNull(indicePerna)

            val idExistente = paradasExistentes.firstOrNull {
                it.passageiroId == parada.passageiroId
            }?.id

            ParadaRotaEntity(
                id = idExistente ?: rotaRepository.novoIdParada(),
                rotaId = rotaId,
                passageiroId = parada.passageiroId,
                localId = parada.localId,
                nomePassageiro = parada.nomePassageiro,
                nomeLocal = parada.nomeLocal,
                endereco = parada.endereco,
                latitude = parada.latitude,
                longitude = parada.longitude,
                ordem = index,
                distanciaTrechoMetros = perna?.distanciaMetros ?: 0,
                duracaoTrechoSegundos = perna?.duracaoSegundos ?: 0,
                horarioEstimadoMinutos = horarios[index],
                lastUpdated = agora
            )
        }

        Resultado(
            rota = rotaAtualizada,
            paradas = paradasEntities,
            baseCalculadoEm = rotaAtual.calculadoEm
        )
    }
}