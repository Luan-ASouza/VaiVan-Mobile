package com.example.vaivan

import com.example.vaivan.domain.usecase.preco.PrecoPolicy
import com.example.vaivan.domain.usecase.preco.TabelaPreco
import org.junit.Assert.assertEquals
import org.junit.Test

class PrecoPolicyTest {

    // R$ 120 / 200 / 270 / 330 / 380 · só ida = 60% · 5 km inclusos · R$ 15 por km extra
    private val tabela = TabelaPreco(
        precoPorDiasCentavos = listOf(12_000, 20_000, 27_000, 33_000, 38_000),
        percentualUmTrecho = 60,
        kmIncluidos = 5,
        valorKmExtraCentavos = 1_500
    )

    @Test
    fun ida_e_volta_dentro_da_distancia_incluida() {
        val o = PrecoPolicy.calcular(tabela, "IDA_E_VOLTA", 5, 4_000.0)
        assertEquals(38_000L, o.valorCentavos)
        assertEquals(0, o.kmExtras)
    }

    @Test
    fun exemplo_da_explicacao_so_ida_3_dias_8_km() {
        // (270 + 3 × 15) × 60% = 189
        val o = PrecoPolicy.calcular(tabela, "IDA", 3, 8_000.0)
        assertEquals(18_900L, o.valorCentavos)
        assertEquals(3, o.kmExtras)
        assertEquals(4_500L, o.valorDistanciaCentavos)
    }

    @Test
    fun km_quebrado_arredonda_para_cima() {
        assertEquals(6, PrecoPolicy.distanciaKm(5_100.0))
        assertEquals(5, PrecoPolicy.distanciaKm(5_000.0))
        assertEquals(0, PrecoPolicy.distanciaKm(Double.NaN))
    }

    @Test
    fun valor_final_arredondado_para_reais() {
        // 2 dias, só volta, 6 km: (200 + 15) × 60% = 129
        assertEquals(12_900L, PrecoPolicy.calcular(tabela, "VOLTA", 2, 5_100.0).valorCentavos)
        // 1 dia, só ida, sem extra: 120 × 60% = 72
        assertEquals(7_200L, PrecoPolicy.calcular(tabela, "IDA", 1, 1_000.0).valorCentavos)
        // percentual que gera centavos: 120 × 33% = 39,60 -> 40
        assertEquals(4_000L, PrecoPolicy.calcular(tabela.copy(percentualUmTrecho = 33), "IDA", 1, 0.0).valorCentavos)
    }

    @Test
    fun dias_fora_da_faixa_sao_limitados() {
        assertEquals(38_000L, PrecoPolicy.calcular(tabela, "IDA_E_VOLTA", 7, 0.0).valorCentavos)
        assertEquals(12_000L, PrecoPolicy.calcular(tabela, "IDA_E_VOLTA", 0, 0.0).valorCentavos)
    }

    @Test
    fun dias_cobrados_sao_os_pedidos_que_a_rota_atende() {
        val rota = setOf("SEG", "TER", "QUA", "QUI", "SEX")
        assertEquals(2, PrecoPolicy.diasCobrados(setOf("TER", "QUI"), rota))
        assertEquals(2, PrecoPolicy.diasCobrados(setOf("TER", "QUI", "SAB"), rota))
        assertEquals(5, PrecoPolicy.diasCobrados(emptySet(), rota))
    }

    @Test
    fun rota_so_de_ida_cobra_como_ida_mesmo_se_pediu_ida_e_volta() {
        assertEquals("IDA", PrecoPolicy.modalidadeEfetiva("IDA_E_VOLTA", "IDA"))
        assertEquals("VOLTA", PrecoPolicy.modalidadeEfetiva("VOLTA", "IDA_E_VOLTA"))
    }

    @Test
    fun tabela_incompleta() {
        assertEquals(false, tabela.copy(precoPorDiasCentavos = listOf(1, 2, 3)).completa)
        assertEquals(false, tabela.copy(precoPorDiasCentavos = listOf(1, 2, 0, 4, 5)).completa)
        assertEquals(true, tabela.completa)
    }
}
