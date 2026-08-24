package com.mudasir.smartledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "electricity_records")
data class Electricity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startDate: Long,
    val endDate: Long,
    val startUnits: Double?,
    val endUnits: Double?,
    val totalUnits: Double?,
    val amount: Double?,
    val description: String,
    val imagePaths: List<String> = emptyList(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,


) : Serializable