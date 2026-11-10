package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Aviso que o responsável registra com antecedência avisando que o filho não
 * vai usar o transporte numa próxima viagem. Consumido (e removido) por
 * IniciarViagemUseCase na hora de montar as paradas da viagem.
 */
@Entity(
    tableName = "avisos_ausencia",
    indices = [
        Index(value = ["rotaId"]),
        Index(value = ["passageiroId"]),
        Index(value = ["responsavelId"]),
        Index(value = ["motoristaId"])
    ]
)
data class AvisoAusenciaEntity(
    @PrimaryKey val id: String = "",
    val rotaId: String = "",
    val passageiroId: String = "",
    val nomePassageiro: String = "",
    val responsavelId: String = "",
    val motoristaId: String = "", // denormalizado, mesmo padrão de SolicitacaoInclusaoEntity

    val tipo: String = "DIA_INTEIRO", // DIA_INTEIRO | SOMENTE_IDA
    val pendenteIda: Boolean = true,
    val pendenteVolta: Boolean = false,

    val criadoEm: Long = 0L,
    val lastUpdated: Long = 0L
)
