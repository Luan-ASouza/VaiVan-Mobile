package com.example.vaivan.data.models



data class Passageiro(
    val id: String = "",
    val nome: String = "",
    val dataNascimento: String = "",

    val necessidadesEspeciais: Boolean = false,
    val descricaoNecessidades: String? = null,
    val observacoes: String? = null,

    val responsavelId: String? = null,
    val lastUpdated: Long = 0L,
)
