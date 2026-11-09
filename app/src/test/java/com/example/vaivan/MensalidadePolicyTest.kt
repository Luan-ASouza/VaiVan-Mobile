package com.example.vaivan

import com.example.vaivan.data.models.Contrato
import com.example.vaivan.data.models.Mensalidade
import com.example.vaivan.data.models.StatusContrato
import com.example.vaivan.data.models.StatusMensalidade
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy as P
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy.SituacaoContrato
import com.example.vaivan.domain.usecase.mensalidade.MensalidadePolicy.SituacaoMensalidade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Regras das mensalidades: geração dos meses, atraso, inadimplência e resumo. */
class MensalidadePolicyTest {

    private fun em(ano: Int, mes: Int, dia: Int, hora: Int = 12): Long =
        LocalDateTime.of(ano, mes, dia, hora, 0).atZone(P.FUSO).toInstant().toEpochMilli()

    private fun venc(ano: Int, mes: Int, dia: Int): Long = P.inicioDoDia(LocalDate.of(ano, mes, dia))

    private val contrato = Contrato(
        id = "c1", motoristaId = "m", responsavelId = "r",
        valorMensalCentavos = 40_000, diaVencimento = 10,
        status = StatusContrato.ATIVO, inicioEm = em(2026, 11, 5)
    )

    private fun mensalidade(competencia: String, vencimento: Long, status: String = StatusMensalidade.PENDENTE, pagoEm: Long? = null) =
        Mensalidade(
            id = "c1_$competencia", contratoId = "c1", competencia = competencia,
            valorCentavos = 40_000, vencimento = vencimento, status = status, pagoEm = pagoEm
        )

    @Test
    fun primeira_mensalidade_e_a_primeira_que_vence_depois_do_inicio() {
        assertEquals(YearMonth.of(2026, 11), P.primeiraCompetencia(em(2026, 11, 5), 10))
        assertEquals(YearMonth.of(2026, 11), P.primeiraCompetencia(em(2026, 11, 10), 10))
        assertEquals(YearMonth.of(2026, 12), P.primeiraCompetencia(em(2026, 11, 11), 10))
    }

    @Test
    fun dia_de_vencimento_e_limitado_a_28() {
        assertEquals(LocalDate.of(2027, 2, 28), P.vencimentoDa(YearMonth.of(2027, 2), 31))
        assertEquals(LocalDate.of(2027, 1, 1), P.vencimentoDa(YearMonth.of(2027, 1), 0))
    }

