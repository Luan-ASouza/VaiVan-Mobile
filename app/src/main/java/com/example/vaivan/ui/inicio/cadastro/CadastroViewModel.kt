package com.example.vaivan.ui.inicio.cadastro

import androidx.lifecycle.ViewModel
import com.example.vaivan.data.models.CadastroUsuario

class CadastroViewModel : ViewModel() {

    var cadastro = CadastroUsuario()

    fun definirCredenciais(
        email: String,
        telefone: String,
        senha: String
    ) {
        cadastro.usuario = cadastro.usuario.copy(email = email, telefone = telefone)
        cadastro.senha = senha
    }

    fun definirDadosPessoais(
        nome: String,
        cpf: String,
        dataNascimento: String
    ) {
        cadastro.usuario = cadastro.usuario.copy(nome = nome, cpf = cpf, dataNascimento = dataNascimento)
    }

    fun definirCodigoVerificacao() {
        cadastro.usuario = cadastro.usuario.copy(emailConfirmado = true)
    }

    fun definirEndereco(
        cep: String,
        estado: String,
        cidade: String,
        bairro: String,
        rua: String,
        numero: String,
        complemento: String
    ) {
        cadastro.usuario = cadastro.usuario.copy(
            cep = cep,
            estado = estado,
            cidade = cidade,
            bairro = bairro,
            rua = rua,
            numero = numero,
            complemento = complemento
        )
    }
}
