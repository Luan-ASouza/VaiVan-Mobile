package com.example.vaivan.core.util

import java.text.Normalizer

object TextoUtil {

    /** "São Cristóvão " -> "sao cristovao" */
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .trim()

    /** Chave única do bairro. Inclui a cidade porque "Centro" existe em toda cidade. */
    fun chaveBairro(bairro: String, cidade: String): String =
        normalizar("$bairro - $cidade").replace(",", " ")

    // =========================================================
    // MASCARAMENTO PARA EXIBIÇÃO (tela de perfil)
    // =========================================================

    private const val OCULTO = '•'

    /** "(54) 99123-9460" -> "(54) •••••-9460". Formato desconhecido volta como veio. */
    fun mascararTelefone(telefone: String): String {
        val digitos = telefone.filter { it.isDigit() }
        if (digitos.length !in 10..11) return telefone
        return "(${digitos.take(2)}) ${OCULTO.toString().repeat(digitos.length - 6)}-${digitos.takeLast(4)}"
    }

    /** "luan.souza@gmail.com" -> "lu***@gmail.com". */
    fun mascararEmail(email: String): String {
        val arroba = email.indexOf('@')
        if (arroba <= 0) return email
        return email.substring(0, arroba).take(2) + "***" + email.substring(arroba)
    }

    /** "123.456.789-09" -> "•••.456.789-••". */
    fun mascararCpf(cpf: String): String {
        val digitos = cpf.filter { it.isDigit() }
        if (digitos.length != 11) return cpf
        val oculto3 = OCULTO.toString().repeat(3)
        val oculto2 = OCULTO.toString().repeat(2)
        return "$oculto3.${digitos.substring(3, 6)}.${digitos.substring(6, 9)}-$oculto2"
    }
}