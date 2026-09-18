package com.vaivan.app.models

data class Chat(
    val id: Int = 0,
    val status: String = "",
    val dataCriacao: String = "",

    // Relacionamento 1 Chat -> N Mensagens
    val mensagens: List<Mensagem> = emptyList(),
    val participantesIds: List<Int> = emptyList()
)
