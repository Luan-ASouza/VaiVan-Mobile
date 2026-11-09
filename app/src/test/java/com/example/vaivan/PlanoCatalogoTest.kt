package com.example.vaivan

import com.example.vaivan.data.models.PlanoCatalogo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Planos padrão usados pelo app enquanto o administrador não cadastra os seus. */
class PlanoCatalogoTest {

    @Test
    fun catalogo_padrao_tem_basico_e_premium_em_ordem() {
        val ids = PlanoCatalogo.padrao.sortedBy { it.ordem }.map { it.id }
        assertEquals(listOf("basico", "premium"), ids)
    }

    @Test
    fun basico_tem_limite_de_tres_rotas() {
        val basico = PlanoCatalogo.porId("basico")
        assertNotNull(basico)
        assertEquals(3, basico!!.limiteRotas)
        assertFalse(basico.ilimitado)
    }

    @Test
    fun premium_e_ilimitado_e_mais_caro_que_o_basico() {
        val basico = PlanoCatalogo.porId("basico")!!
        val premium = PlanoCatalogo.porId("premium")!!
        assertTrue(premium.ilimitado)
        assertTrue(premium.valorCentavos > basico.valorCentavos)
    }

    @Test
    fun plano_inexistente_devolve_nulo() {
        assertNull(PlanoCatalogo.porId("nao-existe"))
    }

    @Test
    fun todos_os_planos_padrao_estao_ativos_e_com_id_unico() {
        assertTrue(PlanoCatalogo.padrao.all { it.ativo })
        assertEquals(
            PlanoCatalogo.padrao.size,
            PlanoCatalogo.padrao.map { it.id }.toSet().size
        )
    }
}