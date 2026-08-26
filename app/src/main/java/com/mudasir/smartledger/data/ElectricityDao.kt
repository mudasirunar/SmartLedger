package com.mudasir.smartledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ElectricityDao {
    // Normal Queries (Not Deleted) - Sorting by End Date
    @Query("SELECT * FROM electricity_records WHERE isDeleted = 0 ORDER BY endDate DESC")
    fun getAllByDateDesc(): Flow<List<Electricity>>

    @Query("SELECT * FROM electricity_records WHERE isDeleted = 0 ORDER BY endDate DESC")
    suspend fun getActiveRaw(): List<Electricity>

    @Query("SELECT * FROM electricity_records WHERE isDeleted = 0 ORDER BY endDate ASC")
    fun getAllByDateAsc(): Flow<List<Electricity>>

    @Query("SELECT * FROM electricity_records WHERE isDeleted = 0 ORDER BY amount DESC")
    fun getAllByAmountDesc(): Flow<List<Electricity>>

    @Query("SELECT * FROM electricity_records WHERE isDeleted = 0 ORDER BY amount ASC")
    fun getAllByAmountAsc(): Flow<List<Electricity>>

    @Query("SELECT * FROM electricity_records WHERE isDeleted = 0 ORDER BY totalUnits DESC")
    fun getAllByUnitsDesc(): Flow<List<Electricity>>

    @Query("SELECT * FROM electricity_records WHERE isDeleted = 0 ORDER BY totalUnits ASC")
    fun getAllByUnitsAsc(): Flow<List<Electricity>>

    @Query("SELECT * FROM electricity_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Electricity?

    @Query("SELECT * FROM electricity_records WHERE isDeleted = 0 ORDER BY endDate DESC LIMIT 1")
    suspend fun getLastActiveRecord(): Electricity?

    // Trash Queries
    @Query("SELECT * FROM electricity_records WHERE isDeleted = 1")
    fun getTrashRecords(): Flow<List<Electricity>>

    // Actions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: Electricity)

    @Update
    suspend fun update(record: Electricity)

    @Query("UPDATE electricity_records SET isDeleted = 1, deletedAt = :timestamp WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Int>, timestamp: Long)

    @Query("UPDATE electricity_records SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Int>)

    @Query("DELETE FROM electricity_records WHERE id IN (:ids)")
    suspend fun hardDelete(ids: List<Int>)

    @Query("DELETE FROM electricity_records WHERE isDeleted = 1 AND deletedAt < :cutoffTimestamp")
    suspend fun deleteExpiredTrash(cutoffTimestamp: Long)

    // Add this for Backup
    @Query("SELECT * FROM electricity_records")
    suspend fun getAllRaw(): List<Electricity>


    @Query("DELETE FROM electricity_records")
    suspend fun clearTable()
}