package com.example.vaivan.data.models

/**
 * Classe base abstrata para os diferentes tipos de usuário do sistema
 * (Administrador, Responsavel, Passageiro, Motorista).
 * Corresponde à classe abstrata "Usuario" do diagrama de classes.
 */
class Usuario(
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
