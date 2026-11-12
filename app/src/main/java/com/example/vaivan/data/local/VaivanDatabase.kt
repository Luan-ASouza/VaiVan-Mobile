package com.example.vaivan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.vaivan.data.local.dao.AvisoAusenciaDao
import com.example.vaivan.data.local.dao.DocumentoDao
import com.example.vaivan.data.local.dao.EventoViagemDao
import com.example.vaivan.data.local.dao.LocalizacaoViagemDao
import com.example.vaivan.data.local.dao.MotoristaDao
import com.example.vaivan.data.local.dao.ParadaRotaDao
import com.example.vaivan.data.local.dao.ParadaViagemDao
import com.example.vaivan.data.local.dao.PassageiroDao
import com.example.vaivan.data.local.dao.PontoEmbarqueDao
import com.example.vaivan.data.local.dao.RotaDao
import com.example.vaivan.data.local.dao.SolicitacaoInclusaoDao
import com.example.vaivan.data.local.dao.UsuarioDao
import com.example.vaivan.data.local.dao.VeiculoDao
import com.example.vaivan.data.local.dao.ViagemDao
import com.example.vaivan.data.local.entities.AvisoAusenciaEntity
import com.example.vaivan.data.local.entities.DocumentoEntity
import com.example.vaivan.data.local.entities.EventoViagemEntity
import com.example.vaivan.data.local.entities.LocalizacaoViagemEntity
import com.example.vaivan.data.local.entities.MotoristaEntity
import com.example.vaivan.data.local.entities.ParadaRotaEntity
import com.example.vaivan.data.local.entities.ParadaViagemEntity
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.local.entities.PontoEmbarqueEntity
import com.example.vaivan.data.local.entities.RotaEntity
import com.example.vaivan.data.local.entities.SolicitacaoInclusaoEntity
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.local.entities.VeiculoEntity
import com.example.vaivan.data.local.entities.ViagemEntity

@Database(
    entities = [
        UsuarioEntity::class,
        PassageiroEntity::class,
        MotoristaEntity::class,
        DocumentoEntity::class,
        PontoEmbarqueEntity::class,
        VeiculoEntity::class,
        RotaEntity::class,
        ParadaRotaEntity::class,
        SolicitacaoInclusaoEntity::class,
        ViagemEntity::class,
        ParadaViagemEntity::class,
        EventoViagemEntity::class,
        LocalizacaoViagemEntity::class,
        AvisoAusenciaEntity::class
    ],
    version = 18,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VaivanDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun passageiroDao(): PassageiroDao
    abstract fun motoristaDao(): MotoristaDao
    abstract fun documentoDao(): DocumentoDao
    abstract fun pontoEmbarqueDao(): PontoEmbarqueDao
    abstract fun veiculoDao(): VeiculoDao
    abstract fun rotaDao(): RotaDao
    abstract fun paradaRotaDao(): ParadaRotaDao
    abstract fun solicitacaoInclusaoDao(): SolicitacaoInclusaoDao
    abstract fun viagemDao(): ViagemDao
    abstract fun paradaViagemDao(): ParadaViagemDao
    abstract fun eventoViagemDao(): EventoViagemDao
    abstract fun localizacaoViagemDao(): LocalizacaoViagemDao
    abstract fun avisoAusenciaDao(): AvisoAusenciaDao

    companion object {
        @Volatile
        private var INSTANCE: VaivanDatabase? = null

        private val VERSOES_DE_DESENVOLVIMENTO = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)

        @Suppress("DEPRECATION")

        fun getInstance(context: Context): VaivanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaivanDatabase::class.java,
                    "vaivan_cache.db"
                )
                    // Migrações reais (ver Migracoes.kt)
                    .addMigrations(*Migracoes.TODAS)
                    // Apenas as versões antigas de desenvolvimento (1 a 13), que não têm
                    // histórico de esquema, podem ser recriadas. Da 14 em diante, só migração.
                    .fallbackToDestructiveMigrationFrom(*VERSOES_DE_DESENVOLVIMENTO)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}