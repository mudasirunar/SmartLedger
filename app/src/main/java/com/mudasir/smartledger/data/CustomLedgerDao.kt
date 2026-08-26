package com.mudasir.smartledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomLedgerDao {
    // --- TEMPLATE QUERIES ---
    @Query("SELECT * FROM custom_ledgers WHERE isDeleted = 0")
    fun getAllLedgers(): Flow<List<CustomLedger>>

    @Query("SELECT * FROM custom_ledgers")
    suspend fun getAllLedgersList(): List<CustomLedger>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedger(ledger: CustomLedger)

    @Query("DELETE FROM custom_ledgers WHERE id = :ledgerId")
    suspend fun deleteLedgerById(ledgerId: Int)

    @Query("SELECT COUNT(*) FROM custom_entries WHERE ledgerId = :ledgerId AND isDeleted = 1")
    suspend fun getTrashedEntriesCount(ledgerId: Int): Int

    // Get ledger by ID to resolve the name for the CustomEntryItem
    @Query("SELECT * FROM custom_ledgers WHERE id = :id")
    suspend fun getLedgerById(id: Int): CustomLedger?

    // Get total count of entries for a ledger (including deleted ones)
    @Query("SELECT COUNT(*) FROM custom_entries WHERE ledgerId = :ledgerId")
    suspend fun getEntriesCountForLedger(ledgerId: Int): Int

    // Get deleted ledgers for the Trash Bin
    @Query("SELECT * FROM custom_ledgers WHERE isDeleted = 1")
    fun getTrashedLedgers(): Flow<List<CustomLedger>>

    // SOFT DELETE: Marks the ledger as deleted. No need to touch individual entries!
    @Query("UPDATE custom_ledgers SET isDeleted = 1, deletedAt = :timestamp WHERE id = :ledgerId")
    suspend fun softDeleteLedger(ledgerId: Int, timestamp: Long)

    @Query("UPDATE custom_daily_records SET isDeleted = 1, deletedAt = :timestamp WHERE id IN (:ids)")
    suspend fun softDeleteDailyRecords(ids: List<Int>, timestamp: Long)

    @Query("UPDATE custom_ledgers SET isDeleted = 0, deletedAt = NULL WHERE id = :ledgerId")
    suspend fun restoreLedger(ledgerId: Int)

    // HARD DELETE
    @Transaction
    suspend fun autoCleanExpiredLedgers(cutoff: Long) {
        val expiredIds = getExpiredLedgerIds(cutoff)

        expiredIds.forEach { id ->
            permanentlyDeleteLedger(id)
        }
    }

    // Trashed daily records whose ledger is NOT deleted
    @Query("SELECT * FROM custom_daily_records WHERE isDeleted = 1")
    fun getTrashedDailyRecords(): Flow<List<CustomDailyRecord>>

    // Count daily records deleted WITH the ledger (for the "X Records" label)
    @Query("SELECT COUNT(*) FROM custom_daily_records WHERE ledgerId = :ledgerId AND deletedAt = :ledgerDeletedAt")
    suspend fun getDailyRecordsCountDeletedWithLedger(ledgerId: Int, ledgerDeletedAt: Long): Int

    // Soft delete daily records when ledger is deleted
    @Query("UPDATE custom_daily_records SET isDeleted = 1, deletedAt = :time WHERE ledgerId = :lId AND isDeleted = 0")
    suspend fun softDeleteActiveDailyRecordsByLedger(lId: Int, time: Long)

    // Restore daily records bundled with ledger deletion
    @Query("UPDATE custom_daily_records SET isDeleted = 0, deletedAt = NULL WHERE ledgerId = :lId AND deletedAt = :ledgerDeletedAt")
    suspend fun restoreDailyRecordsByLedgerTimestamp(lId: Int, ledgerDeletedAt: Long)

    // Hard delete daily records for a ledger
    @Query("DELETE FROM custom_daily_records WHERE ledgerId = :ledgerId")
    suspend fun hardDeleteDailyRecordsByLedger(ledgerId: Int)

    // Hard delete specific daily records
    @Query("DELETE FROM custom_daily_records WHERE id IN (:ids)")
    suspend fun hardDeleteDailyRecords(ids: List<Int>)

    // Restore specific daily records
    @Query("UPDATE custom_daily_records SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restoreDailyRecords(ids: List<Int>)

    @Query("SELECT * FROM custom_daily_records")
    suspend fun getAllRawDailyRecords(): List<CustomDailyRecord>

    @Query("SELECT * FROM custom_daily_records WHERE ledgerId = :ledgerId AND monthIndex = :month AND year = :year LIMIT 1")
    suspend fun getDailyRecordByMonthYear(ledgerId: Int, month: Int, year: Int): CustomDailyRecord?

    @Query("SELECT id FROM custom_ledgers WHERE isDeleted = 1 AND deletedAt < :cutoff")
    suspend fun getExpiredLedgerIds(cutoff: Long): List<Int>
    @Transaction
    suspend fun permanentlyDeleteLedger(ledgerId: Int) {
        deleteEntriesByLedgerId(ledgerId)
        deleteLedgerById(ledgerId)
    }

    @Query("DELETE FROM custom_entries WHERE ledgerId = :ledgerId")
    suspend fun deleteEntriesByLedgerId(ledgerId: Int)

    // --- ENTRY QUERIES (Active Records) ---
    @Query("SELECT * FROM custom_entries WHERE ledgerId = :lId AND isDeleted = 0 ORDER BY date DESC")
    fun getEntriesByDateDesc(lId: Int): Flow<List<CustomEntry>>

    @Query("SELECT * FROM custom_entries WHERE ledgerId = :lId AND isDeleted = 0 ORDER BY date DESC")
    suspend fun getActiveEntriesRaw(lId: Int): List<CustomEntry>

    @Query("SELECT * FROM custom_entries WHERE ledgerId = :lId AND isDeleted = 0 ORDER BY date ASC")
    fun getEntriesByDateAsc(lId: Int): Flow<List<CustomEntry>>

    @Query("SELECT * FROM custom_entries WHERE ledgerId = :lId AND isDeleted = 0 ORDER BY amount DESC")
    fun getEntriesByAmountDesc(lId: Int): Flow<List<CustomEntry>>

    @Query("SELECT * FROM custom_entries WHERE ledgerId = :lId AND isDeleted = 0 ORDER BY amount ASC")
    fun getEntriesByAmountAsc(lId: Int): Flow<List<CustomEntry>>


    @Query("SELECT * FROM custom_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): CustomEntry?

    @Query("SELECT * FROM custom_entries WHERE isDeleted = 1")
    fun getAllTrashEntries(): Flow<List<CustomEntry>>

    @Query("SELECT * FROM custom_daily_records WHERE ledgerId = :ledgerId AND isDeleted = 0 ORDER BY year DESC, monthIndex DESC")
    fun getDailyRecordsByLedger(ledgerId: Int): Flow<List<CustomDailyRecord>>

    @Query("SELECT * FROM custom_daily_records WHERE ledgerId = :ledgerId AND isDeleted = 0 ORDER BY year DESC, monthIndex DESC")
    fun getDailyRecordsByDateDesc(ledgerId: Int): Flow<List<CustomDailyRecord>>

    @Query("SELECT * FROM custom_daily_records WHERE ledgerId = :ledgerId AND isDeleted = 0 ORDER BY year DESC, monthIndex DESC")
    suspend fun getActiveDailyRecordsRaw(ledgerId: Int): List<CustomDailyRecord>

    @Query("SELECT * FROM custom_daily_records WHERE ledgerId = :ledgerId AND isDeleted = 0 ORDER BY year ASC, monthIndex ASC")
    fun getDailyRecordsByDateAsc(ledgerId: Int): Flow<List<CustomDailyRecord>>

    @Query("SELECT * FROM custom_daily_records WHERE ledgerId = :ledgerId AND isDeleted = 0 ORDER BY totalAmount DESC")
    fun getDailyRecordsByAmountDesc(ledgerId: Int): Flow<List<CustomDailyRecord>>

    @Query("SELECT * FROM custom_daily_records WHERE ledgerId = :ledgerId AND isDeleted = 0 ORDER BY totalAmount ASC")
    fun getDailyRecordsByAmountAsc(ledgerId: Int): Flow<List<CustomDailyRecord>>

    // --- ACTIONS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CustomEntry)

    @Update
    suspend fun updateEntry(entry: CustomEntry)

    @Insert
    suspend fun insertDailyRecord(record: CustomDailyRecord): Long

    @Query("SELECT * FROM custom_daily_records WHERE ledgerId = :ledgerId AND monthIndex = :month AND year = :year LIMIT 1")
    suspend fun getDailyRecord(ledgerId: Int, month: Int, year: Int): CustomDailyRecord?

    @Update
    suspend fun updateDailyRecord(record: CustomDailyRecord)

    @Query("UPDATE custom_entries SET isDeleted = 1, deletedAt = :timestamp WHERE id IN (:ids)")
    suspend fun softDeleteEntries(ids: List<Long>, timestamp: Long)

    @Query("UPDATE custom_entries SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restoreEntries(ids: List<Long>)

    @Query("DELETE FROM custom_entries WHERE id IN (:ids)")
    suspend fun hardDeleteEntries(ids: List<Long>)

    @Query("DELETE FROM custom_entries WHERE isDeleted = 1 AND deletedAt < :cutoff")
    suspend fun deleteExpiredTrash(cutoff: Long)

    @Transaction
    suspend fun deleteLedgerAndAllEntries(ledgerId: Int) {
        val currentTime = System.currentTimeMillis()
        softDeleteEntriesByLedger(ledgerId, currentTime)
        deleteLedgerById(ledgerId)
    }

    // 1. Delete only records that aren't already deleted (The "Active" ones)
    @Query("UPDATE custom_entries SET isDeleted = 1, deletedAt = :time WHERE ledgerId = :lId AND isDeleted = 0")
    suspend fun softDeleteOnlyActiveEntriesByLedger(lId: Int, time: Long)

    // 2. Count records deleted with the ledger (Matches the Ledger's deletedAt)
    @Query("SELECT COUNT(*) FROM custom_entries WHERE ledgerId = :ledgerId AND deletedAt = :ledgerDeletedAt")
    suspend fun getCountDeletedWithLedger(ledgerId: Int, ledgerDeletedAt: Long): Int

    // 3. Restore ONLY records associated with that specific ledger deletion event
    @Query("UPDATE custom_entries SET isDeleted = 0, deletedAt = NULL WHERE ledgerId = :lId AND deletedAt = :ledgerDeletedAt")
    suspend fun restoreEntriesByLedgerTimestamp(lId: Int, ledgerDeletedAt: Long)

    // 1. Updated DAO Method to use a specific timestamp
    @Query("UPDATE custom_entries SET isDeleted = 1, deletedAt = :time WHERE ledgerId = :lId AND isDeleted = 0")
    suspend fun softDeleteEntriesByLedger(lId: Int, time: Long)

    // --- UTILS & BACKUP ---
    @Query("""
    SELECT EXISTS(
        SELECT 1 FROM custom_entries 
        WHERE ledgerId = :lId 
        AND date = :d 
        AND (ABS(amount - :a) < 0.001 OR (amount IS NULL AND :a = 0.0))
        AND dataJson = :json
    )
""")
    suspend fun checkEntryExists(lId: Int, d: Long, a: Double, json: String): Boolean

    @Query("SELECT * FROM custom_entries")
    suspend fun getAllRawEntries(): List<CustomEntry>

    @Query("DELETE FROM custom_entries")
    suspend fun clearEntryTable()
}