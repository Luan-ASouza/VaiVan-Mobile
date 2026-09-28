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
}