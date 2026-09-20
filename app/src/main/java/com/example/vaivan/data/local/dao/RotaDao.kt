package com.example.vaivan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.vaivan.data.local.entities.RotaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RotaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RotaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RotaEntity>)

    @Query("SELECT * FROM rotas WHERE id = :id")
    fun getById(id: String): Flow<RotaEntity?>

    @Query("SELECT * FROM rotas WHERE motoristaId = :motoristaId ORDER BY nome ASC")
    fun getByMotorista(motoristaId: String): Flow<List<RotaEntity>>
}