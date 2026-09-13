package com.pitaka.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Pitaka::class, Goal::class, LedgerEntry::class, MonthlyBudget::class,
        CurrencySettings::class, ExchangeRate::class, RecurringRule::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pitakaDao(): PitakaDao
    abstract fun goalDao(): GoalDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun monthlyBudgetDao(): MonthlyBudgetDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pitaka.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
