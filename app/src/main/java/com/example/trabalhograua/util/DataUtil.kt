package com.example.trabalhograua.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Funções auxiliares de data usadas no cadastro de passageiros (RF3/RF4).
 *
 * Guardamos a data de nascimento como texto no formato ISO "yyyy-MM-dd"
 * dentro do banco (Firestore/Room), pois esse formato ordena e compara
 * corretamente como string. Nas telas, o usuário digita no formato
 * brasileiro "dd/MM/yyyy".
 */
object DataUtil {

    private fun formatoBrasileiro() = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply {
        isLenient = false // não aceita datas "inventadas" tipo 31/02/2020
    }

    private fun formatoIso() = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).apply {
        isLenient = false
    }

    /**
     * Converte "dd/MM/yyyy" -> "yyyy-MM-dd".
     * Retorna null se a data for inválida ou estiver no futuro.
     */
    fun paraIso(dataBrasileira: String): String? {
        return try {
            val data = formatoBrasileiro().parse(dataBrasileira) ?: return null

            if (data.after(Calendar.getInstance().time)) {
                return null // data de nascimento não pode ser no futuro
            }

            formatoIso().format(data)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converte "yyyy-MM-dd" -> "dd/MM/yyyy" para exibir na tela.
     */
    fun paraBrasileiro(dataIso: String): String {
        return try {
            val data = formatoIso().parse(dataIso) ?: return ""
            formatoBrasileiro().format(data)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Calcula a idade (em anos completos) a partir de uma data "yyyy-MM-dd".
     * Retorna -1 se a data for inválida, para o chamador poder tratar o erro.
     */
    fun calcularIdade(dataNascimentoIso: String): Int {
        val nascimento = try {
            formatoIso().parse(dataNascimentoIso) ?: return -1
        } catch (e: Exception) {
            return -1
        }

        val calNascimento = Calendar.getInstance().apply { time = nascimento }
        val calHoje = Calendar.getInstance()

        var idade = calHoje.get(Calendar.YEAR) - calNascimento.get(Calendar.YEAR)

        // Se ainda não fez aniversário este ano, subtrai 1
        if (calHoje.get(Calendar.DAY_OF_YEAR) < calNascimento.get(Calendar.DAY_OF_YEAR)) {
            idade--
        }

        return idade
    }
}