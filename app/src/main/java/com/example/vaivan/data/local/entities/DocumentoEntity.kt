package com.example.vaivan.data.local.entities

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
    val arquivoVerso: String? = null,
    val modalidadeCnh: String? = null,
    val dataEnvio: String = "",
    val statusValidacao: String = "",
    val motivoRejeicao: String? = null,
    val analisadoEm: Long? = null,
    val analisadoPor: String? = null,
    val motoristaId: String = "",
    val veiculoId: String = "",
    val lastUpdated: Long = 0L
)