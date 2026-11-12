package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local salvo pelo usuário ("Casa", "Trabalho"...), usado como ponto de
 * embarque na busca de rotas. O dono fica em `responsavelId` (nome histórico
 * do campo: vale também pro motorista) — é o que a regra do Firestore checa. */
@Entity(tableName = "locais", indices = [Index(value = ["responsavelId"])])
data class LocalEntity(
    @PrimaryKey val id: String = "",

    val nome: String = "",
    val endereco: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeId: String? = null,

    val responsavelId: String = "",

    val lastUpdated: Long = 0L
)
