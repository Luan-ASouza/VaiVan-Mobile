package com.example.vaivan.ui.inicio.cadastro

import androidx.lifecycle.ViewModel
import com.example.vaivan.data.models.CadastroUsuario

class CadastroViewModel : ViewModel() {

    var cadastro = CadastroUsuario()

    /** O e-mail da identificação já existe no Auth — a etapa de senha vira
     * "continuar cadastro" (pede a senha antiga em vez de criar uma). */
    var emailJaCadastrado = false

    /** Etapa 1 — identificação (nome, sobrenome e e-mail). */
    fun definirIdentificacao(
        nome: String,
        sobrenome: String,
        email: String
    ) {
        cadastro.usuario = cadastro.usuario.copy(nome = nome, sobrenome = sobrenome, email = email)
    }

    /** Etapa 2 — credenciais (senha). */
    fun definirSenha(senha: String) {
        cadastro.senha = senha
    }

    /** Etapa 2.5 — verificação real do e-mail (link do Firebase). */
    fun definirVerificacaoEmail() {
        cadastro.usuario = cadastro.usuario.copy(emailConfirmado = true)
    }

    /** Etapa 3 — informações pessoais (telefone, CPF e, se ainda faltarem,
     * nome/sobrenome). */
    fun definirDadosPessoais(
        nome: String,
        sobrenome: String,
        telefone: String,
        cpf: String
    ) {
        cadastro.usuario = cadastro.usuario.copy(
            nome = nome,
            sobrenome = sobrenome,
            telefone = telefone,
            cpf = cpf
        )
    }

    /** Etapa 3 — data de nascimento (tela própria). */
    fun definirDataNascimento(dataNascimento: String) {
        cadastro.usuario = cadastro.usuario.copy(dataNascimento = dataNascimento)
    }

    /** Etapa 4 — endereço. */
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

    /** Etapa 5 — consentimentos. */
    fun definirConsentimento(
        aceitouTermos: Boolean,
        aceitaComunicacoes: Boolean
    ) {
        cadastro.usuario = cadastro.usuario.copy(
            aceitouTermos = aceitouTermos,
            aceitaComunicacoes = aceitaComunicacoes
        )
    }
}
