package com.pitaka.app.data

import androidx.room.migration.Migration
import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Pitaka::class, Goal::class, LedgerEntry::class, MonthlyBudget::class, ExpenseFunnel::class, CurrencySettings::class, ExchangeRate::class, RecurringRule::class],
    version = 6,
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

        private val MIGRATION_4_5 = object : Migration(4,5) {
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
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

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pitaka.db")
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .build().also { INSTANCE = it }
        }
    }
}
