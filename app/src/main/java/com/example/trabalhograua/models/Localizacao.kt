package com.vaivan.app.models

data class Localizacao(
    val id: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val dataHora: String = "",
    val viagemId: Int = 0
)
