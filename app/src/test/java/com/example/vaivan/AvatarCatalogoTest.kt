package com.example.vaivan

import com.example.vaivan.core.util.AvatarCatalogo
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarCatalogoTest {

    @Test
    fun tem_os_20_avatares_sem_repetir() {
        assertEquals(20, AvatarCatalogo.AVATARES.size)
        assertEquals(20, AvatarCatalogo.AVATARES.toSet().size)
    }

    @Test
    fun tem_as_7_cores_com_overlay_e_tons_distintos() {
        val cores = AvatarCatalogo.CORES
        assertEquals(7, cores.size)
        assertEquals(7, cores.map { it.overlay }.toSet().size)
        assertEquals(7, cores.map { it.cor }.toSet().size)
        assertEquals(7, cores.map { it.corClara }.toSet().size)
        assertEquals(14, (cores.map { it.cor } + cores.map { it.corClara }).toSet().size)
    }
}
