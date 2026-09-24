package com.pitaka.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppDatabaseMigrationContractTest {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("pitaka-migration-contract-test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE pitakas (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, currentAmount REAL NOT NULL, currency TEXT NOT NULL, currencyBalances TEXT NOT NULL, colorHex TEXT)")
                    db.execSQL("CREATE TABLE goals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, targetAmount REAL NOT NULL, currency TEXT NOT NULL, currencyBalances TEXT NOT NULL, targetDate INTEGER NOT NULL, colorHex TEXT, createdAt INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE expense_funnels (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, limit REAL NOT NULL, currency TEXT NOT NULL, currencyBalances TEXT NOT NULL, validFrom INTEGER, validUntil INTEGER, colorHex TEXT)")
                    db.execSQL("CREATE TABLE ledger_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, name TEXT NOT NULL, category TEXT, pitakaId INTEGER, fromPitakaId INTEGER, toPitakaId INTEGER, secondaryAmount REAL, goalId INTEGER, funnelId INTEGER, date INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE recurring_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL, amount REAL NOT NULL, category TEXT, pitakaId INTEGER NOT NULL, dayOfMonth INTEGER NOT NULL, active INTEGER NOT NULL, lastAppliedMonth TEXT, createdAt INTEGER NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase("pitaka-migration-contract-test.db")
    }

    @Test
    fun migrations_4_to_8_preserve_and_extend_contract() {
        db.execSQL("INSERT INTO pitakas(name,currentAmount,currency,currencyBalances,colorHex) VALUES('Wallet',100.0,'PHP','PHP=100',NULL)")
        db.execSQL("INSERT INTO expense_funnels(name,limit,currency,currencyBalances,validFrom,validUntil,colorHex) VALUES('Food',1000.0,'PHP','PHP=0',NULL,NULL,NULL)")
        db.execSQL("INSERT INTO ledger_entries(type,amount,currency,name,pitakaId,secondaryAmount,date) VALUES('TRANSFER',10.0,'PHP','Transfer',1,NULL,1000)")
        db.execSQL("INSERT INTO recurring_rules(type,name,amount,category,pitakaId,dayOfMonth,active,lastAppliedMonth,createdAt) VALUES('INCOME','Salary',100.0,NULL,1,31,1,NULL,1000)")

        AppDatabase.MIGRATION_4_5.migrate(db)
        assertTrue(columnExists("pitakas", "parentPitakaId"))
        assertTrue(columnExists("pitakas", "cardStyle"))
        assertTrue(columnExists("goals", "cardStyle"))
        assertTrue(columnExists("expense_funnels", "cardStyle"))
        assertTrue(columnExists("expense_funnels", "isSystem"))
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM expense_funnels WHERE name='Unclassified Expense'"))

        AppDatabase.MIGRATION_5_6.migrate(db)
        assertTrue(columnExists("ledger_entries", "funnelAmount"))
        assertTrue(columnExists("ledger_entries", "funnelCurrency"))
        assertTrue(columnExists("ledger_entries", "goalAmount"))
        assertTrue(columnExists("ledger_entries", "goalCurrency"))

        AppDatabase.MIGRATION_6_7.migrate(db)
        assertTrue(columnExists("ledger_entries", "secondaryCurrency"))
        assertEquals("PHP", scalarString("SELECT secondaryCurrency FROM ledger_entries WHERE type='TRANSFER'"))

        AppDatabase.MIGRATION_7_8.migrate(db)
        assertTrue(columnExists("recurring_rules", "currency"))
        assertEquals("PHP", scalarString("SELECT currency FROM recurring_rules WHERE name='Salary'"))
    }

    private fun columnExists(table: String, column: String): Boolean {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) if (cursor.getString(nameIndex) == column) return true
        }
        return false
    }

    private fun scalarInt(sql: String): Int =
        db.query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun scalarString(sql: String): String? =
        db.query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}
