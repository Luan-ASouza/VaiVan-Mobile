package com.example.vaivan.data.local.dao

import androidx.room.*
import com.example.vaivan.data.local.entities.PontoDeEmbarqueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PontoEmbarqueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PontoDeEmbarqueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PontoDeEmbarqueEntity>)

    @Delete
    suspend fun delete(item: PontoDeEmbarqueEntity)

    @Query("DELETE FROM ponto_embarque WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM ponto_embarque WHERE usuarioId = :id")
    fun getByResponsavel(id: String): Flow<List<PontoDeEmbarqueEntity>>

    @Query("SELECT * FROM ponto_embarque WHERE id = :id")
    fun getById(id: String): Flow<PontoDeEmbarqueEntity?>

    @Query("SELECT * FROM ponto_embarque")
    fun getAll(): Flow<List<PontoDeEmbarqueEntity>>
}
