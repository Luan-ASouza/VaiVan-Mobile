package com.example.trabalhograua.chat

data class Mensagem(
    val id: String = "",
    val remetenteId: String = "",
    val texto: String = "",
    val enviadoEm: Long = 0L,
    val lida: Boolean = false
)