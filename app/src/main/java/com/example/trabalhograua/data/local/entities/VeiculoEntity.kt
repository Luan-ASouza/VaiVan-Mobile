package com.example.trabalhograua.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "veiculos", indices = [Index(value = ["motoristaId"])])
data class VeiculoEntity(
    @PrimaryKey val id: String = "",
    val placa: String = "",
    val marca: String = "",
    val modelo: String = "",
    val cor: String = "",
    val anoFabricacao: Int = 0,
    val capacidadePassageiros: Int = 0,
    val status: String = "",
    val motoristaId: String = "",
    val lastUpdated: Long = 0L
)