package com.vaivan.app.models

data class EventoRota(
    val id: Int = 0,
    val tipo: String = "", // ex: EMBARQUE, DESEMBARQUE, ATRASO
    val descricao: String = "",
    val dataHora: String = "",
    val viagemId: Int = 0
)
