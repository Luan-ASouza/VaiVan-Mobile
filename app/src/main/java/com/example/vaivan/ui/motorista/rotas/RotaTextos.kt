package com.example.vaivan.ui.motorista.rotas

/**
 * Textos de exibição dos campos da rota (turno, sentido, dias, duração,
 * bairros). Ficam num lugar só porque a lista, o detalhe e a criação/edição
 * da rota mostram os mesmos valores.
 */
object RotaTextos {

    val TURNOS = listOf("MANHA", "TARDE", "NOITE")
    val SENTIDOS = listOf("IDA", "VOLTA", "IDA_E_VOLTA")
    val DIAS = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM")

    fun turno(turno: String): String = when (turno) {
        "MANHA" -> "Manhã"
        "TARDE" -> "Tarde"
        "NOITE" -> "Noite"
        else -> turno
    }

    fun sentido(tipoTrajeto: String): String = when (tipoTrajeto) {
        "IDA" -> "Ida"
        "VOLTA" -> "Volta"
        "IDA_E_VOLTA" -> "Ida e volta"
        else -> tipoTrajeto
    }

    fun descricaoSentido(tipoTrajeto: String): String = when (tipoTrajeto) {
        "IDA" -> "Busca os alunos e leva até a escola."
        "VOLTA" -> "Busca os alunos na escola e leva de volta."
        "IDA_E_VOLTA" -> "Leva até a escola e traz de volta."
        else -> ""
    }

    /** Na volta, o ponto escolhido é onde a van DEIXA os alunos. */
    fun rotuloPonto(tipoTrajeto: String): String =
        if (tipoTrajeto == "VOLTA") "Ponto de desembarque" else "Ponto de embarque"

    fun diaCurto(dia: String): String = when (dia.uppercase()) {
        "SAB" -> "Sáb"
        else -> dia.take(3).lowercase().replaceFirstChar { it.uppercase() }
    }

    /** "Seg, Ter e Qua" / "Todos os dias úteis". */
    fun dias(dias: List<String>): String {
        val ordenados = DIAS.filter { it in dias.map { d -> d.uppercase() } }
        if (ordenados.isEmpty()) return "Nenhum"
        if (ordenados == DIAS.take(5)) return "Seg a Sex"
        if (ordenados == DIAS) return "Todos os dias"
        val curtos = ordenados.map { diaCurto(it) }
        return if (curtos.size == 1) curtos.first()
        else curtos.dropLast(1).joinToString(", ") + " e " + curtos.last()
    }

    /** "35 min" / "1h 05min". */
    fun duracao(segundos: Int): String {
        if (segundos <= 0) return "—"
        val minutosTotais = ((segundos + 59) / 60).coerceAtLeast(1)
        val horas = minutosTotais / 60
        val minutos = minutosTotais % 60
        return when {
            horas == 0 -> "$minutosTotais min"
            minutos == 0 -> "${horas}h"
            else -> "${horas}h ${minutos.toString().padStart(2, '0')}min"
        }
    }

    /** A chave do bairro é normalizada (ver TextoUtil.chaveBairro); aqui ela
     * volta a um formato legível. Não existe nome de exibição guardado. */
    fun bairro(chave: String): String =
        chave
            .substringBefore("|")
            .substringBefore("#")
            .substringBefore(";")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { palavra -> palavra.replaceFirstChar { it.uppercase() } }
}
