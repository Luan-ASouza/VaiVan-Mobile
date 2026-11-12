package com.example.vaivan.domain.usecase.viagem

import android.util.Log
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.domain.usecase.rota.CalcularOrdemVoltaUseCase
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.ViagemRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reúne o que uma viagem precisa para começar: busca a rota e os passageiros
 * com parada confirmada (ParadaRotaEntity) direto no Firebase — não confia no
 * cache do Room para esse momento, já que precisa do estado mais atual antes
 * de criar a viagem — e delega a criação ao ViagemRepository.
 *
 * Uma viagem NOVA exige assinatura em dia (as regras do Firestore também exigem);
 * retomar uma viagem já em andamento não exige.
 */
class IniciarViagemUseCase(
    private val rotaRepository: RotaRepository,
    private val passageiroRepository: PassageiroRepository,
    private val viagemRepository: ViagemRepository,
    private val avisoAusenciaRepository: AvisoAusenciaRepository,
    private val calcularOrdemVolta: CalcularOrdemVoltaUseCase? = null,
    private val assinaturaRepository: AssinaturaRepository = AssinaturaRepository()
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

        // Viagem nova: o plano precisa estar em dia
        assinaturaRepository.exigirPodeIniciarViagem(motoristaId)

        val rota = rotaRepository.consultarRota(rotaId)
            ?: throw IllegalStateException("Rota não encontrada.")

        if (rota.motoristaId != motoristaId) {
            throw IllegalStateException("Esta rota não pertence a este motorista.")
        }

        val todasParadas = rotaRepository.consultarParadas(rotaId)

        if (todasParadas.isEmpty()) {
            throw IllegalStateException("Nenhum passageiro confirmado nesta rota ainda.")
        }

        // Passageiros que avisaram ausência pra esta direção continuam na
        // viagem (histórico/cobrança intactos), mas nascem com a parada já
        // resolvida (ver ViagemRepository.statusAusenteOu) — nunca pendente,
        // nunca entram nos waypoints da navegação. Cada aviso aplicado é
        // consumido (ver AvisoAusenciaRepository.marcarConsumido) depois que
        // a viagem é criada com sucesso.
        val avisosAtivos = avisoAusenciaRepository.consultarAvisosAtivos(rotaId, motoristaId)
        val avisosAplicados = avisosAtivos.filter { aviso ->
            if (direcao == ViagemRepository.DIRECAO_IDA) aviso.pendenteIda else aviso.pendenteVolta
        }
        val passageirosAusentes = avisosAplicados.map { it.passageiroId }.toSet()

        // Busca cada passageiro uma vez só (direto no Firestore, estado mais
        // atual) e denormaliza tanto o responsável (regras do Firestore sem
        // consulta cruzada) quanto a foto (snapshot da viagem, pra tela de
        // acompanhamento/navegação não depender de lookup próprio).
        val passageirosPorId = todasParadas
            .map { it.passageiroId }
            .distinct()
            .mapNotNull { passageiroId ->
                passageiroRepository.consultarPassageiro(passageiroId)?.let { passageiroId to it }
            }
            .toMap()

        val responsavelPorPassageiro = passageirosPorId
            .mapNotNull { (passageiroId, passageiro) ->
                val responsavelId = passageiro.responsavelId
                if (responsavelId.isNullOrBlank()) null else passageiroId to responsavelId
            }
            .toMap()

        val fotoPorPassageiro = passageirosPorId.mapValues { (_, passageiro) -> passageiro.fotoUrl }

        // Rota "ida e volta": a ordem de paradas.ordem é a da ida; a volta tem
        // ordem própria (ordemVolta), calculada no aceite e guardada na rota.
        // Rotas só de VOLTA já foram calculadas no sentido certo (ver
        // RecalcularRotaUseCase).
        val paradasDaViagem =
            if (direcao == ViagemRepository.DIRECAO_VOLTA && rota.tipoTrajeto == "IDA_E_VOLTA") {
                ordenarParaVolta(rota, todasParadas, passageirosAusentes)
            } else {
                todasParadas
            }

        val viagem = viagemRepository.iniciarViagem(
            rota = rota,
            paradasRota = paradasDaViagem,
            motoristaId = motoristaId,
            veiculoId = rota.veiculoId,
            direcao = direcao,
            responsavelPorPassageiro = responsavelPorPassageiro,
            fotoPorPassageiro = fotoPorPassageiro,
            passageirosAusentes = passageirosAusentes
        )

        avisosAplicados.forEach { avisoAusenciaRepository.marcarConsumido(it, direcao) }

        return viagem
    }

    /**
     * Ordem de entrega da volta: usa a ordem guardada na rota
     * (ParadaRotaEntity.ordemVolta, calculada no aceite do passageiro). Só
     * calcula aqui se ela não existir ou estiver desatualizada (rota antiga,
     * ou rota alterada depois do último cálculo) — e grava o resultado, pra
     * próxima volta não calcular de novo. Sem cálculo possível (sem rede),
     * usa a ordem da ida invertida: quem foi buscado por último desce primeiro.
     *
     * Ausentes não viram waypoint: vão pro fim, já resolvidos.
     */
    private suspend fun ordenarParaVolta(
        rota: RotaEntity,
        paradasDaRota: List<ParadaRotaEntity>,
        passageirosAusentes: Set<String>
    ): List<ParadaRotaEntity> {
        val voltaGuardadaValida = rota.voltaCalculadaEm != 0L &&
            rota.voltaCalculadaEm == rota.calculadoEm &&
            paradasDaRota.all { it.ordemVolta >= 0 }

        val comVolta: List<ParadaRotaEntity>? =
            if (voltaGuardadaValida) {
                paradasDaRota
            } else {
                calcularOrdemVolta?.invoke(rota, paradasDaRota)?.also { calculadas ->
                    try {
                        rotaRepository.salvarOrdemVolta(rota, calculadas)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Não gravou: a viagem usa a ordem mesmo assim, e a
                        // próxima volta tenta calcular/gravar de novo.
                        Log.w(TAG, "Não foi possível gravar a ordem da volta da rota ${rota.id}", e)
                    }
                }
            }

        val ordenadas = comVolta?.sortedBy { it.ordemVolta }
            ?: paradasDaRota.sortedByDescending { it.ordem }

        val presentes = ordenadas.filter { it.passageiroId !in passageirosAusentes }
        val ausentes = ordenadas.filter { it.passageiroId in passageirosAusentes }

        return (presentes + ausentes).mapIndexed { index, parada ->
            parada.copy(
                ordem = index,
                horarioEstimadoMinutos =
                    if (comVolta != null) parada.horarioEstimadoVoltaMinutos
                    else parada.horarioEstimadoMinutos
            )
        }
    }

    private companion object {
        const val TAG = "IniciarViagemUseCase"
    }
}
