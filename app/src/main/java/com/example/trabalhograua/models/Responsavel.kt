package com.vaivan.app.models

data class Responsavel(
    override val id: Int = 0,
    override val nome: String = "",
    override val cpf: String = "",
    override val email: String = "",
    override val senha: String = "",
    override val telefone: String = "",
    override val dataNascimento: String = "",
    override val status: String = "",
    val emailConfirmado: Boolean = false,

    // Relacionamentos (1 Responsavel -> N Passageiros vinculados, N Solicitações, N Viagens acompanhadas)
    val passageirosIds: List<Int> = emptyList(),
    val solicitacoesVagaIds: List<Int> = emptyList()
) : Usuario(id, nome, cpf, email, senha, telefone, dataNascimento, status)
