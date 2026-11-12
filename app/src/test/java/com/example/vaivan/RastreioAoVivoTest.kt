package com.example.vaivan

import com.example.vaivan.core.util.EtaParadasUtil
import com.example.vaivan.core.util.PolylineUtil
import com.example.vaivan.core.util.ProgressoRotaUtil
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RastreioAoVivoTest {

    // Rua reta de oeste pra leste, um ponto a cada ~97 m (0,001° de longitude)
    private val rua = (0..20).map { -29.17 to (-51.20 + it * 0.001) }

    @Test
    fun polyline_encode_decode_ida_e_volta() {
        val pontos = listOf(LatLng(-29.16781, -51.17932), LatLng(-29.17012, -51.18240), LatLng(-29.2, -51.1))
        val decodificados = PolylineUtil.decode(PolylineUtil.encode(pontos))
        assertEquals(pontos.size, decodificados.size)
        pontos.zip(decodificados).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-5)
            assertEquals(a.longitude, b.longitude, 1e-5)
        }
    }

    @Test
    fun polyline_encode_exemplo_da_documentacao_google() {
        val pontos = listOf(LatLng(38.5, -120.2), LatLng(40.7, -120.95), LatLng(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", PolylineUtil.encode(pontos))
    }

    @Test
    fun eta_codifica_e_decodifica() {
        val etas = mapOf(1 to 1_700_000_000_000L, 4 to 1_700_000_600_000L)
        assertEquals(etas, EtaParadasUtil.decodificar(EtaParadasUtil.codificar(etas)))
        assertEquals(emptyMap<Int, Long>(), EtaParadasUtil.decodificar(""))
        assertEquals(mapOf(2 to 5L), EtaParadasUtil.decodificar("lixo;2=5;3="))
    }

    @Test
    fun van_em_cima_da_rua_acha_o_segmento() {
        // Entre o ponto 5 e o 6, ~5 m ao norte da linha
        val indice = ProgressoRotaUtil.localizarNaRota(rua, 0, -29.16995, -51.1945)
        assertEquals(5, indice)
    }

    @Test
    fun van_fora_da_rota_nao_recorta() {
        // ~550 m ao norte da rua (desvio por outra rua paralela)
        val indice = ProgressoRotaUtil.localizarNaRota(rua, 0, -29.165, -51.1945)
        assertNull(indice)
    }

    @Test
    fun progresso_nunca_volta_pra_tras() {
        // Van perto do ponto 3, mas o progresso já está no 10: não acha nada atrás
        val indice = ProgressoRotaUtil.localizarNaRota(rua, 10, -29.17, -51.197)
        assertNull(indice)
    }

    @Test
    fun busca_limitada_a_janela_a_frente() {
        // Van no ponto 15, mas a janela a partir do 0 só vai até o segmento 5
        val indice = ProgressoRotaUtil.localizarNaRota(rua, 0, -29.17, -51.185, janela = 5)
        assertNull(indice)
    }

    @Test
    fun ponto_na_fracao_segue_o_caminho() {
        // Caminho em L: 0,001° pro leste e depois 0,001° pro norte (pernas quase iguais)
        val caminho = listOf(-29.17 to -51.20, -29.17 to -51.199, -29.169 to -51.199)
        val meio = ProgressoRotaUtil.pontoNaFracao(caminho, 0.5)
        // A metade do comprimento cai perto da esquina, não na diagonal
        assertEquals(-29.17, meio.first, 0.0002)
        assertEquals(-51.199, meio.second, 0.0002)
        assertEquals(caminho.first(), ProgressoRotaUtil.pontoNaFracao(caminho, 0.0))
        assertEquals(caminho.last(), ProgressoRotaUtil.pontoNaFracao(caminho, 1.0))
    }
}
