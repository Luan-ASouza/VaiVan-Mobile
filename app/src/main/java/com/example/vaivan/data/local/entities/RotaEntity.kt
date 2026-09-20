package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rotas",
    indices = [Index(value = ["motoristaId"]), Index(value = ["destinoNome"]), Index(value = ["turno"])]
)
data class RotaEntity(
    @PrimaryKey val id: String = "",
    val motoristaId: String = "",
    val veiculoId: String? = null,
    val nome: String = "", // ex: "La Salle Carmo - Manhã"

    val origemNome: String = "",
    val origemLatitude: Double = 0.0,
    val origemLongitude: Double = 0.0,

    val destinoNome: String = "",
    val destinoEndereco: String = "",
    val destinoLatitude: Double = 0.0,
    val destinoLongitude: Double = 0.0,

    val turno: String = "", // MANHA | TARDE | NOITE
    val diasSemana: String = "", // ex: "SEG,TER,QUA,QUI,SEX"

    val capacidadeTotal: Int = 0,
    val vagasOcupadas: Int = 0,

    val distanciaMetros: Int = 0,
    val duracaoSegundos: Int = 0,
    val duracaoSemTrafegoSegundos: Int = 0,
    val polylineEncoded: String = "",

    val status: String = "ATIVA", // ATIVA | ARQUIVADA
    val calculadoEm: Long = 0L,
    val lastUpdated: Long = 0L
)