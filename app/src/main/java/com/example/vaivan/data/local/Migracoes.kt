package com.example.vaivan.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrações reais do banco local (Room).
 *
 * O banco é só um CACHE do Firestore, mas apagá-lo a cada mudança de estrutura obriga todos
 * os usuários a baixar tudo de novo (e perde dados ainda não sincronizados). Por isso, a
 * partir da versão 14, TODA mudança de estrutura (coluna nova, tabela nova...) exige:
 *
 *  1. subir `version` em VaivanDatabase;
 *  2. criar aqui uma Migration(versaoAntiga, versaoNova) com o SQL da mudança;
 *  3. incluir a migração na lista TODAS;
 *  4. versionar no Git o novo arquivo gerado em app/schemas.
 *
 * Se esquecer o passo 2 ou 3, o app falha ao abrir o banco (de propósito: melhor falhar
 * nos testes do que apagar o cache dos usuários em silêncio).
 *
 * Exemplo (NÃO ativo), adicionar a coluna "apelido" em uma tabela:
 *
 *   val DE_14_PARA_15 = object : Migration(14, 15) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE nome_da_tabela ADD COLUMN apelido TEXT")
 *       }
 *   }
 *   // e então: val TODAS: Array<Migration> = arrayOf(DE_14_PARA_15)
 *   // (importe androidx.sqlite.db.SupportSQLiteDatabase)
 */
object Migracoes {

    /** Tabela nova: avisos de ausência que o responsável registra com antecedência. */
    val DE_14_PARA_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `avisos_ausencia` (" +
                    "`id` TEXT NOT NULL, `rotaId` TEXT NOT NULL, `passageiroId` TEXT NOT NULL, " +
                    "`nomePassageiro` TEXT NOT NULL, `responsavelId` TEXT NOT NULL, `motoristaId` TEXT NOT NULL, " +
                    "`tipo` TEXT NOT NULL, `pendenteIda` INTEGER NOT NULL, `pendenteVolta` INTEGER NOT NULL, " +
                    "`criadoEm` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_avisos_ausencia_rotaId` ON `avisos_ausencia` (`rotaId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_avisos_ausencia_passageiroId` ON `avisos_ausencia` (`passageiroId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_avisos_ausencia_responsavelId` ON `avisos_ausencia` (`responsavelId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_avisos_ausencia_motoristaId` ON `avisos_ausencia` (`motoristaId`)")
        }
    }

    /** Rotas passam a ter o valor da mensalidade e o dia de vencimento. */
    val DE_15_PARA_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `rotas` ADD COLUMN `valorMensalCentavos` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `rotas` ADD COLUMN `diaVencimento` INTEGER NOT NULL DEFAULT 10")
        }
    }

    val TODAS: Array<Migration> = arrayOf(DE_14_PARA_15, DE_15_PARA_16)
}