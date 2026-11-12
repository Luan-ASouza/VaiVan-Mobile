package com.example.vaivan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.vaivan.data.local.entities.LocalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalEntity>)

    @Query("DELETE FROM locais WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM locais WHERE responsavelId = :responsavelId ORDER BY nome COLLATE NOCASE")
    fun getByResponsavel(responsavelId: String): Flow<List<LocalEntity>>

    /** One-shot pra diffing na sincronização (apagar o que sumiu do Firestore). */
    @Query("SELECT id FROM locais WHERE responsavelId = :responsavelId")
    suspend fun idsDoResponsavel(responsavelId: String): List<String>
}
