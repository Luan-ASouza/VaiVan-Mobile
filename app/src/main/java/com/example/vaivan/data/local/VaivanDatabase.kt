package com.example.vaivan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters // <--- IMPORTANTE
import com.example.vaivan.data.TimestampConverter
import com.example.vaivan.data.local.dao.DocumentoDao
import com.example.vaivan.data.local.dao.LocalizacaoDao
import com.example.vaivan.data.local.dao.MotoristaDao
import com.example.vaivan.data.local.dao.PassageiroDao
import com.example.vaivan.data.local.dao.UsuarioDao
import com.example.vaivan.data.local.dao.VeiculoDao
import com.example.vaivan.data.local.entities.DocumentoEntity
import com.example.vaivan.data.local.entities.LocalizacaoEntity
import com.example.vaivan.data.local.entities.MotoristaEntity
import com.example.vaivan.data.local.entities.PassageiroEntity
import com.example.vaivan.data.local.entities.UsuarioEntity
import com.example.vaivan.data.local.entities.VeiculoEntity

@Database(
    entities = [
        UsuarioEntity::class,
        PassageiroEntity::class,
        MotoristaEntity::class,
        DocumentoEntity::class,
        LocalizacaoEntity::class,
        VeiculoEntity::class
    ],
    version = 5, // <--- AUMENTEI PARA 5 (novos campos em PassageiroEntity: necessidades especiais e observações)
    exportSchema = false

)

@TypeConverters(TimestampConverter::class) // <--- ADICIONE ESTA LINHA AQUI
abstract class VaivanDatabase : RoomDatabase() {

    abstract fun UsuarioDao(): UsuarioDao
    abstract fun passageiroDao(): PassageiroDao
    abstract fun motoristaDao(): MotoristaDao
    abstract fun documentoDao(): DocumentoDao
    abstract fun localizacaoDao(): LocalizacaoDao
    abstract fun veiculoDao(): VeiculoDao

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
                    // Como mudamos para a versão 2, isso vai recriar o DB local sem travar o app
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}