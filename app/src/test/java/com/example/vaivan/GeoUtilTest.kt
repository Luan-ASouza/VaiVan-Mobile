package com.example.vaivan

import com.example.vaivan.core.util.GeoUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoUtilTest {

    @Test
    fun haversine_centesimo_de_grau() {
        val d = GeoUtil.distanciaMetros(-29.17, -51.18, -29.16, -51.18)
        assertEquals(1112.0, d, 15.0)
    }

    @Test
    fun ponto_ao_norte_do_trajeto() {
        val trajeto = listOf(-29.17 to -51.20, -29.17 to -51.16)
        val d = GeoUtil.distanciaAoTrajetoMetros(-29.1655, -51.18, trajeto)
        assertEquals(500.0, d, 10.0)
    }

    @Test
    fun ponto_sobre_o_trajeto() {
        val trajeto = listOf(-29.17 to -51.20, -29.17 to -51.16)
        val d = GeoUtil.distanciaAoTrajetoMetros(-29.17, -51.17, trajeto)
        assertEquals(0.0, d, 1.0)
    }
}
