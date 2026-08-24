package com.mudasir.smartledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

enum class FieldType { TEXT, NUMBER, DECIMAL }
enum class DateMode { SINGLE, RANGE, MONTH }
enum class LedgerType { RECORD, DAILY_LOG }

@Entity(tableName = "custom_ledgers")
data class CustomLedger(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val iconName: String,
    val fields: List<CustomField>,
    val hasPhotos: Boolean = true,
    val photoLimit: Int = 3,
    val dateMode: DateMode = DateMode.SINGLE,
    val ledgerType: LedgerType = LedgerType.RECORD,
    val unitLabel: String? = null,
    val pricePerUnit: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
) : Serializable

data class CustomField(
    val fieldName: String,
    val fieldType: FieldType
) : Serializable

data class PricingConfig(
    val isFixed: Boolean,
    val price: Double
) : Serializable

@Entity(
    tableName = "custom_entries",
    indices = [Index(value = ["ledgerId"])]
)
data class CustomEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ledgerId: Int,
    val date: Long,
    val amount: Double?,
    val dataJson: String,
    val imagePaths: List<String> = emptyList(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
) : Serializable