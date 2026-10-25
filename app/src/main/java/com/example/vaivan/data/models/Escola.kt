package com.example.vaivan.data.models

data class Escola(
    val id: String = "",
    val nome: String = "",
    val nomeNormalizado: String = "",
    val endereco: String = "",
    val cidade: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val criadoPor: String = "",
    val criadoEm: Long = 0L,

    // ---- NOVOS (importação do INEP) ----
    val codigoInep: String = "",
    val nomeOriginal: String = "",
    val cep: String = "",
    val telefone: String = "",
    val dependencia: String = "",       // MUNICIPAL | ESTADUAL | FEDERAL | PRIVADA
    val fonteCoordenadas: String = ""   // INEP | GEOCODER | NENHUMA
)