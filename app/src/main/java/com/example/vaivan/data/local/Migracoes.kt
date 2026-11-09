package com.example.vaivan.data.local

import androidx.room.migration.Migration

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

    /** Ainda vazia: a versão 14 é o ponto de partida. */
    val TODAS: Array<Migration> = arrayOf()
}