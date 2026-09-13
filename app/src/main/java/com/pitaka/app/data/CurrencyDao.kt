package com.pitaka.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {

    @Query("SELECT * FROM currency_settings WHERE id = 0")
    fun observeSettings(): Flow<CurrencySettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: CurrencySettings)

    @Query("SELECT * FROM exchange_rates ORDER BY code ASC")
    fun observeRates(): Flow<List<ExchangeRate>>

    @Query("SELECT * FROM exchange_rates ORDER BY code ASC")
    suspend fun getRatesOnce(): List<ExchangeRate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(rate: ExchangeRate)

    @Query("DELETE FROM exchange_rates WHERE code = :code")
    suspend fun deleteRate(code: String)
}
