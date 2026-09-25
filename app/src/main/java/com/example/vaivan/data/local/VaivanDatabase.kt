package com.example.vaivan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.vaivan.data.local.dao.DocumentoDao
import com.example.vaivan.data.local.dao.PontoEmbarqueDao
import com.example.vaivan.data.local.dao.MotoristaDao
import com.example.vaivan.data.local.dao.ParadaRotaDao
import com.example.vaivan.data.local.dao.PassageiroDao
import com.example.vaivan.data.local.dao.RotaDao
import com.example.vaivan.data.local.dao.SolicitacaoInclusaoDao
import com.example.vaivan.data.local.dao.UsuarioDao
import com.example.vaivan.data.local.dao.VeiculoDao
import com.example.vaivan.data.local.entities.DocumentoEntity
import com.example.vaivan.data.local.entities.PontoDeEmbarqueEntity
import com.example.vaivan.data.local.entities.MotoristaEntity
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.local.entities.VeiculoEntity

@Database(
    entities = [
        UsuarioEntity::class,
        PassageiroEntity::class,
        MotoristaEntity::class,
        DocumentoEntity::class,
        PontoDeEmbarqueEntity::class,
        VeiculoEntity::class,
        RotaEntity::class,
        ParadaRotaEntity::class,
        SolicitacaoInclusaoEntity::class
    ],
    version = 9, // <--- AUMENTEI PARA 8 (RotaEntity agora é N por motorista; SolicitacaoInclusaoEntity ganhou rotaId)
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VaivanDatabase : RoomDatabase() {

    abstract fun UsuarioDao(): UsuarioDao
    abstract fun passageiroDao(): PassageiroDao
    abstract fun motoristaDao(): MotoristaDao
    abstract fun documentoDao(): DocumentoDao
    abstract fun pontoEmbarqueDao(): PontoEmbarqueDao
    abstract fun veiculoDao(): VeiculoDao
    abstract fun rotaDao(): RotaDao
    abstract fun paradaRotaDao(): ParadaRotaDao
    abstract fun solicitacaoInclusaoDao(): SolicitacaoInclusaoDao

    companion object {
        @Volatile
        private var INSTANCE: VaivanDatabase? = null

        fun getInstance(context: Context): VaivanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaivanDatabase::class.java,
                    "vaivan_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}