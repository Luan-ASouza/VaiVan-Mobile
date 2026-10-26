package com.example.vaivan

import com.example.vaivan.data.models.Assinatura
import com.example.vaivan.data.models.StatusAssinatura
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy as P
import com.example.vaivan.domain.usecase.assinatura.AssinaturaPolicy.Estado
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras de negócio dos planos: estado da assinatura, carência, limites e pro-rata. */
class AssinaturaPolicyTest {

    private val D = 24L * 60 * 60 * 1000

    @Test
    fun regras_de_assinatura() {
        val t0 = 1_800_000_000_000L
        val basico = Assinatura(motoristaId="m", planoId="basico", planoNome="Plano Básico", valorCentavos=9900,
            limiteRotas=3, ilimitado=false, status=StatusAssinatura.ATIVA, cicloInicio=t0, proximaCobranca=t0+30*D)
        val premium = basico.copy(planoId="premium", planoNome="Plano Premium", valorCentavos=19900, ilimitado=true, limiteRotas=0)

        assertTrue("sem assinatura", P.estado(null, t0) == Estado.SEM_PLANO)
        assertTrue("pendente", P.estado(basico.copy(status=StatusAssinatura.PENDENTE), t0) == Estado.AGUARDANDO_PAGAMENTO)
        assertTrue("ativa em dia", P.estado(basico, t0+10*D) == Estado.ATIVA)
        assertTrue("ativa no dia do vencimento", P.estado(basico, t0+30*D) == Estado.ATIVA)
        assertTrue("carencia 1 dia depois", P.estado(basico, t0+31*D) == Estado.EM_CARENCIA)
        assertTrue("carencia 3 dias depois", P.estado(basico, t0+33*D) == Estado.EM_CARENCIA)
        assertTrue("suspensa 3d+1ms", P.estado(basico, t0+33*D+1) == Estado.SUSPENSA)
        assertTrue("suspensa 10 dias depois", P.estado(basico, t0+40*D) == Estado.SUSPENSA)
        assertTrue("cancelamento agendado ainda ativa", P.estado(basico.copy(cancelamentoAgendado=true), t0+29*D) == Estado.ATIVA)
        assertTrue("cancelamento agendado expira", P.estado(basico.copy(cancelamentoAgendado=true), t0+30*D+1) == Estado.EXPIRADA)
        assertTrue("suspensa pelo admin", P.estado(basico.copy(status=StatusAssinatura.SUSPENSA), t0) == Estado.SUSPENSA)

        assertTrue("operacional ativa", P.operacional(basico, t0+1))
        assertTrue("operacional carencia", P.operacional(basico, t0+32*D))
        assertTrue("nao operacional suspensa", !P.operacional(basico, t0+40*D))
        assertTrue("nao operacional sem plano", !P.operacional(null, t0))

        assertTrue("criar rota 0 de 3", P.verificarCriarRota(basico, 0, t0+D).permitido)
        assertTrue("criar rota 2 de 3", P.verificarCriarRota(basico, 2, t0+D).permitido)
        assertTrue("criar rota 3 de 3 bloqueia", !P.verificarCriarRota(basico, 3, t0+D).permitido)
        assertTrue("premium ilimitado", P.verificarCriarRota(premium, 500, t0+D).permitido)
        assertTrue("suspensa nao cria rota", !P.verificarCriarRota(basico, 0, t0+40*D).permitido)
        assertTrue("sem plano nao cria rota", !P.verificarCriarRota(null, 0, t0).permitido)
        assertTrue("aceitar ativa", P.verificarAceitarPassageiro(basico, t0+D).permitido)
        assertTrue("aceitar suspensa bloqueia", !P.verificarAceitarPassageiro(basico, t0+40*D).permitido)
        assertTrue("pagamentos ocultos suspensa", !P.podeVerPagamentos(basico, t0+40*D))
        assertTrue("pagamentos visiveis ativa", P.podeVerPagamentos(basico, t0+D))

        val (i1, f1) = P.novoCiclo(null, t0)
        assertTrue("primeiro ciclo comeca agora", i1 == t0 && f1 == t0 + 30*D)
        val (i2, f2) = P.novoCiclo(basico, t0+28*D)
        assertTrue("pagamento adiantado encadeia", i2 == t0+30*D && f2 == t0+60*D)
        val (i3, f3) = P.novoCiclo(basico, t0+32*D)
        assertTrue("pagamento na carencia encadeia", i3 == t0+30*D && f3 == t0+60*D)
        val (i4, f4) = P.novoCiclo(basico, t0+50*D)
        assertTrue("pagamento apos suspensa recomeca", i4 == t0+50*D && f4 == t0+80*D)

        assertTrue("renovacao ainda cedo", !P.deveGerarCobrancaDeRenovacao(basico, t0+20*D))
        assertTrue("renovacao 3 dias antes", P.deveGerarCobrancaDeRenovacao(basico, t0+27*D))
        assertTrue("renovacao nao com cancelamento", !P.deveGerarCobrancaDeRenovacao(basico.copy(cancelamentoAgendado=true), t0+29*D))
        assertTrue("renovacao nao se pendente", !P.deveGerarCobrancaDeRenovacao(basico.copy(status=StatusAssinatura.PENDENTE), t0+29*D))

        // pro-rata: Basico 99 -> Premium 199 = diferenca 100,00; metade do ciclo = 50,00
        assertTrue("prorata 15 dias", P.proRataCentavos(9900, 19900, t0+15*D, t0) == 5000L)
        assertTrue("prorata 30 dias", P.proRataCentavos(9900, 19900, t0+30*D, t0) == 10000L)
        assertTrue("prorata 0 dias", P.proRataCentavos(9900, 19900, t0, t0) == 0L)
        assertTrue("prorata downgrade zero", P.proRataCentavos(19900, 9900, t0+15*D, t0) == 0L)
        assertTrue("prorata mais de 30 dias limitado", P.proRataCentavos(9900, 19900, t0+45*D, t0) == 10000L)
        assertTrue("referencia", P.referenciaDoVencimento(t0).length == 8)

    }
}