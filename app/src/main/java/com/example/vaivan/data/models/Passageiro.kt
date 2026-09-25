package com.example.vaivan.data.models



data class Passageiro(
    val id: String = "",
    val nome: String = "",
    val cpf: String? = null,
    val telefone: String? = null,
    val dataNascimento: String = "",

    val matricula: String = "",
    val necessidadesEspeciais: Boolean = false,
    val descricaoNecessidades: String? = null,
    val observacoes: String? = null,

    val responsavelId: String? = null,
    val motoristaId: String? = null,
    val pontoEmbarqueId: String? = null,

    val rotaId: String? = null,
    val lastUpdated: Long = 0L,
)
