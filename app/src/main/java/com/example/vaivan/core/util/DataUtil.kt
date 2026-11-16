package com.example.vaivan.core.util

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

    // ---------------------------------------------------------
    // CHAT: rótulos de dia/horário das mensagens
    // ---------------------------------------------------------

    private val localeBr = Locale("pt", "BR")

    /** Dias de calendário entre [momento] e [agora] (0 = hoje, 1 = ontem...). */
    private fun diasAtras(momento: Long, agora: Long): Int {
        fun inicioDoDia(millis: Long) = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val inicioMomento = inicioDoDia(momento)
        val inicioAgora = inicioDoDia(agora)
        var dias = 0
        while (inicioMomento.before(inicioAgora) && dias < 8) {
            inicioMomento.add(Calendar.DAY_OF_YEAR, 1)
            dias++
        }
        return dias
    }

    /** Os dois instantes caem no mesmo dia (separador de data do chat). */
    fun mesmoDia(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    /** "09:24". */
    fun hora(momento: Long): String = SimpleDateFormat("HH:mm", localeBr).format(momento)

    /** Separador entre os dias da conversa: "Hoje", "Ontem", "Segunda-feira" (até 6 dias) ou "12/11/2026". */
    fun rotuloDiaChat(momento: Long, agora: Long = System.currentTimeMillis()): String =
        when (val dias = diasAtras(momento, agora)) {
            0 -> "Hoje"
            1 -> "Ontem"
            in 2..6 -> SimpleDateFormat("EEEE", localeBr).format(momento)
                .replaceFirstChar { it.uppercase() }
            else -> SimpleDateFormat("dd/MM/yyyy", localeBr).format(momento)
        }

    /** Horário na lista de conversas: hoje a hora, "Ontem", o dia da semana abreviado ou "12/11". */
    fun rotuloCurtoChat(momento: Long, agora: Long = System.currentTimeMillis()): String =
        when (diasAtras(momento, agora)) {
            0 -> hora(momento)
            1 -> "Ontem"
            in 2..6 -> SimpleDateFormat("EEE", localeBr).format(momento)
                .removeSuffix(".").replaceFirstChar { it.uppercase() }
            else -> SimpleDateFormat("dd/MM", localeBr).format(momento)
        }

    /** Chaves dos dias como são gravadas em `diasSemana`, na ordem da semana. */
    val ORDEM_DIAS_SEMANA = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM")

    /**
     * Coloca os dias na ordem da semana (SEG, TER, QUA...), sem repetidos e sem vazios.
     * Os dias são guardados na ordem em que o motorista tocou nos botões, então
     * sempre ordene antes de gravar ou de exibir. Chave desconhecida vai para o fim.
     */
    fun ordenarDiasSemana(dias: Iterable<String>): List<String> =
        dias.map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { ORDEM_DIAS_SEMANA.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }

    /** Mesmo que [ordenarDiasSemana], partindo do texto gravado (ex.: "TER,SEG,QUA"). */
    fun ordenarDiasSemana(diasSemana: String): List<String> =
        ordenarDiasSemana(diasSemana.split(Regex("[,;/\\s]+")))
}
