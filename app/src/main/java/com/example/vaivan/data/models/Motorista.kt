package com.vaivan.app.models

data class Motorista(
    override val id: Int = 0,
    override val nome: String = "",
    override val cpf: String = "",
    override val email: String = "",
    override val senha: String = "",
    override val telefone: String = "",
    override val dataNascimento: String = "",
    override val status: String = "",
    val cnh: String = "",
    val statusCadastro: String = "",
    val areaAtuacao: String = "",

    // Relacionamentos
    val veiculosIds: List<Int> = emptyList(),
    val documentosIds: List<Int> = emptyList(),
    val rotasIds: List<Int> = emptyList()
) : Usuario(id, nome, cpf, email, senha, telefone, dataNascimento, status)
