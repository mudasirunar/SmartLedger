package com.example.smartledger.data

sealed class TrashItem {
    abstract val deletedAt: Long?

    data class ExpenseItem(val data: Expense) : TrashItem() { override val deletedAt = data.deletedAt }
    data class ElectricityItem(val data: Electricity) : TrashItem() { override val deletedAt = data.deletedAt }
    data class MilkItem(val data: MilkRecord) : TrashItem() { override val deletedAt = data.deletedAt }
    data class CustomEntryItem(val entry: CustomEntry, val ledgerName: String) : TrashItem() { override val deletedAt = entry.deletedAt }
    data class TrashedLedgerItem(val ledger: CustomLedger, val entryCount: Int) : TrashItem() {
        override val deletedAt = ledger.deletedAt
    }
    data class CustomDailyRecordItem(val record: CustomDailyRecord, val ledgerName: String) : TrashItem() { override val deletedAt = record.deletedAt }

}