package com.example.trabalhograua.cadastro

data class Usuario(

    var nome: String = "",
    var email: String = "",
    var telefone: String = "",
    var cpf: String = "",
    var nascimento: String = "",
    var senha: String = "",
    var tipo: String = ""

) {
    constructor(
        nome: String,
        email: String,
        telefone: String,
        senha: String,
        Responsavel: String
    ) : this() {

    }
}