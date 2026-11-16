package com.example.vaivan.domain.usecase.rota

import android.util.Log
import com.example.vaivan.data.repository.RotaRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Recalcula o trajeto de uma rota só com os alunos que estão nela agora.
 *
 * Por que existe: quando o responsável cancela a vaga do filho, a parada
 * dele é apagada, mas o desenho do trajeto (polyline, distância, tempo,
 * horários) continuava passando pela casa dele — o responsável não pode
 * gravar o trajeto da rota (regras do Firestore). O cancelamento deixa o
 * aviso [RotaRepository.CAMPO_SAIDA_PENDENTE] na rota e o app do motorista
 * chama [atualizarPendentes] ao abrir as rotas / antes de iniciar viagem.
 *
 * [invoke] também serve para forçar o recálculo (botão "Recalcular trajeto"
 * e depois de trocar o ponto ou a escola de uma rota que já tem alunos).
 */
class AtualizarTrajetoDaRotaUseCase(
    private val rotaRepository: RotaRepository,
    private val recalcularRotaUseCase: RecalcularRotaUseCase
) {

    /** Recalcula e grava. Devolve false se não deu pra gravar desta vez
     * (a rota mudou durante o cálculo, ou não há como traçar). */
    suspend operator fun invoke(rotaId: String): Boolean {
        val resultado = recalcularRotaUseCase.calcularComParadasAtuais(rotaId) ?: return false
        return rotaRepository.salvarTrajetoAtualizado(
            rota = resultado.rota,
            paradas = resultado.paradas,
            baseCalculadoEm = resultado.baseCalculadoEm
        )
    }

    /**
     * Recalcula as rotas do motorista que perderam um aluno desde o último
     * cálculo. Nunca falha: o que der errado (ex.: sem internet) fica para a
     * próxima vez, porque o aviso só é apagado quando o trajeto é gravado.
     * Devolve quantas rotas foram atualizadas.
     */
    suspend fun atualizarPendentes(motoristaId: String): Int {
        val rotas = try {
            rotaRepository.consultarRotasComTrajetoDesatualizado(motoristaId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao procurar rotas com trajeto desatualizado", e)
            return 0
        }

        return rotas.count { rotaId ->
            try {
                invoke(rotaId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao recalcular o trajeto da rota $rotaId", e)
                false
            }
        }
    }

    private companion object {
        const val TAG = "AtualizarTrajeto"
    }
}
