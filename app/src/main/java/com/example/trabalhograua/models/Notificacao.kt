package com.vaivan.app.models

data class Notificacao(
    val id: Int = 0,
    val titulo: String = "",
    val mensagem: String = "",
    val tipo: String = "",
    val dataEnvio: String = "",
    val lida: Boolean = false,
    val usuarioId: Int = 0
)
