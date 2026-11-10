package com.example.vaivan.data.models

/**
 * Vínculo financeiro entre um responsável e um motorista, para UM passageiro numa rota.
 *
 * Nasce quando o motorista aceita a solicitação de inclusão, e o id do documento é o
 * mesmo id da solicitação (um contrato por solicitação aceita, sem duplicar).
 *
 * O valor e o dia de vencimento são uma cópia (snapshot) do que a rota tinha no aceite:
 * se o motorista mudar o preço da rota depois, os contratos já existentes não mudam
 * sozinhos (ele pode reajustar contrato por contrato).
 */
data class Contrato(
    val id: String = "",

    val motoristaId: String = "",
    val responsavelId: String = "",
    val nomeResponsavel: String = "",

    val passageiroId: String = "",
    val nomePassageiro: String = "",

    val rotaId: String = "",
    val nomeRota: String = "",

    val valorMensalCentavos: Long = 0L,
    /** Dia do mês (1 a 28) em que a mensalidade vence. */
    val diaVencimento: Int = 10,

    /** Veja [StatusContrato]. */
    val status: String = StatusContrato.ATIVO,
    val inicioEm: Long = 0L,
    val encerradoEm: Long = 0L,

    val criadoEm: Long = 0L,
    val atualizadoEm: Long = 0L
)

/**
 * Uma mensalidade (cobrança de um mês) de um contrato.
 *
 * O id do documento é determinístico: "<contratoId>_<AAAA-MM>". Assim, se o motorista e o
 * responsável abrirem o app ao mesmo tempo, os dois tentam criar o MESMO documento e
 * nunca aparecem duas cobranças do mesmo mês.
 *
 * O status gravado é só PENDENTE / PAGA / CANCELADA. "A vencer", "atrasada" e
 * "inadimplente" são calculados na hora pela [com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy],
 * porque dependem da data de hoje.
 */
data class Mensalidade(
    val id: String = "",
    val contratoId: String = "",

    val motoristaId: String = "",
    val responsavelId: String = "",
    val passageiroId: String = "",
    val nomePassageiro: String = "",
    val nomeRota: String = "",

    /** Mês de referência no formato "AAAA-MM" (ex.: "2026-11"). */
    val competencia: String = "",
    val valorCentavos: Long = 0L,
    /** Início do dia do vencimento, no fuso de São Paulo. */
    val vencimento: Long = 0L,

    /** Veja [StatusMensalidade]. */
    val status: String = StatusMensalidade.PENDENTE,
    val pagoEm: Long? = null,
    /** Veja [FormaPagamentoMensalidade]. */
    val forma: String = "",
    /** Quem marcou como paga: "MOTORISTA" (recebeu por fora) ou "RESPONSAVEL" (pagou no app). */
    val registradoPor: String = "",

    val criadoEm: Long = 0L,
    val atualizadoEm: Long = 0L
)

object StatusContrato {
    const val ATIVO = "ATIVO"
    const val ENCERRADO = "ENCERRADO"
}

object StatusMensalidade {
    const val PENDENTE = "PENDENTE"
    const val PAGA = "PAGA"
    const val CANCELADA = "CANCELADA"
}

object RegistradoPor {
    const val MOTORISTA = "MOTORISTA"
    const val RESPONSAVEL = "RESPONSAVEL"
}

object FormaPagamentoMensalidade {
    /** Pago pelo responsável dentro do app (simulado, como na assinatura). */
    const val PIX_APP = "PIX_APP"

    // Recebido pelo motorista fora do app e registrado manualmente por ele.
    const val PIX = "PIX"
    const val DINHEIRO = "DINHEIRO"
    const val TRANSFERENCIA = "TRANSFERENCIA"
    const val OUTRO = "OUTRO"

    /** Opções que o motorista vê ao registrar um recebimento. */
    val manuais: List<String> = listOf(PIX, DINHEIRO, TRANSFERENCIA, OUTRO)

    fun rotulo(forma: String): String = when (forma) {
        PIX_APP -> "PIX pelo app"
        PIX -> "PIX"
        DINHEIRO -> "Dinheiro"
        TRANSFERENCIA -> "Transferência"
        OUTRO -> "Outro"
        else -> "-"
    }
}
