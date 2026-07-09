package com.example.trabalhograua.cadastro.responsavel

import com.google.firebase.Timestamp
import java.util.Date

data class CadastroResponsavel(

    var nome: String = "",

    var email: String = "",

    var telefone: String = "",

    var senha: String = "",

    var cpf: String = "",

    var dataNascimento: Date? = null,

    var endereco: Endereco = Endereco()

) {

    data class Endereco(

        var cep: String = "",

        var estado: String = "",

        var cidade: String = "",

        var bairro: String = "",

        var rua: String = "",

        var numero: String = "",

        var complemento: String? = null

    )

}