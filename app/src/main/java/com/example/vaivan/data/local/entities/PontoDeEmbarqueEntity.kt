package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ponto_embarque", indices = [Index(value = ["viagemId"])])
data class PontoDeEmbarqueEntity(
    @PrimaryKey val id: String = "",

    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    val viagemId: String = "",
    val usuarioId: String? = null,

    val lastUpdated: Long = 0L
)
