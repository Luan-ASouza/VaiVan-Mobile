package com.example.trabalhograua.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "motoristas")
data class MotoristaEntity(
    @PrimaryKey val id: String = "",
    val nome: String = "",
    val cpf: String = "",
    val email: String = "",
    val telefone: String = "",
    val dataNascimento: String = "",
    val status: String = "",
    val cnh: String = "",
    val statusCadastro: String = "",
    val areaAtuacao: String = "",
    val lastUpdated: Long = 0L
)
