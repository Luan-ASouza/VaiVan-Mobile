package com.example.vaivan.cadastro

import com.example.vaivan.ui.responsavel.cadastro.CadastroResponsavel

object CadastroSession {

    var cadastroResponsavel = CadastroResponsavel()

    fun limpar() {
        cadastroResponsavel = CadastroResponsavel()
    }

}