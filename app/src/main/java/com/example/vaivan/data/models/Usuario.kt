package com.example.vaivan.data.models

/**
 * Classe base abstrata para os diferentes tipos de usuário do sistema
 * (Administrador, Responsavel, Passageiro, Motorista).
 * Corresponde à classe abstrata "Usuario" do diagrama de classes.
 */
data class Usuario(
    var id: String = "",

    var nome: String = "",
    var cpf: String = "",
    var email: String = "",
    var telefone: String = "",
    var dataNascimento: String? = null,

    var cep: String = "",
    var estado: String = "",
    var cidade: String = "",
    var bairro: String = "",
    var rua: String = "",
    var numero: String = "",
    var complemento: String? = null,

    var status: String = "",
    var statusMotorista: String? = null,
    var emailConfirmado: Boolean = false,
    var lastUpdated: Long = 0L
)
