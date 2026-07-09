package com.example.trabalhograua.data.local.dao

import androidx.room.*
import com.example.trabalhograua.data.local.entities.ResponsavelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResponsavelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ResponsavelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ResponsavelEntity>)

    @Delete
    suspend fun delete(item: ResponsavelEntity)

    @Query("DELETE FROM responsaveis WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM responsaveis WHERE id = :id")
    fun getById(id: String): Flow<ResponsavelEntity?>

    @Query("SELECT * FROM responsaveis")
    fun getAll(): Flow<List<ResponsavelEntity>>
}
