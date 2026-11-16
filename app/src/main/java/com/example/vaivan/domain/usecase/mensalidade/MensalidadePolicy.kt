package com.example.vaivan.domain.usecase.mensalidade

import com.example.vaivan.data.models.Contrato
import com.example.vaivan.data.models.Mensalidade
import com.example.vaivan.data.models.StatusContrato
import com.example.vaivan.data.models.StatusMensalidade
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Regras das mensalidades (responsável → motorista). Sem Android e sem Firestore,
 * para poder ser testada (veja MensalidadePolicyTest), no mesmo espírito da AssinaturaPolicy.
 *
 * Datas são comparadas por DIA no fuso de São Paulo: quem vence dia 10 está em dia
 * durante o dia 10 inteiro e só fica atrasado no dia 11.
 */
object MensalidadePolicy {

    val FUSO: ZoneId = ZoneId.of("America/Sao_Paulo")

    /** Dias de atraso tolerados antes de considerar o responsável inadimplente. */
    const val DIAS_CARENCIA = 5

    const val DIA_VENCIMENTO_PADRAO = 10

    /** Dias que o responsável tem, depois do aceite, para pagar a 1ª mensalidade. */
    const val PRAZO_PRIMEIRO_PAGAMENTO_DIAS = 3L

    /** Proteção contra contratos com data inválida: nunca gera mais que isso de uma vez. */
    private const val MAX_COMPETENCIAS = 24

    enum class SituacaoMensalidade { PAGA, A_VENCER, VENCE_HOJE, ATRASADA, CANCELADA }

    enum class SituacaoContrato { RESERVADO, EM_DIA, ATRASADO, INADIMPLENTE, ENCERRADO }

    data class Resumo(
        val recebidoNoMesCentavos: Long,
        val aReceberNoMesCentavos: Long,
        val emAtrasoCentavos: Long,
        val qtdEmDia: Int,
        val qtdAtrasados: Int,
        val qtdInadimplentes: Int
    )

    // =========================================================
    // DATAS
    // =========================================================

    fun dia(milissegundos: Long): LocalDate =
        Instant.ofEpochMilli(milissegundos).atZone(FUSO).toLocalDate()

    fun inicioDoDia(data: LocalDate): Long =
        data.atStartOfDay(FUSO).toInstant().toEpochMilli()

    /** Limitado a 28 para existir em todos os meses (inclusive fevereiro). */
    fun diaValido(dia: Int): Int = dia.coerceIn(1, 28)

    fun vencimentoDa(competencia: YearMonth, diaVencimento: Int): LocalDate =
        competencia.atDay(diaValido(diaVencimento))

    fun idMensalidade(contratoId: String, competencia: YearMonth): String =
        "${contratoId}_$competencia"

    // =========================================================
    // VALORES
    // =========================================================

    /**
     * Converte o que o motorista digitou em centavos. Aceita "400", "400,00", "400,5",
     * "1.200,00", "1200.50" e "R$ 400". Devolve null se não for um valor válido.
     */
    fun textoParaCentavos(texto: String): Long? {
        var limpo = texto.replace("R$", "").replace(" ", "").trim()
        if (limpo.isEmpty()) return null

        limpo = when {
            limpo.contains(',') -> limpo.replace(".", "").replace(',', '.')
            Regex("^\\d+\\.\\d{1,2}$").matches(limpo) -> limpo // "1200.50": ponto decimal
            else -> limpo.replace(".", "")                         // "1.200": milhar
        }

        val valor = limpo.toBigDecimalOrNull() ?: return null
        if (valor.signum() < 0) return null
        return valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
    }

    /** 40000 -> "400,00" (para preencher o campo de edição). */
    fun centavosParaTexto(centavos: Long): String =
        BigDecimal(centavos).movePointLeft(2).setScale(2).toPlainString().replace('.', ',')

    // =========================================================
    // 1ª MENSALIDADE (paga antes de usar a van)
    // =========================================================

    /** Último dia para pagar a 1ª mensalidade: dia do aceite + 3 dias. */
    fun prazoPrimeiroPagamento(aceiteEm: Long): Long =
        inicioDoDia(dia(aceiteEm).plusDays(PRAZO_PRIMEIRO_PAGAMENTO_DIAS))

    /** A reserva vence quando termina o último dia do prazo. */
    fun reservaExpirada(aceiteEm: Long, agora: Long): Boolean =
        aceiteEm > 0L && dia(agora).isAfter(dia(aceiteEm).plusDays(PRAZO_PRIMEIRO_PAGAMENTO_DIAS))

