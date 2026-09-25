package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class UsuarioEntity(

    @PrimaryKey
    val id: String = "",

    val nome: String = "",
    val cpf: String = "",
    val email: String = "",
    val telefone: String = "",
    val dataNascimento: String? = null,

    val cep: String = "",
    val estado: String = "",
    val cidade: String = "",
    val bairro: String = "",
    val rua: String = "",
    val numero: String = "",
    val complemento: String? = null,

    val status: String = "",
    val emailConfirmado: Boolean = false,
    val lastUpdated: Long = 0L
)
