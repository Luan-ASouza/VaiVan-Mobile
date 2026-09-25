package com.example.vaivan.data.mapper

import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.models.Usuario

fun Usuario.toEntity(): UsuarioEntity {
    return UsuarioEntity(
        id = id,
        nome = nome,
        cpf = cpf ?: "",
        email = email,
        telefone = telefone ?: "",
        dataNascimento = dataNascimento,
        cep = cep,
        estado = estado,
        cidade = cidade,
        bairro = bairro,
        rua = rua,
        numero = numero,
        complemento = complemento,
        status = status,
        emailConfirmado = emailConfirmado,
        lastUpdated = lastUpdated
    )
}

fun UsuarioEntity.toModel(): Usuario {
    return Usuario(
        id = id,
        nome = nome,
        cpf = cpf,
        email = email,
        telefone = telefone,
        dataNascimento = dataNascimento,
        cep = cep,
        estado = estado,
        cidade = cidade,
        bairro = bairro,
        rua = rua,
        numero = numero,
        complemento = complemento,
        status = status,
        emailConfirmado = emailConfirmado,
        lastUpdated = lastUpdated
    )
}