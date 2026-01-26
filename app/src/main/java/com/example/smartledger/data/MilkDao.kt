package com.example.smartledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MilkDao {
    // Normal Queries
    @Query("SELECT * FROM milk_records WHERE isDeleted = 0 ORDER BY year DESC, monthIndex DESC")
    fun getAllByDateDesc(): Flow<List<MilkRecord>>

    @Query("SELECT * FROM milk_records WHERE isDeleted = 0 ORDER BY year ASC, monthIndex ASC")
    fun getAllByDateAsc(): Flow<List<MilkRecord>>

    @Query("SELECT * FROM milk_records WHERE isDeleted = 0 ORDER BY totalAmount DESC")
    fun getAllByAmountDesc(): Flow<List<MilkRecord>>

    @Query("SELECT * FROM milk_records WHERE isDeleted = 0 ORDER BY totalAmount ASC")
    fun getAllByAmountAsc(): Flow<List<MilkRecord>>

    @Query("SELECT * FROM milk_records WHERE isDeleted = 0 ORDER BY totalLiters DESC")
    fun getAllByLitersDesc(): Flow<List<MilkRecord>>

    @Query("SELECT * FROM milk_records WHERE isDeleted = 0 ORDER BY totalLiters ASC")
    fun getAllByLitersAsc(): Flow<List<MilkRecord>>

    // Trash Queries
    @Query("SELECT * FROM milk_records WHERE isDeleted = 1")
    fun getTrashRecords(): Flow<List<MilkRecord>>

    // Actions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MilkRecord)

    @Update
    suspend fun update(record: MilkRecord)

    @Query("UPDATE milk_records SET isDeleted = 1, deletedAt = :timestamp WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Int>, timestamp: Long)

    @Query("UPDATE milk_records SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Int>)

    @Query("DELETE FROM milk_records WHERE id IN (:ids)")
    suspend fun hardDelete(ids: List<Int>)

    @Query("DELETE FROM milk_records WHERE isDeleted = 1 AND deletedAt < :cutoffTimestamp")
    suspend fun deleteExpiredTrash(cutoffTimestamp: Long)

    // Add this for Backup
    @Query("SELECT * FROM milk_records")
    suspend fun getAllRaw(): List<MilkRecord>

    @Query("DELETE FROM milk_records")
    suspend fun clearTable()
}