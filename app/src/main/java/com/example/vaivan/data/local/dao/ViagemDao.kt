package com.example.vaivan.data.local.dao

import androidx.room.*
import com.example.vaivan.data.local.entities.ViagemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ViagemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ViagemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ViagemEntity>)

    @Query("DELETE FROM viagens WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM viagens WHERE id = :id")
    fun getById(id: String): Flow<ViagemEntity?>

    @Query("SELECT * FROM viagens WHERE motoristaId = :motoristaId AND status IN ('EM_ANDAMENTO', 'PAUSADA') LIMIT 1")
    fun getViagemAtivaDoMotorista(motoristaId: String): Flow<ViagemEntity?>
}
