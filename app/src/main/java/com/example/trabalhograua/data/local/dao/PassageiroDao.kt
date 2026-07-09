package com.example.trabalhograua.data.local.dao

import androidx.room.*
import com.example.trabalhograua.data.local.entities.PassageiroEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PassageiroDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PassageiroEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PassageiroEntity>)

    @Delete
    suspend fun delete(item: PassageiroEntity)

    @Query("DELETE FROM passageiros WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM passageiros WHERE id = :id")
    fun getById(id: String): Flow<PassageiroEntity?>

    @Query("SELECT * FROM passageiros")
    fun getAll(): Flow<List<PassageiroEntity>>

    @Query("SELECT * FROM passageiros WHERE responsavelId = :responsavelId")
    fun getByResponsavel(responsavelId: String): Flow<List<PassageiroEntity>>

    @Query("SELECT * FROM passageiros WHERE rotaId = :rotaId")
    fun getByRota(rotaId: String): Flow<List<PassageiroEntity>>
}
