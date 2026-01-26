package com.example.smartledger.data

sealed class TrashItem {
    abstract val deletedAt: Long?

    data class ExpenseItem(val data: Expense) : TrashItem() { override val deletedAt = data.deletedAt }
    data class ElectricityItem(val data: Electricity) : TrashItem() { override val deletedAt = data.deletedAt }
    data class MilkItem(val data: MilkRecord) : TrashItem() { override val deletedAt = data.deletedAt } // NEW
}