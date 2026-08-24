package com.mudasir.smartledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "custom_daily_records")
data class CustomDailyRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ledgerId: Int,
    val monthIndex: Int,
    val year: Int,
    val monthName: String,
    val dailyEntries: List<CustomDailyEntry>,
    val totalAmount: Double = 0.0,
    val pricingJson: String? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
): Serializable

data class CustomDailyEntry(
    val day: Int,
    var values: MutableList<Double> = mutableListOf(0.0, 0.0, 0.0)
) : Serializable


