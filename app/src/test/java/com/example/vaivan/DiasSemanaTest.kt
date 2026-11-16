package com.example.vaivan

import com.example.vaivan.core.util.DataUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class DiasSemanaTest {

    @Test
    fun `ordena na ordem da semana, nao na ordem em que foi tocado`() {
        assertEquals(listOf("SEG", "TER", "QUA"), DataUtil.ordenarDiasSemana("TER,SEG,QUA"))
    }

    @Test
    fun `aceita espacos, minusculas e remove repetidos e vazios`() {
        assertEquals(listOf("SEG", "SEX", "DOM"), DataUtil.ordenarDiasSemana(" dom, sex,,SEG , sex"))
    }

    @Test
    fun `texto vazio vira lista vazia`() {
        assertEquals(emptyList<String>(), DataUtil.ordenarDiasSemana(""))
    }

    @Test
    fun `chave desconhecida vai para o fim`() {
        assertEquals(listOf("QUA", "XYZ"), DataUtil.ordenarDiasSemana(listOf("XYZ", "QUA")))
    }
}
