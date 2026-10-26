package com.example.vaivan.data.models
data class Plano(
    val id: String = "",
    val nome: String = "",
    val valorCentavos: Long = 0L,
    /** Quantidade máxima de rotas ATIVAS. Ignorado quando [ilimitado] = true. */
    val limiteRotas: Int = 0,
    val ilimitado: Boolean = false,
    val beneficios: List<String> = emptyList(),
    val ativo: Boolean = true,
    val ordem: Int = 0
)

/** Assinatura atual do motorista. O id do documento é o id do motorista. */
data class Assinatura(
    val motoristaId: String = "",

    // Cópia (snapshot) do plano contratado: o limite não muda se o catálogo mudar.
    val planoId: String = "",
    val planoNome: String = "",
    val valorCentavos: Long = 0L,
    val limiteRotas: Int = 0,
    val ilimitado: Boolean = false,

    /** Veja [StatusAssinatura]. É o estado "gravado"; o estado efetivo vem da AssinaturaPolicy. */
    val status: String = StatusAssinatura.PENDENTE,

    val cicloInicio: Long = 0L,
    /** Vencimento do ciclo atual. */
    val proximaCobranca: Long = 0L,

    /** Cancelamento pedido: vale até o fim do ciclo já pago. */
    val cancelamentoAgendado: Boolean = false,
    /** Downgrade pedido: vale a partir da próxima renovação. */
    val planoAgendadoId: String? = null,

    val criadoEm: Long = 0L,
    val atualizadoEm: Long = 0L
)

/** Cobrança da assinatura (mensalidade ou diferença de upgrade). */
data class Pagamento(
    val id: String = "",
    val motoristaId: String = "",
    val assinaturaId: String = "",

    /** Veja [TipoPagamento]. */
    val tipo: String = TipoPagamento.MENSALIDADE,
    val planoId: String = "",
    val planoNome: String = "",
    val descricao: String = "",
    val valorCentavos: Long = 0L,

    /** Veja [StatusPagamento]. */
    val status: String = StatusPagamento.PENDENTE,
    val vencimento: Long = 0L,
    val pagoEm: Long? = null,
    val forma: String = "",

    val criadoEm: Long = 0L
)

object StatusAssinatura {
    const val PENDENTE = "PENDENTE"     // contratada, aguardando o primeiro pagamento
    const val ATIVA = "ATIVA"
    const val SUSPENSA = "SUSPENSA"     // suspensa pelo administrador
    const val CANCELADA = "CANCELADA"
    const val EXPIRADA = "EXPIRADA"
}

object StatusPagamento {
    const val PENDENTE = "PENDENTE"
    const val PAGO = "PAGO"
    const val RECUSADO = "RECUSADO"
    const val CANCELADO = "CANCELADO"
}

object TipoPagamento {
    const val MENSALIDADE = "MENSALIDADE"
    const val UPGRADE = "UPGRADE"
}

/** Catálogo usado enquanto o administrador não tiver cadastrado planos no Firestore. */
object PlanoCatalogo {

    val padrao: List<Plano> = listOf(
        Plano(
            id = "basico",
            nome = "Plano Básico",
            valorCentavos = 9_900L,
            limiteRotas = 3,
            ilimitado = false,
            beneficios = listOf("Até 3 rotas ativas", "Suporte básico"),
            ativo = true,
            ordem = 1
        ),
        Plano(
            id = "premium",
            nome = "Plano Premium",
            valorCentavos = 19_900L,
            limiteRotas = 0,
            ilimitado = true,
            beneficios = listOf("Rotas ilimitadas", "Suporte 24h"),
            ativo = true,
            ordem = 2
        )
    )

    fun porId(id: String): Plano? = padrao.firstOrNull { it.id == id }
}