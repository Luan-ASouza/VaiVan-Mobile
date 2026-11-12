package com.example.vaivan.data.local.dao

import androidx.room.*
import com.example.vaivan.data.local.entities.RotaViagemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RotaViagemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RotaViagemEntity)

    @Query("SELECT * FROM rotas_viagem WHERE viagemId = :viagemId")
    fun observarPorViagem(viagemId: String): Flow<RotaViagemEntity?>
}
