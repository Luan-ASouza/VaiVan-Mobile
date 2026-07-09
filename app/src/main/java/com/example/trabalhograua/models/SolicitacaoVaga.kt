package com.vaivan.app.models

data class SolicitacaoVaga(
    val id: Int = 0,
    val dataSolicitacao: String = "",
    val status: String = "",
    val observacao: String = "",
    val responsavelId: Int = 0,
    val passageiroId: Int = 0,
    val rotaId: Int? = null
)
