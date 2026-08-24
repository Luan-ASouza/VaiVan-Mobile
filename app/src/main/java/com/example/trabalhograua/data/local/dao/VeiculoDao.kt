package com.example.trabalhograua.data.local.dao

import androidx.room.*
import com.example.trabalhograua.data.local.entities.VeiculoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VeiculoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: VeiculoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<VeiculoEntity>)

    @Delete
    suspend fun delete(item: VeiculoEntity)

    @Query("DELETE FROM veiculos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM veiculos WHERE id = :id")
    fun getById(id: String): Flow<VeiculoEntity?>

    @Query("SELECT * FROM veiculos")
    fun getAll(): Flow<List<VeiculoEntity>>

    @Query("SELECT * FROM veiculos WHERE motoristaId = :motoristaId")
    fun getByMotoristaId(motoristaId: String): Flow<List<VeiculoEntity>>
}
