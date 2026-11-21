package com.example.vaivan.ui.comum.mensalidade

import com.example.vaivan.data.models.FormaPagamentoMensalidade
import com.example.vaivan.data.models.Mensalidade
import com.example.vaivan.data.models.RegistradoPor
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy.SituacaoContrato
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy.SituacaoMensalidade
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import com.example.vaivan.ui.comum.tema.TomSelo
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Textos e cores das mensalidades, usados pelas telas do motorista e do responsável. */
object MensalidadeUi {

    private val localeBr = Locale("pt", "BR")
    private val formatoMes = DateTimeFormatter.ofPattern("MMMM/yyyy", localeBr)
    private val formatoSoMes = DateTimeFormatter.ofPattern("MMMM", localeBr)

    /** "2026-11" -> "Novembro/2026" */
    fun nomeDoMes(competencia: String): String =
        runCatching { YearMonth.parse(competencia).format(formatoMes).maiuscula() }
            .getOrDefault(competencia)

    /** Mês atual por extenso, minúsculo: "novembro". */
    fun mesAtual(agora: Long): String =
        YearMonth.from(MensalidadePolicy.dia(agora)).format(formatoSoMes)

    fun rotuloDoContrato(situacao: SituacaoContrato): Pair<String, TomSelo> = when (situacao) {
        SituacaoContrato.RESERVADO -> "Reservado" to TomSelo.ALERTA
        SituacaoContrato.EM_DIA -> "Em dia" to TomSelo.SUCESSO
        SituacaoContrato.ATRASADO -> "Atrasado" to TomSelo.ALERTA
        SituacaoContrato.INADIMPLENTE -> "Inadimplente" to TomSelo.PERIGO
        SituacaoContrato.ENCERRADO -> "Encerrado" to TomSelo.NEUTRO
    }

    fun rotuloDaMensalidade(mensalidade: Mensalidade, agora: Long): Pair<String, TomSelo> =
        when (MensalidadePolicy.situacao(mensalidade, agora)) {
            SituacaoMensalidade.PAGA -> "Paga" to TomSelo.SUCESSO
            SituacaoMensalidade.A_VENCER -> "A vencer" to TomSelo.NEUTRO
            SituacaoMensalidade.VENCE_HOJE -> "Vence hoje" to TomSelo.ALERTA
            SituacaoMensalidade.ATRASADA -> {
                val dias = MensalidadePolicy.diasDeAtraso(mensalidade, agora)
                (if (dias == 1L) "1 dia de atraso" else "$dias dias de atraso") to TomSelo.PERIGO
            }
            SituacaoMensalidade.CANCELADA -> "Cancelada" to TomSelo.NEUTRO
        }

    /** Linha de detalhe de uma mensalidade no histórico. */
    fun detalheDaMensalidade(mensalidade: Mensalidade, paraMotorista: Boolean): String {
        val pagoEm = mensalidade.pagoEm
        if (pagoEm != null) {
            val quem = when {
                mensalidade.registradoPor == RegistradoPor.MOTORISTA && paraMotorista -> " (registrado por você)"
                mensalidade.registradoPor == RegistradoPor.MOTORISTA -> " (registrado pelo motorista)"
                else -> ""
            }
            return "Pago em ${AssinaturaUi.formatarData(pagoEm)} · " +
                FormaPagamentoMensalidade.rotulo(mensalidade.forma) + quem
        }
        return "Vence em ${AssinaturaUi.formatarData(mensalidade.vencimento)}"
    }

    private fun String.maiuscula() = replaceFirstChar { it.titlecase(localeBr) }
}
