package com.example.vaivan.domain.usecase.preco

import kotlin.math.ceil

/**
 * Tabela de preços de uma rota, definida pelo motorista.
 * Os valores por dia são de IDA E VOLTA. Só ida ou só volta pagam um percentual deles.
 */
data class TabelaPreco(
        /** Posição 0 = 1 dia por semana ... posição 4 = 5 dias. Em centavos. */
        val precoPorDiasCentavos: List<Long>,
        /** Quanto só ida (ou só volta) custa, em % do valor de ida e volta. Ex.: 60. */
        val percentualUmTrecho: Int,
        /** Distância (casa → escola) já incluída no preço. */
        val kmIncluidos: Int,
        /** Valor mensal cobrado por km que passar do incluído. Em centavos. */
        val valorKmExtraCentavos: Long
) {
    /** Só dá para calcular preço com os 5 valores preenchidos. */
    val completa: Boolean
    get() = precoPorDiasCentavos.size == 5 && precoPorDiasCentavos.all { it > 0 }
}

/** O resultado do cálculo, com o detalhamento para mostrar ao responsável e ao motorista. */
data class Orcamento(
        val valorCentavos: Long,
        val modalidade: String,
        val dias: Int,
        val distanciaKm: Int,
        val kmExtras: Int,
        val valorDiasCentavos: Long,
        val valorDistanciaCentavos: Long,
        val percentual: Int
)

/**
 * Cálculo da mensalidade de um passageiro:
 *   (valor dos dias + km excedentes × valor por km) × percentual da modalidade
 * Sem Android e sem Firebase, para poder ser testada (veja PrecoPolicyTest).
 */
object PrecoPolicy {

    const val IDA = "IDA"
    const val VOLTA = "VOLTA"
    const val IDA_E_VOLTA = "IDA_E_VOLTA"

    /**
     * A modalidade que vale para o preço. Se a rota só faz ida (ou só volta), é isso
     * que o passageiro vai usar, mesmo que tenha pesquisado ida e volta.
     */
    fun modalidadeEfetiva(modalidadeDesejada: String, tipoTrajetoDaRota: String): String =
            if (tipoTrajetoDaRota == IDA_E_VOLTA) modalidadeDesejada.uppercase() else tipoTrajetoDaRota

    /** Dias que o passageiro paga: os que pediu E que a rota atende. Nenhum pedido = todos da rota. */
    fun diasCobrados(diasDesejados: Set<String>, diasDaRota: Set<String>): Int {
        val emComum = if (diasDesejados.isEmpty()) diasDaRota else diasDesejados.intersect(diasDaRota)
        return emComum.size.coerceIn(1, 5)
    }

    /** Arredonda para cima: 5,1 km conta como 6 km. */
    fun distanciaKm(metros: Double): Int =
            if (metros.isNaN() || metros <= 0.0) 0 else ceil(metros / 1000.0).toInt()

    fun calcular(
            tabela: TabelaPreco,
            modalidade: String,
            dias: Int,
            distanciaMetros: Double
    ): Orcamento {
        require(tabela.completa) { "Tabela de preços incompleta." }

        val qtdDias = dias.coerceIn(1, 5)
        val valorDias = tabela.precoPorDiasCentavos[qtdDias - 1]

        val km = distanciaKm(distanciaMetros)
        val kmExtras = (km - tabela.kmIncluidos).coerceAtLeast(0)
        val valorDistancia = kmExtras * tabela.valorKmExtraCentavos

        val percentual =
        if (modalidade.uppercase() == IDA_E_VOLTA) 100
        else tabela.percentualUmTrecho.coerceIn(1, 100)

        val bruto = (valorDias + valorDistancia) * percentual / 100.0

        return Orcamento(
                valorCentavos = arredondarParaReais(bruto),
                modalidade = modalidade.uppercase(),
                dias = qtdDias,
                distanciaKm = km,
                kmExtras = kmExtras,
                valorDiasCentavos = valorDias,
                valorDistanciaCentavos = valorDistancia,
                percentual = percentual
        )
    }

    /** R$ 188,60 vira R$ 189,00: mensalidade sem centavos é mais fácil de pagar e conferir. */
    private fun arredondarParaReais(centavos: Double): Long =
            Math.round(centavos / 100.0) * 100

    fun rotuloModalidade(modalidade: String): String = when (modalidade.uppercase()) {
        IDA -> "Só ida"
        VOLTA -> "Só volta"
        else -> "Ida e volta"
    }
}