package com.vaivan.app.models

data class Viagem(
    val id: Int = 0,
    val data: String = "", // yyyy-MM-dd
    val horaInicio: String = "", // HH:mm
    val horaFim: String = "", // HH:mm
    val status: String = "",

    val rotaId: Int = 0,
    val motoristaId: Int = 0,
    val eventos: List<EventoRota> = emptyList(),
    val localizacoesIds: List<Int> = emptyList()
)
