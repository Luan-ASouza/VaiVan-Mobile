package com.example.vaivan.data.local.dao

import androidx.room.*
import com.example.vaivan.data.local.entities.PontoEmbarqueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PontoEmbarqueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PontoEmbarqueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PontoEmbarqueEntity>)

    @Delete
    suspend fun delete(item: PontoEmbarqueEntity)

    @Query("DELETE FROM ponto_embarque WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM ponto_embarque WHERE usuarioId = :id")
    fun getByResponsavel(id: String): Flow<List<PontoEmbarqueEntity>>

    @Query("SELECT * FROM ponto_embarque WHERE id = :id")
    fun getById(id: String): Flow<PontoEmbarqueEntity?>

    @Query("SELECT * FROM ponto_embarque")
    fun getAll(): Flow<List<PontoEmbarqueEntity>>
}
