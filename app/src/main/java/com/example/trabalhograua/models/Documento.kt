package com.vaivan.app.models

data class Documento(
    val id: Int = 0,
    val tipo: String = "",
    val arquivo: String = "", // URL/URI do arquivo
    val dataEnvio: String = "",
    val statusValidacao: String = "",
    val motoristaId: Int = 0
)
