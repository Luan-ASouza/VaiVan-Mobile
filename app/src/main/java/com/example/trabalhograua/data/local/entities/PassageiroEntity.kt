package com.example.trabalhograua.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "passageiros",
    indices = [Index(value = ["responsavelId"]), Index(value = ["rotaId"])]
)
data class PassageiroEntity(
    @PrimaryKey val id: String = "",
    val nome: String = "",
    val cpf: String = "",
    val email: String = "",
    val telefone: String = "",
    val dataNascimento: String = "",
    val status: String = "",
    val matricula: String = "",
    val maiorIdade: Boolean = false,
    val responsavelId: String? = null,
    val rotaId: String? = null,
    val lastUpdated: Long = 0L
)
