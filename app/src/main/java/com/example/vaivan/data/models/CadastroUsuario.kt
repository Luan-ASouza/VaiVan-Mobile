package com.example.vaivan.data.models

import com.example.vaivan.data.local.entities.UsuarioEntity

data class CadastroUsuario(
    var usuario: UsuarioEntity = UsuarioEntity(),
    var senha: String = ""
)