    /**
     * Valor da 1ª mensalidade: proporcional aos dias que faltam no mês, contando o
     * dia do aceite. Arredondado para reais. Ex.: R$ 400 aceito em 16/11 (15 de 30
     * dias) = R$ 200.
     */
    fun valorProporcional(valorMensalCentavos: Long, aceiteEm: Long): Long {
        val inicio = dia(aceiteEm)
        val diasNoMes = inicio.lengthOfMonth()
        val diasRestantes = diasNoMes - inicio.dayOfMonth + 1
        val proporcional = valorMensalCentavos.toDouble() * diasRestantes / diasNoMes
        return Math.round(proporcional / 100.0) * 100
    }

    private fun mesInicial(contrato: Contrato): YearMonth? =
        contrato.competenciaInicial.takeIf { it.isNotBlank() }
            ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

    /** Vencimento de um mês do contrato (a 1ª tem prazo próprio). */
    fun vencimentoDaCompetencia(contrato: Contrato, mes: YearMonth): LocalDate =
        if (mes == mesInicial(contrato) && contrato.vencimentoPrimeira > 0L) dia(contrato.vencimentoPrimeira)
        else vencimentoDa(mes, contrato.diaVencimento)

    /** Valor de um mês do contrato (a 1ª é proporcional). */
    fun valorDaCompetencia(contrato: Contrato, mes: YearMonth): Long =
        if (mes == mesInicial(contrato) && contrato.valorPrimeiraCentavos > 0L) contrato.valorPrimeiraCentavos
        else contrato.valorMensalCentavos

    // =========================================================
    // GERAÇÃO
    // =========================================================

    /**
     * Primeira mensalidade: a primeira cujo vencimento cai no dia do início ou depois.
     * Ex.: contrato começou dia 20/11 com vencimento dia 10 → primeira vence 10/12.
     *      contrato começou dia 05/11 com vencimento dia 10 → primeira vence 10/11.
     */
    fun primeiraCompetencia(inicioEm: Long, diaVencimento: Int): YearMonth {
        val inicio = dia(inicioEm)
        val mes = YearMonth.from(inicio)
        return if (!vencimentoDa(mes, diaVencimento).isBefore(inicio)) mes else mes.plusMonths(1)
    }

    /**
     * Meses que precisam ter mensalidade criada: desde a primeira até a PRÓXIMA a vencer
     * (inclusive), para o motorista e o responsável sempre enxergarem o próximo mês.
     * Contrato encerrado não gera meses com vencimento depois do encerramento.
     */
    fun competenciasDevidas(contrato: Contrato, agora: Long): List<YearMonth> {
        if (contrato.inicioEm <= 0L) return emptyList()

        val hoje = dia(agora)
        val limite: LocalDate? =
            if (contrato.status == StatusContrato.ENCERRADO && contrato.encerradoEm > 0L) {
                dia(contrato.encerradoEm)
            } else {
                null
            }

        val resultado = mutableListOf<YearMonth>()
        var mes = mesInicial(contrato) ?: primeiraCompetencia(contrato.inicioEm, contrato.diaVencimento)

        // Vaga só reservada: existe apenas a 1ª mensalidade até ela ser paga
        if (contrato.status == StatusContrato.AGUARDANDO_PAGAMENTO) return listOf(mes)

        while (resultado.size < MAX_COMPETENCIAS) {
            val vencimento = vencimentoDaCompetencia(contrato, mes)
            if (limite != null && vencimento.isAfter(limite)) break

            resultado += mes
            if (!vencimento.isBefore(hoje)) break // esta já é a próxima a vencer

            mes = mes.plusMonths(1)
        }
        return resultado
    }

    /** Das competências devidas, as que ainda não têm documento. */
    fun competenciasFaltando(
        contrato: Contrato,
        idsExistentes: Set<String>,
        agora: Long
    ): List<YearMonth> =
        competenciasDevidas(contrato, agora)
            .filter { idMensalidade(contrato.id, it) !in idsExistentes }

    // =========================================================
    // SITUAÇÃO
    // =========================================================

    fun diasDeAtraso(mensalidade: Mensalidade, agora: Long): Long {
        if (mensalidade.status != StatusMensalidade.PENDENTE) return 0L
        val dias = ChronoUnit.DAYS.between(dia(mensalidade.vencimento), dia(agora))
        return dias.coerceAtLeast(0L)
    }

    fun situacao(mensalidade: Mensalidade, agora: Long): SituacaoMensalidade {
        when (mensalidade.status) {
            StatusMensalidade.PAGA -> return SituacaoMensalidade.PAGA
            StatusMensalidade.CANCELADA -> return SituacaoMensalidade.CANCELADA
        }
        val vencimento = dia(mensalidade.vencimento)
        val hoje = dia(agora)
        return when {
            hoje.isBefore(vencimento) -> SituacaoMensalidade.A_VENCER
            hoje.isEqual(vencimento) -> SituacaoMensalidade.VENCE_HOJE
            else -> SituacaoMensalidade.ATRASADA
        }
    }

