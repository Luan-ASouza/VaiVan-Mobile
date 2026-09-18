package com.vaivan.app.models

data class Veiculo(
    val id: Int = 0,
    val placa: String = "",
    val modelo: String = "",
    val anoFabricacao: Int = 0,
    val capacidadePassageiros: Int = 0,
    val status: String = "",
    val motoristaId: Int = 0
)
