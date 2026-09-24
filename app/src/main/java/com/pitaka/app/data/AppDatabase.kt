package com.pitaka.app.data

import androidx.room.migration.Migration
import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Pitaka::class, Goal::class, LedgerEntry::class, MonthlyBudget::class, ExpenseFunnel::class, CurrencySettings::class, ExchangeRate::class, RecurringRule::class],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pitakaDao(): PitakaDao
    abstract fun goalDao(): GoalDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun monthlyBudgetDao(): MonthlyBudgetDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun expenseFunnelDao(): ExpenseFunnelDao
    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        internal val MIGRATION_4_5 = object : Migration(4,5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pitakas ADD COLUMN parentPitakaId INTEGER")
                db.execSQL("ALTER TABLE pitakas ADD COLUMN cardStyle TEXT NOT NULL DEFAULT 'solid'")
                db.execSQL("ALTER TABLE goals ADD COLUMN cardStyle TEXT NOT NULL DEFAULT 'solid'")
                db.execSQL("ALTER TABLE expense_funnels ADD COLUMN cardStyle TEXT NOT NULL DEFAULT 'solid'")
                db.execSQL("ALTER TABLE expense_funnels ADD COLUMN isSystem INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pitakas_parentPitakaId ON pitakas(parentPitakaId)")
                db.execSQL("INSERT INTO expense_funnels(name,limit,currency,currencyBalances,validFrom,validUntil,colorHex,cardStyle,isSystem) SELECT 'Unclassified Expense', 0, 'PHP', 'PHP=0', NULL, NULL, NULL, 'solid', 1 WHERE NOT EXISTS (SELECT 1 FROM expense_funnels WHERE name='Unclassified Expense')")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6,7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledger_entries ADD COLUMN secondaryCurrency TEXT")
                db.execSQL("UPDATE ledger_entries SET secondaryCurrency = currency WHERE type = 'TRANSFER' AND secondaryCurrency IS NULL AND secondaryAmount IS NULL")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7,8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recurring_rules ADD COLUMN currency TEXT NOT NULL DEFAULT 'PHP'")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledger_entries ADD COLUMN funnelAmount REAL")
                db.execSQL("ALTER TABLE ledger_entries ADD COLUMN funnelCurrency TEXT")
                db.execSQL("ALTER TABLE ledger_entries ADD COLUMN goalAmount REAL")
                db.execSQL("ALTER TABLE ledger_entries ADD COLUMN goalCurrency TEXT")

                // Preserve the meaning of all historical rows. Existing transactions were
                // recorded as one amount/currency, so those values are also the applied
                // funnel/goal values.
                db.execSQL("""
                    UPDATE ledger_entries
                    SET funnelAmount = amount,
                        funnelCurrency = currency
                    WHERE type = 'EXPENSE' AND funnelId IS NOT NULL
                """.trimIndent())
                db.execSQL("""
                    UPDATE ledger_entries
                    SET goalAmount = amount,
                        goalCurrency = currency
                    WHERE type = 'GOAL_CONTRIBUTION' AND goalId IS NOT NULL
                """.trimIndent())
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledger_entries ADD COLUMN conversionRateToBaseAtTransaction REAL")
                db.execSQL("ALTER TABLE ledger_entries ADD COLUMN amountInBaseAtTransaction REAL")
                db.execSQL("ALTER TABLE ledger_entries ADD COLUMN baseCurrencyAtTransaction TEXT")
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pitakas ADD COLUMN archivedAt INTEGER")
                db.execSQL("ALTER TABLE goals ADD COLUMN archivedAt INTEGER")
                db.execSQL("ALTER TABLE expense_funnels ADD COLUMN archivedAt INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pitaka.db")
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build().also { INSTANCE = it }
        }
    }
}
