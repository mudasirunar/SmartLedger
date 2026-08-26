package com.mudasir.smartledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    // --- UPDATED NORMAL QUERIES ---
    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllExpensesByDateDesc(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY date DESC")
    suspend fun getActiveRaw(): List<Expense>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY date ASC")
    fun getAllExpensesByDateAsc(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY amount DESC")
    fun getAllExpensesByAmountDesc(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY amount ASC")
    fun getAllExpensesByAmountAsc(): Flow<List<Expense>>


    // 1. Get Trash Items (Sorted by Deleted Date DESC - Last deleted on top)
    @Query("SELECT * FROM expenses WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getTrashExpenses(): Flow<List<Expense>>

    // 2. Soft Delete (Send to Trash)
    @Query("UPDATE expenses SET isDeleted = 1, deletedAt = :timestamp WHERE id IN (:ids)")
    suspend fun softDeleteExpenses(ids: List<Int>, timestamp: Long)

    // 3. Restore (Recover from Trash)
    @Query("UPDATE expenses SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restoreExpenses(ids: List<Int>)

    // 4. Hard Delete (Permanent)
    @Query("DELETE FROM expenses WHERE id IN (:ids)")
    suspend fun hardDeleteExpenses(ids: List<Int>)

    // Auto-Cleanup (Delete items older than 15 days)
    @Query("DELETE FROM expenses WHERE isDeleted = 1 AND deletedAt < :cutoffTimestamp")
    suspend fun deleteExpiredTrash(cutoffTimestamp: Long)

    @Query("DELETE FROM expenses")
    suspend fun clearTable()

    // Add this for Backup
    @Query("SELECT * FROM expenses")
    suspend fun getAllRaw(): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Expense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Update
    suspend fun updateExpense(expense: Expense)
}