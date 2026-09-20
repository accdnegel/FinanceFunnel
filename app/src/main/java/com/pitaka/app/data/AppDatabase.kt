package com.pitaka.app.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Pitaka::class, Goal::class, LedgerEntry::class, MonthlyBudget::class, ExpenseFunnel::class, CurrencySettings::class, ExchangeRate::class, RecurringRule::class],
    version = 5,
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

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pitaka.db")
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
        }
    }
}