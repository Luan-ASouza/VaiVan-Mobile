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
        cadastro.usuario.email = email
        cadastro.usuario.telefone = telefone
        cadastro.senha = senha
    }

    fun definirDadosPessoais(
        nome: String,
        cpf: String,
        dataNascimento: String
    ) {
        cadastro.usuario.nome = nome
        cadastro.usuario.cpf = cpf
        cadastro.usuario.dataNascimento = dataNascimento
    }

    fun definirCodigoVerificacao() {
        cadastro.usuario.emailConfirmado = true
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
        cadastro.usuario.cep = cep
        cadastro.usuario.estado = estado
        cadastro.usuario.cidade = cidade
        cadastro.usuario.bairro = bairro
        cadastro.usuario.rua = rua
        cadastro.usuario.numero = numero
        cadastro.usuario.complemento = complemento
    }
}