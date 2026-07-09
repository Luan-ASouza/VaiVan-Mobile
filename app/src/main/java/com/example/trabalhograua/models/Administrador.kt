package com.vaivan.app.models

data class Administrador(
    override val id: Int = 0,
    override val nome: String = "",
    override val cpf: String = "",
    override val email: String = "",
    override val senha: String = "",
    override val telefone: String = "",
    override val dataNascimento: String = "",
    override val status: String = "",
    val nivelAcesso: String = "" // ex: SUPER_ADMIN, SUPORTE
) : Usuario(id, nome, cpf, email, senha, telefone, dataNascimento, status)
