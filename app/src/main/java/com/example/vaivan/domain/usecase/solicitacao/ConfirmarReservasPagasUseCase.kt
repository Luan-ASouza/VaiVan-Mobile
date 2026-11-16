package com.example.vaivan.domain.usecase.solicitacao

import android.util.Log
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.repository.MensalidadeRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import com.example.vaivan.domain.usecase.rota.RecalcularRotaUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Roda no app do MOTORISTA (ao abrir a tela da rota e antes de iniciar uma viagem).
 * Para cada vaga reservada (AGUARDANDO_PAGAMENTO):
 *
 *  - 1ª mensalidade PAGA  -> coloca o aluno no trajeto (recalcula a rota) e o pedido
 *                            vira ACEITA; o contrato passa a valer mês a mês;
 *  - prazo de 3 dias vencido sem pagamento -> a reserva expira e a vaga volta a ficar livre;
 *  - ainda no prazo        -> nada muda.
 *
 * Por que no app do motorista: incluir alguém no trajeto grava a rota dele, e só ele
 * tem permissão para isso. Chamar antes de iniciar a viagem garante que quem pagou
 * nunca fique de fora. Também faz o cálculo de trajeto (API paga do Google) acontecer
 * só para quem de fato pagou.
 *
 * Devolve quantos alunos entraram na rota.
 */
class ConfirmarReservasPagasUseCase(
    private val solicitacaoRepository: SolicitacaoInclusaoRepository,
    private val recalcularRotaUseCase: RecalcularRotaUseCase,
    private val rotaRepository: RotaRepository,
    private val cancelarSolicitacaoUseCase: CancelarSolicitacaoUseCase,
    private val mensalidadeRepository: MensalidadeRepository = MensalidadeRepository()
) {

    suspend operator fun invoke(motoristaId: String): Int = withContext(Dispatchers.IO) {
        var ativadas = 0

        solicitacaoRepository.consultarReservasDoMotorista(motoristaId).forEach { reserva ->
            try {
                // Se o contrato não foi criado no aceite (ex.: sem internet), cria agora
                mensalidadeRepository.garantirContrato(reserva.id, inicioEm = reserva.respondidoEm ?: System.currentTimeMillis())

                when {
                    mensalidadeRepository.primeiraMensalidadePaga(reserva.id, motoristaId) -> {
                        ativar(reserva)
                        ativadas++
                    }
                    else -> cancelarSolicitacaoUseCase.expirarReserva(reserva.id)
                }
            } catch (e: Exception) {
                // Uma reserva com problema não impede as outras (tenta de novo na próxima vez)
                Log.w(TAG, "Não foi possível processar a reserva ${reserva.id}", e)
            }
        }
        ativadas
    }

    /** Coloca o aluno no trajeto, usando a vaga que já estava reservada. */
    private suspend fun ativar(reserva: SolicitacaoInclusaoEntity) {
        val calculo = recalcularRotaUseCase.calcular(
            rotaId = reserva.rotaId,
            novaParada = RotaRepository.ParadaCandidata(
                passageiroId = reserva.passageiroId,
                nomePassageiro = reserva.nomePassageiro,
                localId = reserva.localEmbarqueId,
                nomeLocal = reserva.nomeLocalEmbarque,
                endereco = reserva.enderecoEmbarque,
                latitude = reserva.latitudeEmbarque,
                longitude = reserva.longitudeEmbarque
            ),
            vagaJaReservada = true
        )

        val ativadaEm = System.currentTimeMillis()

        rotaRepository.confirmarInclusaoNaRota(
            solicitacaoId = reserva.id,
            rotaAtualizada = calculo.rota,
            paradas = calculo.paradas,
            baseCalculadoEm = calculo.baseCalculadoEm,
            respondidaEm = ativadaEm,
            vagaJaReservada = true
        )

        mensalidadeRepository.ativarContrato(reserva.id)

        solicitacaoRepository.registrarAceitaLocalmente(
            reserva,
            reserva.respondidoEm ?: ativadaEm,
            status = SolicitacaoInclusaoRepository.STATUS_ACEITA
        )
    }

    private companion object {
        const val TAG = "ConfirmarReservas"
    }
}