    @Test
    fun gera_ate_a_proxima_a_vencer() {
        // Hoje 09/11: só novembro (vence dia 10, ainda não passou).
        assertEquals(listOf(YearMonth.of(2026, 11)), P.competenciasDevidas(contrato, em(2026, 11, 9)))
        // Hoje 10/11: ainda só novembro (vence hoje).
        assertEquals(listOf(YearMonth.of(2026, 11)), P.competenciasDevidas(contrato, em(2026, 11, 10)))
        // Hoje 11/11: novembro venceu, então dezembro já aparece como próxima.
        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12)),
            P.competenciasDevidas(contrato, em(2026, 11, 11))
        )
        // Hoje 15/02/2027: nov, dez, jan, fev e mar.
        assertEquals(5, P.competenciasDevidas(contrato, em(2027, 2, 15)).size)
    }

    @Test
    fun contrato_encerrado_nao_gera_meses_depois_do_encerramento() {
        val encerrado = contrato.copy(status = StatusContrato.ENCERRADO, encerradoEm = em(2026, 12, 20))
        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12)),
            P.competenciasDevidas(encerrado, em(2027, 3, 1))
        )
    }

    @Test
    fun contrato_sem_data_de_inicio_nao_gera_nada() {
        assertEquals(emptyList<YearMonth>(), P.competenciasDevidas(contrato.copy(inicioEm = 0), em(2026, 11, 9)))
    }

    @Test
    fun so_cria_os_meses_que_faltam() {
        val faltando = P.competenciasFaltando(contrato, setOf("c1_2026-11"), em(2026, 11, 11))
        assertEquals(listOf(YearMonth.of(2026, 12)), faltando)
    }

    @Test
    fun situacao_da_mensalidade_por_dia() {
        val m = mensalidade("2026-11", venc(2026, 11, 10))
        assertEquals(SituacaoMensalidade.A_VENCER, P.situacao(m, em(2026, 11, 9, 23)))
        assertEquals(SituacaoMensalidade.VENCE_HOJE, P.situacao(m, em(2026, 11, 10, 0)))
        assertEquals(SituacaoMensalidade.VENCE_HOJE, P.situacao(m, em(2026, 11, 10, 23)))
        assertEquals(SituacaoMensalidade.ATRASADA, P.situacao(m, em(2026, 11, 11, 0)))
        assertEquals(SituacaoMensalidade.PAGA, P.situacao(m.copy(status = StatusMensalidade.PAGA), em(2027, 1, 1)))
        assertEquals(3L, P.diasDeAtraso(m, em(2026, 11, 13)))
        assertEquals(0L, P.diasDeAtraso(m.copy(status = StatusMensalidade.PAGA), em(2026, 11, 13)))
    }

    @Test
    fun situacao_do_contrato() {
        val nov = mensalidade("2026-11", venc(2026, 11, 10))
        val dez = mensalidade("2026-12", venc(2026, 12, 10))

        assertEquals(SituacaoContrato.EM_DIA, P.situacaoDoContrato(contrato, listOf(nov), em(2026, 11, 10)))
        assertEquals(SituacaoContrato.ATRASADO, P.situacaoDoContrato(contrato, listOf(nov), em(2026, 11, 15)))   // 5 dias
        assertEquals(SituacaoContrato.INADIMPLENTE, P.situacaoDoContrato(contrato, listOf(nov), em(2026, 11, 16))) // 6 dias
        assertEquals(
            SituacaoContrato.EM_DIA,
            P.situacaoDoContrato(contrato, listOf(nov.copy(status = StatusMensalidade.PAGA), dez), em(2026, 11, 20))
        )
        // Duas vencidas = inadimplente mesmo dentro da carência da segunda
        val novPaga = nov.copy(status = StatusMensalidade.PAGA)
        val jan = mensalidade("2027-01", venc(2027, 1, 10))
        assertEquals(SituacaoContrato.INADIMPLENTE, P.situacaoDoContrato(contrato, listOf(novPaga, dez, jan), em(2027, 1, 11)))

        val encerrado = contrato.copy(status = StatusContrato.ENCERRADO)
        assertEquals(SituacaoContrato.ENCERRADO, P.situacaoDoContrato(encerrado, listOf(novPaga), em(2026, 12, 1)))
        assertEquals(SituacaoContrato.INADIMPLENTE, P.situacaoDoContrato(encerrado, listOf(nov), em(2026, 12, 1)))
    }

    @Test
    fun proximo_vencimento() {
        val nov = mensalidade("2026-11", venc(2026, 11, 10))
        assertEquals(venc(2026, 11, 10), P.proximoVencimento(contrato, listOf(nov), em(2026, 11, 9)))

        // Tudo pago: o mês seguinte ao último gerado
        val novPaga = nov.copy(status = StatusMensalidade.PAGA)
        assertEquals(venc(2026, 12, 10), P.proximoVencimento(contrato, listOf(novPaga), em(2026, 11, 9)))

        // Ainda sem mensalidades geradas: a primeira devida
        assertEquals(venc(2026, 11, 10), P.proximoVencimento(contrato, emptyList(), em(2026, 11, 9)))

        val encerrado = contrato.copy(status = StatusContrato.ENCERRADO)
        assertNull(P.proximoVencimento(encerrado, listOf(novPaga), em(2026, 11, 9)))
    }

    @Test
    fun resumo_do_mes() {
        val c2 = contrato.copy(id = "c2")
        val c3 = contrato.copy(id = "c3")
        val agora = em(2026, 11, 20)

        val mensalidades = listOf(
            mensalidade("2026-11", venc(2026, 11, 10), StatusMensalidade.PAGA, pagoEm = em(2026, 11, 8)), // recebido
            mensalidade("2026-12", venc(2026, 12, 10)),                                                      // dezembro: não entra
            mensalidade("2026-11", venc(2026, 11, 10)).copy(id = "c2_2026-11", contratoId = "c2"),           // 10 dias de atraso
            mensalidade("2026-11", venc(2026, 11, 25)).copy(id = "c3_2026-11", contratoId = "c3")            // a receber este mês
        )

        val r = P.resumo(listOf(contrato, c2, c3), mensalidades, agora)
        assertEquals(40_000L, r.recebidoNoMesCentavos)
        assertEquals(40_000L, r.aReceberNoMesCentavos)
        assertEquals(40_000L, r.emAtrasoCentavos)
        assertEquals(2, r.qtdEmDia)
        assertEquals(0, r.qtdAtrasados)
        assertEquals(1, r.qtdInadimplentes)
    }

    @Test
    fun conversao_do_valor_digitado() {
        assertEquals(40_000L, P.textoParaCentavos("400"))
        assertEquals(40_000L, P.textoParaCentavos("400,00"))
        assertEquals(40_050L, P.textoParaCentavos("400,5"))
        assertEquals(120_000L, P.textoParaCentavos("1.200,00"))
        assertEquals(120_050L, P.textoParaCentavos("1200.50"))
        assertEquals(120_000L, P.textoParaCentavos("1.200"))
        assertEquals(40_000L, P.textoParaCentavos("R$ 400"))
        assertNull(P.textoParaCentavos(""))
        assertNull(P.textoParaCentavos("abc"))
        assertNull(P.textoParaCentavos("-10"))
        assertEquals("400,00", P.centavosParaTexto(40_000))
        assertEquals("1200,50", P.centavosParaTexto(120_050))
        assertEquals("0,05", P.centavosParaTexto(5))
    }
}
