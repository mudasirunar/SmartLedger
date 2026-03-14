package com.example.smartledger.data

data class RestoreResult(
    var expenseAdded: Int = 0,
    var elecAdded: Int = 0,
    var milkAdded: Int = 0,
    val customCounts: MutableMap<String, Int> = mutableMapOf(),
    var customLedgersAdded: Int = 0,
    var customDailyRecordsAdded: Int = 0,
    var expenseSkipped: Int = 0,
    var elecSkipped: Int = 0,
    var milkSkipped: Int = 0,
    var customSkipped: Int = 0,
    var customDailyRecordsSkipped: Int = 0
)