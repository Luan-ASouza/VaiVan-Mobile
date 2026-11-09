package com.example.vaivan

import com.example.vaivan.data.models.StatusDocumento
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tradução do texto gravado no banco para o status do documento. */
class StatusDocumentoTest {

    @Test
    fun aprovado_aceita_as_variacoes_conhecidas() {
        assertEquals(StatusDocumento.APROVADO, StatusDocumento.fromString("APROVADO"))
        assertEquals(StatusDocumento.APROVADO, StatusDocumento.fromString("APROVADA"))
        assertEquals(StatusDocumento.APROVADO, StatusDocumento.fromString("ATIVO"))
    }

    @Test
    fun em_analise_aceita_as_variacoes_conhecidas() {
        assertEquals(StatusDocumento.EM_ANALISE, StatusDocumento.fromString("EM_ANALISE"))
        assertEquals(StatusDocumento.EM_ANALISE, StatusDocumento.fromString("ENVIADO"))
    }

    @Test
    fun rejeitado_aceita_as_variacoes_conhecidas() {
        assertEquals(StatusDocumento.REJEITADO, StatusDocumento.fromString("REJEITADO"))
        assertEquals(StatusDocumento.REJEITADO, StatusDocumento.fromString("REPROVADO"))
    }

    @Test
    fun ignora_maiusculas_e_minusculas() {
        assertEquals(StatusDocumento.APROVADO, StatusDocumento.fromString("aprovado"))
        assertEquals(StatusDocumento.REJEITADO, StatusDocumento.fromString("Rejeitado"))
    }

    @Test
    fun pendente_continua_pendente() {
        assertEquals(StatusDocumento.PENDENTE, StatusDocumento.fromString("PENDENTE"))
    }

    @Test
    fun nulo_ou_desconhecido_vira_nao_enviado() {
        assertEquals(StatusDocumento.NAO_ENVIADO, StatusDocumento.fromString(null))
        assertEquals(StatusDocumento.NAO_ENVIADO, StatusDocumento.fromString(""))
        assertEquals(StatusDocumento.NAO_ENVIADO, StatusDocumento.fromString("qualquer-coisa"))
    }
}