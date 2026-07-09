package com.example.trabalhograua.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "localizacoes", indices = [Index(value = ["viagemId"])])
data class LocalizacaoEntity(
    @PrimaryKey val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val dataHora: String = "",
    val viagemId: String = "",
    val lastUpdated: Long = 0L
)
