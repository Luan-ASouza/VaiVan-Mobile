package com.example.vaivan

import com.example.vaivan.core.util.TextoUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class TextoUtilMascaraTest {

    @Test
    fun telefone_celular_mostra_ddd_e_final() {
        assertEquals("(54) •••••-9460", TextoUtil.mascararTelefone("(54) 99123-9460"))
    }

    @Test
    fun telefone_fixo_mostra_ddd_e_final() {
        assertEquals("(54) ••••-9460", TextoUtil.mascararTelefone("5432129460"))
    }

    @Test
    fun telefone_invalido_volta_como_veio() {
        assertEquals("123", TextoUtil.mascararTelefone("123"))
    }

    @Test
    fun email_mostra_dois_primeiros_e_dominio() {
        assertEquals("lu***@gmail.com", TextoUtil.mascararEmail("luan.souza@gmail.com"))
    }

    @Test
    fun email_sem_arroba_volta_como_veio() {
        assertEquals("semarroba", TextoUtil.mascararEmail("semarroba"))
    }

    @Test
    fun cpf_esconde_inicio_e_digitos_verificadores() {
        assertEquals("•••.456.789-••", TextoUtil.mascararCpf("123.456.789-09"))
    }
}
