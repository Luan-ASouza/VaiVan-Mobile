package com.example.vaivan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SolicitacaoInclusaoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SolicitacaoInclusaoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SolicitacaoInclusaoEntity>)

    @Query("SELECT * FROM solicitacoes_inclusao WHERE motoristaId = :motoristaId AND status = 'PENDENTE' ORDER BY criadoEm ASC")
    fun getPendentesPorMotorista(motoristaId: String): Flow<List<SolicitacaoInclusaoEntity>>

    @Query("SELECT * FROM solicitacoes_inclusao WHERE rotaId = :rotaId AND status = 'PENDENTE' ORDER BY criadoEm ASC")
    fun getPendentesPorRota(rotaId: String): Flow<List<SolicitacaoInclusaoEntity>>

    @Query("SELECT * FROM solicitacoes_inclusao WHERE responsavelId = :responsavelId ORDER BY criadoEm DESC")
    fun getPorResponsavel(responsavelId: String): Flow<List<SolicitacaoInclusaoEntity>>
}