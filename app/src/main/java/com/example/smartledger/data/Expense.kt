package com.example.smartledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val amount: Double,
    val date: Long,
    val imagePaths: List<String> = emptyList(),

    // Trash Bin Fields
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null, // Timestamp when it was deleted

) : Serializable