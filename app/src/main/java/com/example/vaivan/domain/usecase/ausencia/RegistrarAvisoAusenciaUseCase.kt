package com.example.vaivan.domain.usecase.ausencia

import android.util.Log
import com.example.vaivan.data.local.entities.AvisoAusenciaEntity
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.ViagemRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Salva o aviso de ausência e, se já existir uma viagem em andamento/pausada
 * pra alguma das direções afetadas, aplica a falta nela também — o status do
 * passageiro já fica resolvido sem o motorista precisar fazer nada (nunca
 * altera a navegação/waypoints ao vivo, só o status da parada). Se a parada
 * já tiver sido resolvida pelo motorista (já passou do ponto), não há nada
 * pra mudar, mas a direção é consumida do mesmo jeito — não faz mais sentido
 * pra essa viagem.
 */
class RegistrarAvisoAusenciaUseCase(
    private val avisoAusenciaRepository: AvisoAusenciaRepository,
    private val viagemRepository: ViagemRepository
) {
    suspend operator fun invoke(aviso: AvisoAusenciaEntity, rotaIdaEVolta: Boolean) {
        // salvarAviso devolve a cópia com o id real gerado pelo Firestore —
        // usar o `aviso` original (id = "") nas chamadas abaixo quebra
        // marcarConsumido (precisa do id pra achar o documento).
        val salvo = avisoAusenciaRepository.salvarAviso(aviso)

        aplicarSeHouverViagemAtiva(salvo, ViagemRepository.DIRECAO_IDA, salvo.pendenteIda)
        if (rotaIdaEVolta) {
            aplicarSeHouverViagemAtiva(salvo, ViagemRepository.DIRECAO_VOLTA, salvo.pendenteVolta)
        }
    }

    private suspend fun aplicarSeHouverViagemAtiva(
        aviso: AvisoAusenciaEntity,
        direcao: String,
        pendente: Boolean
    ) {
        if (!pendente) return

        // O aviso em si já foi salvo: se aplicar na viagem em andamento falhar
        // (rede, permissão), ele continua pendente e o motorista ainda o vê —
        // não pode derrubar o app nem desfazer o aviso.
        try {
            val viagem = viagemRepository.consultarViagemAtivaParaRotaEDirecao(
                aviso.rotaId,
                direcao,
                aviso.responsavelId
            ) ?: return

            viagemRepository.aplicarAusenciaNaParadaAtiva(
                viagemId = viagem.id,
                passageiroId = aviso.passageiroId,
                responsavelId = aviso.responsavelId,
                motivo = ViagemRepository.MOTIVO_AVISO_EM_ANDAMENTO
            )

            avisoAusenciaRepository.marcarConsumido(aviso, direcao)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível aplicar o aviso na viagem em andamento ($direcao)", e)
        }
    }

    private companion object {
        const val TAG = "RegistrarAvisoAusencia"
    }
}
