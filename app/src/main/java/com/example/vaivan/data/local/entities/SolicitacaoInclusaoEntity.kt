package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "solicitacoes_inclusao",
    indices = [Index(value = ["motoristaId"]), Index(value = ["responsavelId"]), Index(value = ["passageiroId"]), Index(value = ["rotaId"])]
)
data class SolicitacaoInclusaoEntity(
    @PrimaryKey val id: String = "",

    val rotaId: String = "",
    val passageiroId: String = "",
    val nomePassageiro: String = "",

    val responsavelId: String = "",
    val motoristaId: String = "",

    val localEmbarqueId: String = "",
    val nomeLocalEmbarque: String = "",
    val enderecoEmbarque: String = "",
    val latitudeEmbarque: Double = 0.0,
    val longitudeEmbarque: Double = 0.0,

    val horarioDesejado: String = "",

    // NOVO: pedido ou observação do responsável para o motorista (opcional, até 300 caracteres)
    val mensagem: String = "",

    val status: String = "PENDENTE", // PENDENTE | ACEITA | RECUSADA

    val criadoEm: Long = 0L,
    val respondidoEm: Long? = null,
    val lastUpdated: Long = 0L
)