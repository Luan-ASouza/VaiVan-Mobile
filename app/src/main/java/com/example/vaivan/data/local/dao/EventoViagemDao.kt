package com.example.vaivan.data.local.dao

import androidx.room.*
import com.example.vaivan.data.local.entities.EventoViagemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventoViagemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EventoViagemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EventoViagemEntity>)

    @Query("SELECT * FROM eventos_viagem WHERE viagemId = :viagemId ORDER BY ocorridoEm ASC")
    fun getByViagem(viagemId: String): Flow<List<EventoViagemEntity>>
}
