package com.example.smartledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalcDao {
    @Insert
    suspend fun insert(history: CalcHistory)

    @Query("SELECT * FROM calc_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CalcHistory>>

    @Query("DELETE FROM calc_history")
    suspend fun clearHistory()
}
