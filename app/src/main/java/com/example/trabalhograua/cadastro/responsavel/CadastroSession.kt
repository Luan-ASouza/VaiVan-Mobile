package com.example.trabalhograua.cadastro

import com.example.trabalhograua.cadastro.responsavel.CadastroResponsavel

object CadastroSession {

    var cadastroResponsavel = CadastroResponsavel()

    fun limpar() {
        cadastroResponsavel = CadastroResponsavel()
    }

}