    /**
     * Em dia: nada vencido.
     * Atrasado: uma mensalidade vencida há até [DIAS_CARENCIA] dias.
     * Inadimplente: atraso maior que a carência, ou duas ou mais mensalidades vencidas.
     */
    fun situacaoDoContrato(
        contrato: Contrato,
        mensalidadesDoContrato: List<Mensalidade>,
        agora: Long
    ): SituacaoContrato {
        if (contrato.status == StatusContrato.AGUARDANDO_PAGAMENTO) return SituacaoContrato.RESERVADO

        val vencidas = mensalidadesDoContrato.filter {
            situacao(it, agora) == SituacaoMensalidade.ATRASADA
        }

        return when {
            vencidas.isEmpty() ->
                if (contrato.status == StatusContrato.ENCERRADO) SituacaoContrato.ENCERRADO
                else SituacaoContrato.EM_DIA
            vencidas.size >= 2 -> SituacaoContrato.INADIMPLENTE
            vencidas.any { diasDeAtraso(it, agora) > DIAS_CARENCIA } -> SituacaoContrato.INADIMPLENTE
            else -> SituacaoContrato.ATRASADO
        }
    }

    /** A mensalidade em aberto mais antiga (a que deve ser paga primeiro), se houver. */
    fun proximaEmAberto(mensalidadesDoContrato: List<Mensalidade>): Mensalidade? =
        mensalidadesDoContrato
            .filter { it.status == StatusMensalidade.PENDENTE }
            .minByOrNull { it.vencimento }

    /**
     * Data da próxima cobrança: a mais antiga em aberto; se está tudo pago, o vencimento
     * do mês seguinte ao último já gerado. Nulo para contrato encerrado e quitado.
     */
    fun proximoVencimento(
        contrato: Contrato,
        mensalidadesDoContrato: List<Mensalidade>,
        agora: Long
    ): Long? {
        proximaEmAberto(mensalidadesDoContrato)?.let { return it.vencimento }
        if (contrato.status == StatusContrato.ENCERRADO) return null

        val ultimoMes = mensalidadesDoContrato
            .mapNotNull { runCatching { YearMonth.parse(it.competencia) }.getOrNull() }
            .maxOrNull()
            ?: competenciasDevidas(contrato, agora).lastOrNull()
            ?: return null

        val proximo = if (mensalidadesDoContrato.isEmpty()) ultimoMes else ultimoMes.plusMonths(1)
        return inicioDoDia(vencimentoDaCompetencia(contrato, proximo))
    }

    // =========================================================
    // RESUMO (painel do motorista)
    // =========================================================

    fun resumo(
        contratos: List<Contrato>,
        mensalidades: List<Mensalidade>,
        agora: Long
    ): Resumo {
        val mesAtual = YearMonth.from(dia(agora))
        val reservados = contratos.filter { it.status == StatusContrato.AGUARDANDO_PAGAMENTO }.map { it.id }.toSet()
        val porContrato = mensalidades.groupBy { it.contratoId }

        val recebido = mensalidades
            .filter { it.status == StatusMensalidade.PAGA && it.pagoEm != null }
            .filter { YearMonth.from(dia(it.pagoEm!!)) == mesAtual }
            .sumOf { it.valorCentavos }

        val aReceber = mensalidades
            .filter { it.status == StatusMensalidade.PENDENTE }
            .filter { situacao(it, agora) != SituacaoMensalidade.ATRASADA }
            .filter { YearMonth.from(dia(it.vencimento)) == mesAtual }
            .sumOf { it.valorCentavos }

        val emAtraso = mensalidades
            .filter { it.contratoId !in reservados }
            .filter { situacao(it, agora) == SituacaoMensalidade.ATRASADA }
            .sumOf { it.valorCentavos }

        val situacoes = contratos.map {
            situacaoDoContrato(it, porContrato[it.id].orEmpty(), agora)
        }

        return Resumo(
            recebidoNoMesCentavos = recebido,
            aReceberNoMesCentavos = aReceber,
            emAtrasoCentavos = emAtraso,
            qtdEmDia = situacoes.count { it == SituacaoContrato.EM_DIA },
            qtdAtrasados = situacoes.count { it == SituacaoContrato.ATRASADO },
            qtdInadimplentes = situacoes.count { it == SituacaoContrato.INADIMPLENTE }
        )
    }
}
