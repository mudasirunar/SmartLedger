package com.example.smartledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "milk_records")
data class MilkRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val monthName: String,
    val monthIndex: Int,
    val year: Int,
    val pricePerLiter: Double,
    val totalLiters: Double = 0.0,
    val totalAmount: Double = 0.0,
    val dailyEntries: List<DailyEntry> = emptyList(),

    // Trash Bin Fields
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,


) : Serializable

data class DailyEntry(
    val day: Int,
    var liters: Double = 0.0
) : Serializable