package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Execução concreta (uma viagem) de uma [RotaEntity]. A rota é o modelo/template
 * cadastrado; a viagem é uma instância dela em um dia/turno específico, com
 * status, horários reais e progresso. Nunca escrita a partir da posição GPS
 * bruta — só eventos de negócio (início, chegada, embarque, etc.).
 */
@Entity(
    tableName = "viagens",
    indices = [Index(value = ["motoristaId"]), Index(value = ["rotaId"])]
)
data class ViagemEntity(
    @PrimaryKey val id: String = "",
    val rotaId: String = "",
    val motoristaId: String = "",
    val veiculoId: String? = null,

    val direcao: String = "IDA", // IDA | VOLTA

    // Snapshot dos responsáveis com algum passageiro nesta viagem — permite que
    // as regras do Firestore liberem leitura sem consulta cruzada a outra coleção.
    val responsaveisAutorizados: List<String> = emptyList(),

    val status: String = "EM_ANDAMENTO", // EM_ANDAMENTO | PAUSADA | FINALIZADA | CANCELADA
    val paradaAtualId: String? = null,

    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null,
    val ultimaPosicaoEm: Long? = null,

    val iniciadaEm: Long = 0L,
    val pausadaEm: Long? = null,
    val finalizadaEm: Long? = null,

    val lastUpdated: Long = 0L
)
