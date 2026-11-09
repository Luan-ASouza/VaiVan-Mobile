package com.example.vaivan

import com.example.vaivan.data.models.Assinatura
import com.example.vaivan.data.models.StatusAssinatura
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy as P
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy.Estado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bloqueios por plano: iniciar viagem, mensagens ao motorista, estados terminais. */
class AssinaturaBloqueiosTest {

    private val dia = 24L * 60 * 60 * 1000
    private val t0 = 1_800_000_000_000L

    private val basico = Assinatura(
        motoristaId = "m",
        planoId = "basico",
        planoNome = "Plano Básico",
        valorCentavos = 9900,
        limiteRotas = 3,
        ilimitado = false,
        status = StatusAssinatura.ATIVA,
        cicloInicio = t0,
        proximaCobranca = t0 + 30 * dia
    )

    @Test
    fun iniciar_viagem_permitido_com_assinatura_em_dia() {
        assertTrue(P.verificarIniciarViagem(basico, t0 + dia).permitido)
    }

    @Test
    fun iniciar_viagem_permitido_na_carencia() {
        assertTrue(P.verificarIniciarViagem(basico, t0 + 32 * dia).permitido)
    }

    @Test
    fun iniciar_viagem_bloqueado_quando_suspensa_por_atraso() {
        assertFalse(P.verificarIniciarViagem(basico, t0 + 40 * dia).permitido)
    }

    @Test
    fun iniciar_viagem_bloqueado_sem_plano() {
        assertFalse(P.verificarIniciarViagem(null, t0).permitido)
    }

    @Test
    fun iniciar_viagem_bloqueado_quando_suspensa_pelo_administrador() {
        val suspensa = basico.copy(status = StatusAssinatura.SUSPENSA)
        assertFalse(P.verificarIniciarViagem(suspensa, t0 + dia).permitido)
    }

    @Test
    fun mensagem_do_bloqueio_cita_a_acao_pedida() {
        val decisao = P.verificarIniciarViagem(null, t0)
        assertTrue(decisao.motivo.contains("iniciar novas viagens"))
    }

    @Test
    fun decisao_permitida_nao_tem_motivo() {
        assertEquals("", P.verificarIniciarViagem(basico, t0 + dia).motivo)
    }

    @Test
    fun estados_operacionais_nao_tem_motivo_de_bloqueio() {
        assertEquals("", P.motivoDeBloqueio(Estado.ATIVA, "qualquer coisa"))
        assertEquals("", P.motivoDeBloqueio(Estado.EM_CARENCIA, "qualquer coisa"))
    }

    @Test
    fun limite_de_rotas_explica_o_plano_e_o_numero() {
        val decisao = P.verificarCriarRota(basico, 3, t0 + dia)
        assertFalse(decisao.permitido)
        assertTrue(decisao.motivo.contains("Plano Básico"))
        assertTrue(decisao.motivo.contains("3"))
    }

    @Test
    fun assinatura_cancelada_ou_expirada_nao_e_operacional() {
        val cancelada = basico.copy(status = StatusAssinatura.CANCELADA)
        val expirada = basico.copy(status = StatusAssinatura.EXPIRADA)

        assertEquals(Estado.CANCELADA, P.estado(cancelada, t0 + dia))
        assertEquals(Estado.EXPIRADA, P.estado(expirada, t0 + dia))
        assertFalse(P.operacional(cancelada, t0 + dia))
        assertFalse(P.operacional(expirada, t0 + dia))
    }

    @Test
    fun data_limite_da_carencia_e_o_vencimento_mais_tres_dias() {
        assertEquals(t0 + 33 * dia, P.dataLimiteDaCarencia(basico))
    }
}