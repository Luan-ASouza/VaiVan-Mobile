package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Trilha de eventos de negócio de uma viagem (não é posição de GPS). Alimenta
 * o histórico exibido ao responsável e, na Etapa 5, as notificações push —
 * cada evento é notificado uma única vez, evitando repetição.
 */
@Entity(
    tableName = "eventos_viagem",
    indices = [Index(value = ["viagemId"]), Index(value = ["paradaViagemId"])]
)
data class EventoViagemEntity(
    @PrimaryKey val id: String = "",
    val viagemId: String = "",
    val paradaViagemId: String? = null,

    // INICIO | APROXIMACAO | CHEGADA | EMBARQUE | NAO_EMBARCOU | DESEMBARQUE | FINALIZADA
    val tipo: String = "",

    val ocorridoEm: Long = 0L,
    val lastUpdated: Long = 0L
)
