package com.example.vaivan.domain.usecase.viagem

import com.example.vaivan.data.local.entities.ViagemEntity
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.data.repository.AvisoAusenciaRepository
import com.example.vaivan.data.repository.PassageiroRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.ViagemRepository

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
        val avisosAtivos = avisoAusenciaRepository.consultarAvisosAtivos(rotaId)
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

        val viagem = viagemRepository.iniciarViagem(
            rota = rota,
            paradasRota = todasParadas,
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
}