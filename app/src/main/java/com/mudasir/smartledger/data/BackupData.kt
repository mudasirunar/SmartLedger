package com.mudasir.smartledger.data

data class BackupData(
    val timestamp: Long,
    val expenses: List<Expense>,
    val electricity: List<Electricity>,
    val milkRecords: List<MilkRecord>,
    val customLedgers: List<CustomLedger>,
    val customEntries: List<CustomEntry>,
    val customDailyRecords: List<CustomDailyRecord> = emptyList()

)