package com.example.vaivan.core.util

/**
 * ETA ao vivo por parada (ordem -> horário previsto em epoch ms), guardado
 * como texto simples "ordem=epochMs;ordem=epochMs" em
 * LocalizacaoViagemEntity.etasParadas — evita um TypeConverter de Map no
 * Room só pra isso.
 */
object EtaParadasUtil {

    fun codificar(etas: Map<Int, Long>): String =
        etas.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decodificar(texto: String): Map<Int, Long> {
        if (texto.isBlank()) return emptyMap()
        return texto.split(";").mapNotNull { par ->
            val partes = par.split("=")
            val ordem = partes.getOrNull(0)?.toIntOrNull()
            val eta = partes.getOrNull(1)?.toLongOrNull()
            if (ordem == null || eta == null) null else ordem to eta
        }.toMap()
    }
}
