package com.example.vaivan

import com.example.vaivan.domain.usecase.rota.HorariosDaRota as H
import org.junit.Assert.assertEquals
import org.junit.Test

class HorariosDaRotaTest {

    @Test
    fun com_ponto_de_partida() {
        // saída -> A (10 min) -> B (5 min) -> C (8 min) -> escola (12 min)
        assertEquals(listOf(10, 15, 23), H.minutosPorParada(listOf(600, 300, 480, 720), 3))
    }

    @Test
    fun rota_antiga_a_primeira_parada_e_a_saida() {
        assertEquals(listOf(0, 5, 13), H.minutosPorParada(listOf(300, 480, 720), 3, true))
    }

    @Test
    fun casos_extremos_nao_quebram() {
        assertEquals(listOf(0, 0), H.minutosPorParada(emptyList(), 2))
        assertEquals(emptyList<Int>(), H.minutosPorParada(listOf(600, 300), 0))
    }
}