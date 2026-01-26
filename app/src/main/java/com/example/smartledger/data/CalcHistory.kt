package com.example.smartledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey@Entity(tableName = "calc_history")
data class CalcHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)
