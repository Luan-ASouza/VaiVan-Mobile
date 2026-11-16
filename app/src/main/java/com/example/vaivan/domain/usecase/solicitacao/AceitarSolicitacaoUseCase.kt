package com.example.vaivan.domain.usecase.solicitacao

import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import android.util.Log
import com.example.vaivan.data.repository.AssinaturaRepository
import com.example.vaivan.data.repository.MensalidadeRepository
import com.example.vaivan.data.repository.RotaRepository
import com.example.vaivan.data.repository.SolicitacaoInclusaoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * O motorista ACEITA a solicitação de um responsável. Desde a regra do 1º pagamento,
 * aceitar não coloca o aluno na rota: só RESERVA a vaga.
 *
 *  1. confere o plano do motorista (inadimplente não adiciona passageiros);
 *  2. reserva a vaga numa transação (vagasOcupadas + 1, pedido AGUARDANDO_PAGAMENTO).
 *     Se o pedido já foi respondido/cancelado ou não há mais vaga, nada é gravado;
 *  3. guarda a cópia local;
 *  4. cria o contrato "aguardando pagamento" e a 1ª mensalidade (proporcional aos
 *     dias restantes do mês, com prazo de 3 dias).
 *
 * O aluno entra no trajeto quando o responsável paga a 1ª mensalidade
 * (ConfirmarReservasPagasUseCase). Se não pagar no prazo, a reserva expira e a vaga
 * volta a ficar livre.
 */
class AceitarSolicitacaoUseCase(
    private val solicitacaoInclusaoRepository: SolicitacaoInclusaoRepository,
    private val rotaRepository: RotaRepository,
    private val assinaturaRepository: AssinaturaRepository = AssinaturaRepository(),
    private val mensalidadeRepository: MensalidadeRepository = MensalidadeRepository()
) {

    suspend operator fun invoke(
        solicitacao: SolicitacaoInclusaoEntity
    ) = withContext(Dispatchers.IO) {

        // 1. Plano
        assinaturaRepository.exigirPodeAceitarPassageiro(solicitacao.motoristaId)

        // 2. Reserva da vaga (transação)
        val respondidaEm = System.currentTimeMillis()
        rotaRepository.reservarVaga(
            solicitacaoId = solicitacao.id,
            rotaId = solicitacao.rotaId,
            respondidaEm = respondidaEm
        )

        // 3. Cópia local
        solicitacaoInclusaoRepository.registrarAceitaLocalmente(
            solicitacao,
            respondidaEm,
            status = SolicitacaoInclusaoRepository.STATUS_AGUARDANDO_PAGAMENTO
        )

        // 4. Contrato + 1ª mensalidade. Se falhar agora (ex.: sem internet), a reserva
        //    continua valendo e o ConfirmarReservasPagasUseCase cria o que faltar.
        try {
            mensalidadeRepository.garantirContrato(solicitacao.id, inicioEm = respondidaEm)
        } catch (e: Exception) {
            Log.w("AceitarSolicitacao", "Contrato será criado depois", e)
        }
    }
}
