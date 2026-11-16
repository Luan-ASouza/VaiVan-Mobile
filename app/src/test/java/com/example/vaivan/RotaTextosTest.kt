package com.example.vaivan

import com.example.vaivan.ui.motorista.rotas.RotaTextos
import org.junit.Assert.assertEquals
import org.junit.Test

class RotaTextosTest {

    @Test
    fun `dias uteis viram Seg a Sex em qualquer ordem`() {
        assertEquals("Seg a Sex", RotaTextos.dias(listOf("SEX", "SEG", "QUA", "TER", "QUI")))
    }

    @Test
    fun `todos os dias`() {
        assertEquals("Todos os dias", RotaTextos.dias(RotaTextos.DIAS.reversed()))
    }

    @Test
    fun `lista de dias usa virgula e e no fim, na ordem da semana`() {
        assertEquals("Seg, Qua e Sáb", RotaTextos.dias(listOf("SAB", "SEG", "QUA")))
        assertEquals("Ter", RotaTextos.dias(listOf("TER")))
        assertEquals("Nenhum", RotaTextos.dias(listOf("")))
    }

    @Test
    fun `duracao arredonda para cima e mostra horas`() {
        assertEquals("1 min", RotaTextos.duracao(10))
        assertEquals("35 min", RotaTextos.duracao(35 * 60))
        assertEquals("1h", RotaTextos.duracao(3600))
        assertEquals("1h 05min", RotaTextos.duracao(3600 + 4 * 60 + 1))
        assertEquals("—", RotaTextos.duracao(0))
    }

    @Test
    fun `ponto muda de nome na rota so de volta`() {
        assertEquals("Ponto de desembarque", RotaTextos.rotuloPonto("VOLTA"))
        assertEquals("Ponto de embarque", RotaTextos.rotuloPonto("IDA_E_VOLTA"))
    }
}
