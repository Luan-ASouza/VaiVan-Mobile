package com.vaivan.app.models

data class Mensagem(
    val id: Int = 0,
    val conteudo: String = "",
    val dataEnvio: String = "",
    val lida: Boolean = false,
    val chatId: Int = 0,
    val remetenteId: Int = 0
)
