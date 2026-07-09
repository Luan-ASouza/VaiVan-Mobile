package com.vaivan.app.models

data class Comprovante(
    val id: Int = 0,
    val codigo: String = "",
    val dataGeracao: String = "",
    val urlArquivo: String = "",
    val pagamentoId: Int = 0
)
