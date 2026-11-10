package com.example.vaivan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.vaivan.data.local.entities.AvisoAusenciaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AvisoAusenciaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AvisoAusenciaEntity)

    @Query("DELETE FROM avisos_ausencia WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM avisos_ausencia WHERE rotaId = :rotaId AND passageiroId = :passageiroId LIMIT 1")
    fun getAtivo(rotaId: String, passageiroId: String): Flow<AvisoAusenciaEntity?>

    @Query("SELECT * FROM avisos_ausencia WHERE motoristaId = :motoristaId")
    fun getPorMotorista(motoristaId: String): Flow<List<AvisoAusenciaEntity>>

    @Query("SELECT * FROM avisos_ausencia WHERE responsavelId = :responsavelId")
    fun getPorResponsavel(responsavelId: String): Flow<List<AvisoAusenciaEntity>>
}
