package com.vaivan.app.models

data class Passageiro(
    override val id: Int = 0,
    override val nome: String = "",
    override val cpf: String = "",
    override val email: String = "",
    override val senha: String = "",
    override val telefone: String = "",
    override val dataNascimento: String = "",
    override val status: String = "",
    val matricula: String = "",
    val maiorIdade: Boolean = false,

    // Relacionamentos
    val responsavelId: Int? = null,
    val rotaId: Int? = null
) : Usuario(id, nome, cpf, email, senha, telefone, dataNascimento, status)
