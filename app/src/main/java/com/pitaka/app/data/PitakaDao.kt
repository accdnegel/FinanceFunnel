package com.pitaka.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PitakaDao {
    @Insert suspend fun insertPitaka(pitaka: Pitaka): Long
    @Update suspend fun updatePitaka(pitaka: Pitaka)
    @Delete suspend fun deletePitaka(pitaka: Pitaka)
    @Query("SELECT * FROM pitakas WHERE id = :id") suspend fun getPitaka(id: Long): Pitaka?
    @Query("SELECT * FROM pitakas WHERE archivedAt IS NULL ORDER BY createdAt DESC") fun observePitakas(): Flow<List<Pitaka>>
    @Query("SELECT * FROM pitakas WHERE parentPitakaId = :parentId AND archivedAt IS NULL ORDER BY createdAt DESC")
    fun observeChildren(parentId: Long): Flow<List<Pitaka>>
    @Query("SELECT * FROM pitakas WHERE parentPitakaId IS NULL AND archivedAt IS NULL ORDER BY createdAt DESC")
    fun observeRootPitakas(): Flow<List<Pitaka>>
    @Query("SELECT COUNT(*) FROM pitakas WHERE parentPitakaId = :parentId")
    suspend fun countChildren(parentId: Long): Int
    @Query("UPDATE pitakas SET parentPitakaId = NULL WHERE parentPitakaId = :parentId")
    suspend fun detachChildren(parentId: Long)
}