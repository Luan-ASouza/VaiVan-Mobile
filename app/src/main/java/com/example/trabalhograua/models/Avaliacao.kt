package com.vaivan.app.models

data class Avaliacao(
    val id: Int = 0,
    val nota: Int = 0,
    val comentario: String = "",
    val status: String = "",
    val dataAvaliacao: String = "",
    val autorId: Int = 0,
    val motoristaId: Int = 0
)
