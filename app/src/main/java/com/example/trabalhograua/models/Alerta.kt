package com.vaivan.app.models

data class Alerta(
    val id: Int = 0,
    val tipo: String = "", // ex: SOS, ATRASO, EMERGENCIA
    val descricao: String = "",
    val dataHora: String = "",
    val status: String = "",
    val viagemId: Int = 0,
    val emissorId: Int = 0
)
