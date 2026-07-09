package com.vaivan.app.models

data class VinculoResponsavel(
    val id: Int = 0,
    val grauParentesco: String = "",
    val status: String = "",
    val dataSolicitacao: String = "",
    val responsavelId: Int = 0,
    val passageiroId: Int = 0
)
