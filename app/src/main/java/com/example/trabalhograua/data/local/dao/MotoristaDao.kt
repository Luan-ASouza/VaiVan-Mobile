package com.example.trabalhograua.data.local.dao

import androidx.room.*
import com.example.trabalhograua.data.local.entities.MotoristaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MotoristaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MotoristaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MotoristaEntity>)

    @Delete
    suspend fun delete(item: MotoristaEntity)

    @Query("DELETE FROM motoristas WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM motoristas WHERE id = :id")
    fun getById(id: String): Flow<MotoristaEntity?>

    @Query("SELECT * FROM motoristas")
    fun getAll(): Flow<List<MotoristaEntity>>

    @Query("SELECT * FROM motoristas WHERE statusCadastro = :status")
    fun getByStatusCadastro(status: String): Flow<List<MotoristaEntity>>
}
