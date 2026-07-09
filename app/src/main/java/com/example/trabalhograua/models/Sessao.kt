package com.vaivan.app.models

data class Sessao(
    val idSessao: Int = 0,
    val token: String = "",
    val dataInicio: String = "",
    val dataExpiracao: String = "",
    val ativa: Boolean = false,
    val usuarioId: Int = 0
)
