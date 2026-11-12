package com.example.vaivan.data.models

import com.example.vaivan.domain.usecase.preco.TabelaPreco

/**
 * Tabela de preços do motorista, gravada em "tabelas_preco/{motoristaId}".
 * Vale para todas as rotas dele: o que muda de um passageiro para outro
 * é a distância, os dias e a modalidade.
 */
data class TabelaPrecoMotorista(
    val motoristaId: String = "",
    /** Valores de ida e volta: posição 0 = 1 dia por semana ... posição 4 = 5 dias. Em centavos. */
    val precoPorDiasCentavos: List<Long> = emptyList(),
    val percentualUmTrecho: Int = 60,
    val kmIncluidos: Int = 5,
    val valorKmExtraCentavos: Long = 0L,
    val diaVencimento: Int = 10,
    val atualizadoEm: Long = 0L
) {
    /** A tabela no formato do cálculo, ou null se faltar algum dos 5 valores. */
    fun paraCalculo(): TabelaPreco? =
        TabelaPreco(
            precoPorDiasCentavos = precoPorDiasCentavos,
            percentualUmTrecho = percentualUmTrecho,
            kmIncluidos = kmIncluidos,
            valorKmExtraCentavos = valorKmExtraCentavos
        ).takeIf { it.completa }
}