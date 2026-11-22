package com.example.vaivan

import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.models.StatusDocumento
import com.example.vaivan.data.repository.DocumentoRepository
import com.example.vaivan.data.repository.VeiculoRepository
import com.example.vaivan.domain.usecase.veiculo.VeiculoPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ciclo de validação do veículo: placa, dados críticos, status e associação a rotas. */
class VeiculoPolicyTest {

    private val base = VeiculoEntity(
        id = "v1", placa = "ABC1D23", marca = "Renault", modelo = "Master",
        cor = "Branco", anoFabricacao = 2020, capacidadePassageiros = 15,
        status = VeiculoRepository.STATUS_APROVADO, motoristaId = "m1"
    )

    @Test
    fun normaliza_placa_com_hifen_e_minusculas() {
        assertEquals("ABC1D23", VeiculoPolicy.normalizarPlaca("abc-1d23"))
        assertEquals("ABC1234", VeiculoPolicy.normalizarPlaca(" ABC 1234 "))
    }

    @Test
    fun mudar_placa_ano_capacidade_ou_modelo_e_critico() {
        assertTrue(VeiculoPolicy.alterouDadosCriticos(base, base.copy(placa = "XYZ9A88")))
        assertTrue(VeiculoPolicy.alterouDadosCriticos(base, base.copy(anoFabricacao = 2021)))
        assertTrue(VeiculoPolicy.alterouDadosCriticos(base, base.copy(capacidadePassageiros = 16)))
        assertTrue(VeiculoPolicy.alterouDadosCriticos(base, base.copy(modelo = "Sprinter")))
        assertTrue(VeiculoPolicy.alterouDadosCriticos(base, base.copy(cor = "Prata")))
    }

    @Test
    fun mesma_placa_em_outro_formato_nao_e_alteracao() {
        assertFalse(VeiculoPolicy.alterouDadosCriticos(base, base.copy(placa = "abc-1d23")))
        assertFalse(VeiculoPolicy.alterouDadosCriticos(base, base.copy(lastUpdated = 99L)))
    }

    @Test
    fun status_apos_envio() {
        val crlv = DocumentoRepository.TIPO_CRLV
        val aut = DocumentoRepository.TIPO_AUTORIZACAO

        assertEquals(VeiculoRepository.STATUS_PENDENTE, VeiculoPolicy.statusAposEnvio(emptyMap()))
        assertEquals(
            VeiculoRepository.STATUS_PENDENTE,
            VeiculoPolicy.statusAposEnvio(mapOf(crlv to StatusDocumento.EM_ANALISE))
        )
        assertEquals(
            VeiculoRepository.STATUS_PENDENTE,
            VeiculoPolicy.statusAposEnvio(mapOf(crlv to StatusDocumento.EM_ANALISE, aut to StatusDocumento.REJEITADO))
        )
        assertEquals(
            VeiculoRepository.STATUS_EM_ANALISE,
            VeiculoPolicy.statusAposEnvio(mapOf(crlv to StatusDocumento.APROVADO, aut to StatusDocumento.EM_ANALISE))
        )
        // Os dois aprovados: quem grava APROVADO é o painel, o app não mexe
        assertNull(VeiculoPolicy.statusAposEnvio(mapOf(crlv to StatusDocumento.APROVADO, aut to StatusDocumento.APROVADO)))
    }

    @Test
    fun so_veiculo_aprovado_vai_para_rota() {
        assertTrue(VeiculoPolicy.podeSerAssociadoARota(base))
        assertFalse(VeiculoPolicy.podeSerAssociadoARota(base.copy(status = VeiculoRepository.STATUS_PENDENTE)))
        assertFalse(VeiculoPolicy.podeSerAssociadoARota(base.copy(status = VeiculoRepository.STATUS_EM_ANALISE)))
        assertFalse(VeiculoPolicy.podeSerAssociadoARota(base.copy(status = VeiculoRepository.STATUS_REJEITADO)))
    }
}
