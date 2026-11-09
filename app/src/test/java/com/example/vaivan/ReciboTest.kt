package com.example.vaivan

import com.example.vaivan.data.models.Pagamento
import com.example.vaivan.ui.motorista.assinatura.AssinaturaUi
import org.junit.Assert.assertTrue
import org.junit.Test

/** Texto do recibo de pagamento (o que aparece na tela e é compartilhado). */
class ReciboTest {

    private val pagamento = Pagamento(
        id = "abcdef1234",
        descricao = "Mensalidade - Plano Básico",
        planoNome = "Plano Básico",
        valorCentavos = 9900,
        forma = "PIX (simulado)",
        pagoEm = null
    )

    @Test
    fun recibo_traz_numero_valor_e_forma_de_pagamento() {
        val texto = AssinaturaUi.textoDoRecibo(pagamento, "motorista@exemplo.com")

        assertTrue(texto.contains("ABCDEF12"))
        assertTrue(texto.contains("99,00"))
        assertTrue(texto.contains("PIX (simulado)"))
        assertTrue(texto.contains("Mensalidade - Plano Básico"))
        assertTrue(texto.contains("motorista@exemplo.com"))
    }

    @Test
    fun recibo_sem_data_de_pagamento_mostra_traco() {
        val texto = AssinaturaUi.textoDoRecibo(pagamento, "a@b.com")
        assertTrue(texto.contains("Pago em: -"))
    }

    @Test
    fun recibo_avisa_que_e_de_demonstracao() {
        val texto = AssinaturaUi.textoDoRecibo(pagamento, "a@b.com")
        assertTrue(texto.contains("sem valor fiscal"))
    }
}