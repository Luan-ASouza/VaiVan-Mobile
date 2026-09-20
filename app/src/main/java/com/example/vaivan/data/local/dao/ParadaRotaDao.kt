package com.example.vaivan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParadaRotaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ParadaRotaEntity>)

    @Query("DELETE FROM paradas_rota WHERE rotaId = :rotaId")
    suspend fun deleteByRota(rotaId: String)

    @Query("SELECT * FROM paradas_rota WHERE rotaId = :rotaId ORDER BY ordem ASC")
    fun getByRota(rotaId: String): Flow<List<ParadaRotaEntity>>
}