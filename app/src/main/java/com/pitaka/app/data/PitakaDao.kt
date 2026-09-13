package com.pitaka.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PitakaDao {

    @Insert
    suspend fun insertPitaka(pitaka: Pitaka): Long

    @Update
    suspend fun updatePitaka(pitaka: Pitaka)

    @Delete
    suspend fun deletePitaka(pitaka: Pitaka)

    @Query("SELECT * FROM pitakas WHERE id = :id")
    suspend fun getPitaka(id: Long): Pitaka?

    @Query("SELECT * FROM pitakas ORDER BY createdAt DESC")
    fun observePitakas(): Flow<List<Pitaka>>
}
