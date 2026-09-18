package com.example.vaivan.data.local.dao

import androidx.room.*
import com.example.vaivan.data.local.entities.UsuarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: UsuarioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<UsuarioEntity>)

    @Delete
    suspend fun delete(item: UsuarioEntity)

    @Query("DELETE FROM usuarios WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM usuarios WHERE id = :id")
    fun getById(id: String): Flow<UsuarioEntity?>

    @Query("SELECT * FROM usuarios")
    fun getAll(): Flow<List<UsuarioEntity>>
}
