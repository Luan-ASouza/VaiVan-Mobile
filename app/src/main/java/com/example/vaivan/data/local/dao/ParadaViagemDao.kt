package com.example.vaivan.data.local.dao

import androidx.room.*
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParadaViagemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ParadaViagemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ParadaViagemEntity>)

    @Query("DELETE FROM paradas_viagem WHERE viagemId = :viagemId")
    suspend fun deleteByViagem(viagemId: String)

    @Query("SELECT * FROM paradas_viagem WHERE viagemId = :viagemId ORDER BY ordem ASC")
    fun getByViagem(viagemId: String): Flow<List<ParadaViagemEntity>>
}
