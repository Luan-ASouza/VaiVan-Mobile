package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "paradas_rota",
    indices = [Index(value = ["rotaId"]), Index(value = ["passageiroId"])]
)
data class ParadaRotaEntity(
    @PrimaryKey val id: String = "",
    val rotaId: String = "",

    val passageiroId: String = "",
    val localId: String = "",

    val nomePassageiro: String = "",
    val nomeLocal: String = "",
    val endereco: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    val ordem: Int = 0,
    val distanciaTrechoMetros: Int = 0,
    val duracaoTrechoSegundos: Int = 0,
    val horarioEstimadoMinutos: Int = 0,

    val lastUpdated: Long = 0L
)