package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "passageiros",
    indices = [Index(value = ["responsavelId"])]
)
data class PassageiroEntity(
    @PrimaryKey
    val id: String = "",
    val nome: String = "",
    val dataNascimento: String = "",

    val necessidadesEspeciais: Boolean = false,
    val descricaoNecessidades: String? = null,
    val observacoes: String? = null,

    val responsavelId: String? = null,

    val fotoUrl: String? = null,
    val lastUpdated: Long = 0L,
)
