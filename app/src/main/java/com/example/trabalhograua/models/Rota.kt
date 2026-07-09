package com.vaivan.app.models

data class Rota(
    val id: Int = 0,
    val nome: String = "",
    val areaAtuacao: String = "",
    val turno: String = "",
    val status: String = "",

    val motoristaId: Int = 0,
    val paradas: List<Parada> = emptyList(),
    val passageirosIds: List<Int> = emptyList()
)
