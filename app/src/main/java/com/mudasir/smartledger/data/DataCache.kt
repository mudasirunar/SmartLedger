package com.mudasir.smartledger.data

import java.util.concurrent.ConcurrentHashMap

object DataCache {
    @Volatile
    var cachedElectricity: List<Electricity>? = null

    @Volatile
    var cachedExpenses: List<Expense>? = null

    @Volatile
    var cachedMilk: List<MilkRecord>? = null

    @Volatile
    var cachedCustomLedgers: List<CustomLedger>? = null

    val cachedCustomEntries = ConcurrentHashMap<Int, List<CustomEntry>>()
    val cachedDailyEntries = ConcurrentHashMap<Int, List<CustomDailyRecord>>()

    fun clear() {
        cachedElectricity = null
        cachedExpenses = null
        cachedMilk = null
        cachedCustomLedgers = null
        cachedCustomEntries.clear()
        cachedDailyEntries.clear()
    }
}
