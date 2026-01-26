package com.example.smartledger.data

data class BackupData(
    val timestamp: Long,
    val expenses: List<Expense>,
    val electricity: List<Electricity>,
    val milkRecords: List<MilkRecord>
)