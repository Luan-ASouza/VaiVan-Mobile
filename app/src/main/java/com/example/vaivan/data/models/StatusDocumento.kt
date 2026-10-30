package com.example.vaivan.data.models

enum class StatusDocumento {
    NAO_ENVIADO,
    PENDENTE,
    EM_ANALISE,
    APROVADO,
    REJEITADO;

    companion object {
        fun fromString(valor: String?): StatusDocumento {
            return when (valor?.uppercase()) {
                "APROVADO", "APROVADA", "ATIVO" -> APROVADO
                "EM_ANALISE", "ANALISE", "ENVIADO" -> EM_ANALISE
                "REJEITADO", "REPROVADO", "REPROVADA" -> REJEITADO
                "PENDENTE" -> PENDENTE
                "NAO_ENVIADO" -> NAO_ENVIADO
                else -> NAO_ENVIADO
            }
        }
    }
}
