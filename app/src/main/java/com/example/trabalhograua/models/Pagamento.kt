package com.vaivan.app.models

data class Pagamento(
    val id: Int = 0,
    val valor: Double = 0.0,
    val status: String = "",
    val dataPagamento: String = "",
    val formaPagamento: String = "",
    val tipoPagamento: String = "",
    val assinaturaId: Int = 0,
    val comprovanteId: Int? = null
)
