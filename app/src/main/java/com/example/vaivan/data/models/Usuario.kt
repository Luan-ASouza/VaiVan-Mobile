package com.vaivan.app.models

/**
 * Classe base abstrata para os diferentes tipos de usuário do sistema
 * (Administrador, Responsavel, Passageiro, Motorista).
 * Corresponde à classe abstrata "Usuario" do diagrama de classes.
 */
abstract class Usuario(
    open val id: Int = 0,
    open val nome: String = "",
    open val cpf: String = "",
    open val email: String = "",
    open val senha: String = "",
    open val telefone: String = "",
    open val dataNascimento: String = "", // formato ISO-8601 (yyyy-MM-dd)
    open val status: String = "" // ex: ATIVO, INATIVO, PENDENTE
)
