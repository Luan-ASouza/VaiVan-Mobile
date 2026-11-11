
package com.example.vaivan.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrações do banco local Room.
 *
 * O Firestore é a fonte da verdade e o Room funciona
 * como cache local.
 *
 * Para cada mudança de estrutura:
 * 1. Incrementar a versão em VaivanDatabase.
 * 2. Criar uma migração correspondente.
 * 3. Adicionar a migração em TODAS.
 * 4. Versionar o schema gerado em app/schemas.
 *
 * Evitar fallbackToDestructiveMigration para
 * preservar dados locais.
 */
object Migracoes {

    /**
     * 14 → 15
     *
     * Criação da tabela de avisos de ausência.
     */
    val DE_14_PARA_15 = object : Migration(14, 15) {

        override fun migrate(db: SupportSQLiteDatabase) {

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `avisos_ausencia` (
                    `id` TEXT NOT NULL,
                    `rotaId` TEXT NOT NULL,
                    `passageiroId` TEXT NOT NULL,
                    `nomePassageiro` TEXT NOT NULL,
                    `responsavelId` TEXT NOT NULL,
                    `motoristaId` TEXT NOT NULL,
                    `tipo` TEXT NOT NULL,
                    `pendenteIda` INTEGER NOT NULL,
                    `pendenteVolta` INTEGER NOT NULL,
                    `criadoEm` INTEGER NOT NULL,
                    `lastUpdated` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_avisos_ausencia_rotaId` " +
                        "ON `avisos_ausencia` (`rotaId`)"
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_avisos_ausencia_passageiroId` " +
                        "ON `avisos_ausencia` (`passageiroId`)"
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_avisos_ausencia_responsavelId` " +
                        "ON `avisos_ausencia` (`responsavelId`)"
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_avisos_ausencia_motoristaId` " +
                        "ON `avisos_ausencia` (`motoristaId`)"
            )
        }
    }

    /**
     * 15 → 16
     *
     * Alterações em usuarios:
     * - Sobrenome
     * - Aceite dos termos
     * - Comunicações
     *
     * Alterações em rotas:
     * - Valor mensal
     * - Dia do vencimento
     */
    val DE_15_PARA_16 = object : Migration(15, 16) {

        override fun migrate(db: SupportSQLiteDatabase) {

            // USUÁRIOS
            db.execSQL(
                "ALTER TABLE `usuarios` " +
                        "ADD COLUMN `sobrenome` TEXT NOT NULL DEFAULT ''"
            )

            db.execSQL(
                "ALTER TABLE `usuarios` " +
                        "ADD COLUMN `aceitouTermos` INTEGER NOT NULL DEFAULT 0"
            )

            db.execSQL(
                "ALTER TABLE `usuarios` " +
                        "ADD COLUMN `aceitaComunicacoes` INTEGER NOT NULL DEFAULT 0"
            )

            // ROTAS
            db.execSQL(
                "ALTER TABLE `rotas` " +
                        "ADD COLUMN `valorMensalCentavos` INTEGER NOT NULL DEFAULT 0"
            )

            db.execSQL(
                "ALTER TABLE `rotas` " +
                        "ADD COLUMN `diaVencimento` INTEGER NOT NULL DEFAULT 10"
            )
        }
    }

    /**
     * 16 → 17
     *
     * Ordem da volta guardada na rota (rotas IDA_E_VOLTA): calculada uma vez
     * no aceite do passageiro, em vez de a cada volta iniciada.
     */
    val DE_16_PARA_17 = object : Migration(16, 17) {

        override fun migrate(db: SupportSQLiteDatabase) {

            db.execSQL(
                "ALTER TABLE `paradas_rota` " +
                        "ADD COLUMN `ordemVolta` INTEGER NOT NULL DEFAULT -1"
            )

            db.execSQL(
                "ALTER TABLE `paradas_rota` " +
                        "ADD COLUMN `horarioEstimadoVoltaMinutos` INTEGER NOT NULL DEFAULT 0"
            )

            db.execSQL(
                "ALTER TABLE `rotas` " +
                        "ADD COLUMN `voltaCalculadaEm` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * Todas as migrações registradas.
     */
    val TODAS: Array<Migration> = arrayOf(
        DE_14_PARA_15,
        DE_15_PARA_16,
        DE_16_PARA_17
    )
}
