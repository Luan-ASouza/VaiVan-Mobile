package com.example.trabalhograua.data.local.dao

import androidx.room.*
import com.example.trabalhograua.data.local.entities.DocumentoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DocumentoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DocumentoEntity>)

    @Delete
    suspend fun delete(item: DocumentoEntity)

    @Query("DELETE FROM documentos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM documentos WHERE id = :id")
    fun getById(id: String): Flow<DocumentoEntity?>

    @Query("SELECT * FROM documentos")
    fun getAll(): Flow<List<DocumentoEntity>>
}
