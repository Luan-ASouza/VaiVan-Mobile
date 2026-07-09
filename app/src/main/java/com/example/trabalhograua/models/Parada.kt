package com.vaivan.app.models

data class Parada(
    val id: Int = 0,
    val endereco: String = "",
    val ordem: Int = 0,
    val horarioPrevisto: String = "", // formato HH:mm
    val rotaId: Int = 0
)
