package com.example.trabalhograua.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters // <--- IMPORTANTE
import com.example.trabalhograua.data.TimestampConverter
import com.example.trabalhograua.data.local.dao.DocumentoDao
import com.example.trabalhograua.data.local.dao.LocalizacaoDao
import com.example.trabalhograua.data.local.dao.MotoristaDao
import com.example.trabalhograua.data.local.dao.PassageiroDao
import com.example.trabalhograua.data.local.dao.ResponsavelDao
import com.example.trabalhograua.data.local.entities.DocumentoEntity
import com.example.trabalhograua.data.local.entities.LocalizacaoEntity
import com.example.trabalhograua.data.local.entities.MotoristaEntity
import com.example.trabalhograua.data.local.entities.PassageiroEntity
import com.example.trabalhograua.data.local.entities.ResponsavelEntity

@Database(
    entities = [
        ResponsavelEntity::class,
        PassageiroEntity::class,
        MotoristaEntity::class,
        DocumentoEntity::class,
        LocalizacaoEntity::class
    ],
    version = 2, // <--- AUMENTEI PARA 2 para forçar a limpeza do cache antigo
    exportSchema = false
)
@TypeConverters(TimestampConverter::class) // <--- ADICIONE ESTA LINHA AQUI
abstract class VaivanDatabase : RoomDatabase() {

    abstract fun responsavelDao(): ResponsavelDao
    abstract fun passageiroDao(): PassageiroDao
    abstract fun motoristaDao(): MotoristaDao
    abstract fun documentoDao(): DocumentoDao
    abstract fun localizacaoDao(): LocalizacaoDao

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