package com.example.vaivan.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Rota que o motorista está seguindo DE FATO numa viagem: o trajeto que
 * falta, tirado do Navigation SDK (Navigator.getRouteSegments) e republicado
 * só quando muda (início, recálculo por desvio, parada concluída). É o que
 * o responsável desenha no mapa no lugar da rota fixa do cadastro
 * (RotaEntity.polylineEncoded) — espelhar a rota em vez de recalcular em
 * cada aparelho não gera nenhuma chamada nova à API do Google.
 *
 * Uma linha por viagem, sempre sobrescrita — mesmo molde de
 * [LocalizacaoViagemEntity] (chave de negócio como @PrimaryKey, sem lastUpdated).
 */
@Entity(tableName = "rotas_viagem")
data class RotaViagemEntity(
    @PrimaryKey val viagemId: String = "",
    val polylineEncoded: String = "",
    // Também serve de versão: o responsável só redesenha quando ele cresce.
    val atualizadoEm: Long = 0L
)
