package com.example.vaivan.data.local.dao

import androidx.room.*
import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalizacaoViagemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalizacaoViagemEntity)

    @Query("SELECT * FROM localizacoes_viagem WHERE viagemId = :viagemId")
    fun observarPorViagem(viagemId: String): Flow<LocalizacaoViagemEntity?>
}
