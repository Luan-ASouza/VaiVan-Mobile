package com.example.trabalhograua.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp

@Entity(tableName = "responsaveis")
data class ResponsavelEntity(

    @PrimaryKey
    val id: String = "",

    val nome: String = "",
    val cpf: String = "",
    val email: String = "",
    val telefone: String = "",
    val dataNascimento: Timestamp? = null,

    val cep: String = "",
    val estado: String = "",
    val cidade: String = "",
    val bairro: String = "",
    val rua: String = "",
    val numero: String = "",
    val complemento: String = "",

    val status: String = "",
    val emailConfirmado: Boolean = false,
    val lastUpdated: Long = 0L
)
