package com.example.vaivan.core.validation

import android.util.Patterns
import java.util.Calendar

object CadastroValidator {

    fun emailValido(email: String): Boolean {

        return email.isNotBlank() &&
                Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches()
    }

    fun telefoneValido(telefone: String): Boolean {

        val numeros =
            telefone.filter { it.isDigit() }

        return numeros.length in 10..11
    }

    fun senhaValida(senha: String): Boolean {

        return senha.length >= 8 &&
                senha.any { it.isDigit() } &&
                senha.any {
                    !it.isLetterOrDigit()
                }
    }

    fun senhasConferem(
        senha: String,
        confirmacao: String
    ): Boolean {

        return senha == confirmacao
    }

    fun nomeValido(nome: String): Boolean {

        return nome.trim().length >= 3
    }

    fun cpfValido(cpf: String): Boolean {

        val cpfLimpo =
            cpf.filter { it.isDigit() }

        if (cpfLimpo.length != 11) {
            return false
        }

        if (
            cpfLimpo.all {
                it == cpfLimpo[0]
            }
        ) {
            return false
        }

        var soma = 0

        for (i in 0 until 9) {

            soma +=
                cpfLimpo[i].digitToInt() *
                        (10 - i)
        }

        var resto =
            soma % 11

        val primeiroDigito =
            if (resto < 2) {
                0
            } else {
                11 - resto
            }

        if (
            primeiroDigito !=
            cpfLimpo[9].digitToInt()
        ) {
            return false
        }

        soma = 0

        for (i in 0 until 10) {

            soma +=
                cpfLimpo[i].digitToInt() *
                        (11 - i)
        }

        resto =
            soma % 11

        val segundoDigito =
            if (resto < 2) {
                0
            } else {
                11 - resto
            }

        return segundoDigito ==
                cpfLimpo[10].digitToInt()
    }

    fun dataValida(
        dia: Int,
        mes: Int,
        ano: Int
    ): Boolean {

        return try {

            val calendario =
                Calendar.getInstance()

            calendario.isLenient = false

            calendario.set(
                ano,
                mes,
                dia,
                0,
                0,
                0
            )

            calendario.set(
                Calendar.MILLISECOND,
                0
            )

            calendario.time

            true

        } catch (
            e: IllegalArgumentException
        ) {

            false
        }
    }

    fun idadeValida(
        dataNascimento: Calendar,
        idadeMinima: Int
    ): Boolean {

        val hoje =
            Calendar.getInstance()

        var idade =
            hoje.get(Calendar.YEAR) -
                    dataNascimento.get(Calendar.YEAR)

        if (
            hoje.get(Calendar.MONTH) <
            dataNascimento.get(Calendar.MONTH) ||

            (
                    hoje.get(Calendar.MONTH) ==
                            dataNascimento.get(Calendar.MONTH) &&

                            hoje.get(Calendar.DAY_OF_MONTH) <
                            dataNascimento.get(Calendar.DAY_OF_MONTH)
                    )
        ) {

            idade--
        }

        return idade >= idadeMinima
    }
}