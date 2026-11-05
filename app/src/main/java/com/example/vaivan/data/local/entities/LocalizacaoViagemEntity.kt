package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Última posição conhecida do motorista numa viagem — um registro por
 * viagem, sempre sobrescrito (não é histórico). A fonte remota de verdade
 * fica escondida atrás de uma interface
 * ([com.example.vaivan.data.remote.localizacao.LocalizacaoViagemRemoteSource]),
 * hoje implementada com Firestore; trocar de banco no futuro não muda esta
 * entidade nem quem a lê.
 */
@Entity(tableName = "localizacoes_viagem")
data class LocalizacaoViagemEntity(
    @PrimaryKey val viagemId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val atualizadoEm: Long = 0L
)
