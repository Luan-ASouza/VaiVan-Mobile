package com.example.vaivan.domain.usecase.assinatura

import com.example.vaivan.data.models.Assinatura
import com.example.vaivan.data.models.StatusAssinatura
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Regras de negócio de planos e assinaturas, em Kotlin puro (sem Android e sem Firebase),
 * para poderem ser testadas sozinhas. Toda decisão do tipo "o motorista pode ou não pode?"
 * passa por aqui.
 *
 * Estado efetivo = o que vale AGORA, calculado a partir do que está gravado + datas.
 */
object AssinaturaPolicy {

    const val CARENCIA_DIAS = 3
    const val DIAS_CICLO = 30
    const val ANTECEDENCIA_COBRANCA_DIAS = 3

    private const val DIA_MS = 24L * 60L * 60L * 1000L

    enum class Estado {
        SEM_PLANO,
        AGUARDANDO_PAGAMENTO,
        ATIVA,
        EM_CARENCIA,
        SUSPENSA,
        EXPIRADA,
        CANCELADA
    }

    class Decisao(val permitido: Boolean, val motivo: String = "")

    // ---------------------------------------------------------
    // ESTADO EFETIVO
    // ---------------------------------------------------------

    fun estado(a: Assinatura?, agora: Long): Estado {
        if (a == null) return Estado.SEM_PLANO

        return when (a.status) {
            StatusAssinatura.PENDENTE -> Estado.AGUARDANDO_PAGAMENTO
            StatusAssinatura.CANCELADA -> Estado.CANCELADA
            StatusAssinatura.EXPIRADA -> Estado.EXPIRADA
            StatusAssinatura.SUSPENSA -> Estado.SUSPENSA
            StatusAssinatura.ATIVA -> estadoDaAtiva(a, agora)
            else -> Estado.SEM_PLANO
        }
    }

    private fun estadoDaAtiva(a: Assinatura, agora: Long): Estado {
        val vencimento = a.proximaCobranca

        if (agora <= vencimento) return Estado.ATIVA

        // Venceu o ciclo.
        if (a.cancelamentoAgendado) return Estado.EXPIRADA

        return if (agora <= vencimento + CARENCIA_DIAS * DIA_MS) {
            Estado.EM_CARENCIA
        } else {
            Estado.SUSPENSA
        }
    }

    /** Pode operar (criar rota, aceitar passageiro, aparecer na busca, ver pagamentos). */
    fun operacional(estado: Estado): Boolean =
        estado == Estado.ATIVA || estado == Estado.EM_CARENCIA

    fun operacional(a: Assinatura?, agora: Long): Boolean =
        operacional(estado(a, agora))

    // ---------------------------------------------------------
    // PERMISSÕES
    // ---------------------------------------------------------

    fun verificarCriarRota(a: Assinatura?, rotasAtivas: Int, agora: Long): Decisao {
        val estado = estado(a, agora)

        if (!operacional(estado)) {
            return Decisao(false, motivoDeBloqueio(estado, "criar novas rotas"))
        }

        // operacional implica a != null
        val assinatura = a!!
        if (!assinatura.ilimitado && rotasAtivas >= assinatura.limiteRotas) {
            return Decisao(
                false,
                "Seu ${assinatura.planoNome} permite até ${assinatura.limiteRotas} " +
                        "rota(s) ativa(s) e você já tem $rotasAtivas. " +
                        "Faça upgrade de plano ou arquive uma rota."
            )
        }

        return Decisao(true)
    }

    fun verificarAceitarPassageiro(a: Assinatura?, agora: Long): Decisao {
        val estado = estado(a, agora)
        return if (operacional(estado)) {
            Decisao(true)
        } else {
            Decisao(false, motivoDeBloqueio(estado, "adicionar novos passageiros"))
        }
    }

    fun podeVerPagamentos(a: Assinatura?, agora: Long): Boolean =
        operacional(a, agora)

    fun motivoDeBloqueio(estado: Estado, acao: String): String = when (estado) {
        Estado.SEM_PLANO ->
            "Você ainda não tem um plano. Assine um plano para $acao."
        Estado.AGUARDANDO_PAGAMENTO ->
            "O pagamento da sua assinatura está pendente. Conclua o pagamento para $acao."
        Estado.SUSPENSA ->
            "Sua assinatura está suspensa por inadimplência. Regularize o pagamento para $acao."
        Estado.EXPIRADA, Estado.CANCELADA ->
            "Sua assinatura não está ativa. Assine um plano para $acao."
        Estado.ATIVA, Estado.EM_CARENCIA ->
            ""
    }

    // ---------------------------------------------------------
    // CICLO E COBRANÇA
    // ---------------------------------------------------------

    /**
     * Calcula (início, vencimento) do ciclo que começa quando um pagamento de mensalidade
     * é confirmado. Pagamento em dia (ou na carência) encadeia no vencimento anterior;
     * pagamento depois de suspensa, expirada ou o primeiro começam "agora".
     */
    fun novoCiclo(a: Assinatura?, agora: Long): Pair<Long, Long> {
        val encadeia = a != null &&
                a.status == StatusAssinatura.ATIVA &&
                a.proximaCobranca > 0L &&
                agora <= a.proximaCobranca + CARENCIA_DIAS * DIA_MS

        val inicio = if (encadeia) a!!.proximaCobranca else agora
        return Pair(inicio, inicio + DIAS_CICLO * DIA_MS)
    }

    /** A cobrança de renovação é emitida alguns dias antes do vencimento. */
    fun deveGerarCobrancaDeRenovacao(a: Assinatura?, agora: Long): Boolean {
        if (a == null) return false
        if (a.status != StatusAssinatura.ATIVA) return false
        if (a.cancelamentoAgendado) return false
        if (a.proximaCobranca <= 0L) return false
        return agora >= a.proximaCobranca - ANTECEDENCIA_COBRANCA_DIAS * DIA_MS
    }

    /**
     * Diferença proporcional (pró-rata) de um upgrade: o motorista paga só a diferença de
     * preço pelos dias que faltam do ciclo atual.
     */
    fun proRataCentavos(
        valorAtualCentavos: Long,
        valorNovoCentavos: Long,
        proximaCobranca: Long,
        agora: Long
    ): Long {
        val diferenca = valorNovoCentavos - valorAtualCentavos
        if (diferenca <= 0L) return 0L

        val restanteMs = (proximaCobranca - agora).coerceAtLeast(0L)
        val diasRestantes = ((restanteMs + DIA_MS - 1) / DIA_MS)
            .coerceAtMost(DIAS_CICLO.toLong())

        return diferenca * diasRestantes / DIAS_CICLO
    }

    /** Identifica o ciclo de uma cobrança (usado para não emitir a mesma cobrança duas vezes). */
    fun referenciaDoVencimento(vencimento: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(vencimento))

    fun dataLimiteDaCarencia(a: Assinatura): Long =
        a.proximaCobranca + CARENCIA_DIAS * DIA_MS
}