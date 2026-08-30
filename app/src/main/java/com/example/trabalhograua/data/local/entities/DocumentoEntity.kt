package com.example.trabalhograua.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documentos",
    indices = [Index(value = ["motoristaId"]), Index(value = ["veiculoId"])]
)
data class DocumentoEntity(
    @PrimaryKey val id: String = "",
    val tipo: String = "",
    val arquivo: String = "",
    val dataEnvio: String = "",
    val statusValidacao: String = "",
    val motoristaId: String = "",
    val veiculoId: String = "",   // <-- NOVO CAMPO
    val lastUpdated: Long = 0L
)