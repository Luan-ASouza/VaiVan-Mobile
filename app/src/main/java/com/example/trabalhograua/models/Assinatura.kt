package com.vaivan.app.models

data class Assinatura(
    val id: Int = 0,
    val dataInicio: String = "",
    val dataFim: String = "",
    val status: String = "",
    val planoId: Int = 0,
    val responsavelId: Int = 0,
    val pagamentosIds: List<Int> = emptyList()
)
