package com.example.vaivan.domain.usecase.ausencia

import com.example.vaivan.data.local.entities.AvisoAusenciaEntity
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.ViagemRepository

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

        val viagem = viagemRepository.consultarViagemAtivaParaRotaEDirecao(aviso.rotaId, direcao)
            ?: return

        viagemRepository.aplicarAusenciaNaParadaAtiva(
            viagemId = viagem.id,
            passageiroId = aviso.passageiroId,
            motivo = "Aviso do responsável (viagem em andamento)"
        )

        avisoAusenciaRepository.marcarConsumido(aviso, direcao)
    }
}
