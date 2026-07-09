package com.example.trabalhograua.data.local.dao

import androidx.room.*
import com.example.trabalhograua.data.local.entities.LocalizacaoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalizacaoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalizacaoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalizacaoEntity>)

    @Delete
    suspend fun delete(item: LocalizacaoEntity)

    @Query("DELETE FROM localizacoes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM localizacoes WHERE id = :id")
    fun getById(id: String): Flow<LocalizacaoEntity?>

    @Query("SELECT * FROM localizacoes")
    fun getAll(): Flow<List<LocalizacaoEntity>>
}
