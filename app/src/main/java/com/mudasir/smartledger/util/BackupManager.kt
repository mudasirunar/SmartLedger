package com.mudasir.smartledger.util

import android.content.Context
import android.net.Uri
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.BackupData
import com.mudasir.smartledger.data.DateMode
import com.mudasir.smartledger.data.LedgerType
import com.mudasir.smartledger.data.RestoreResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private const val JSON_FILENAME = "ledger_data.json"
    private const val IMAGES_DIR = "images"

    suspend fun createZipBackup(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        checkCancel: () -> Boolean
    ): RestoreResult = withContext(Dispatchers.IO) {
        val result = RestoreResult()
        val cr = context.contentResolver

        cr.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->

                val expenses = db.expenseDao().getAllRaw()
                val electricity = db.electricityDao().getAllRaw()
                val milk = db.milkDao().getAllRaw()

                // 1. Process Expenses
                val processedExpenses = expenses.map { expense ->
                    if (checkCancel()) throw Exception("Backup Stopped")
                    val newPaths = expense.imagePaths.map { path ->
                        val file = File(path)
                        if (file.exists()) {
                            addToZip(zos, file, "$IMAGES_DIR/${file.name}")
                            file.name
                        } else path
                    }
                    if (!expense.isDeleted) {
                        result.expenseAdded++
                    }
                    val safeDeletedAt = if (expense.isDeleted) {
                        expense.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                    } else null
                    expense.copy(imagePaths = newPaths, deletedAt = safeDeletedAt)
                }

                // 2. Process Electricity
                val processedElectricity = electricity.map { elec ->
                    if (checkCancel()) throw Exception("Backup Stopped")
                    val newPaths = elec.imagePaths.map { path ->
                        val file = File(path)
                        if (file.exists()) {
                            addToZip(zos, file, "$IMAGES_DIR/${file.name}")
                            file.name
                        } else path
                    }
                    if (!elec.isDeleted) {
                        result.elecAdded++
                    }
                    val safeDeletedAt = if (elec.isDeleted) {
                        elec.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                    } else null
                    elec.copy(imagePaths = newPaths, deletedAt = safeDeletedAt)
                }

                // 3. Process Milk
                result.milkAdded = milk.count { !it.isDeleted }
                val processedMilk = milk.map { m ->
                    val safeDeletedAt = if (m.isDeleted) {
                        m.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                    } else null
                    m.copy(deletedAt = safeDeletedAt)
                }

                val customEntries = db.customLedgerDao().getAllRawEntries()
                val customLedgers = db.customLedgerDao().getAllLedgersList()

                // 4. Process Custom Entries
                val processedCustomEntries = customEntries.map { entry ->
                    if (checkCancel()) throw Exception("Backup Stopped")

                    val newPaths = entry.imagePaths.map { path ->
                        val file = File(path)
                        if (file.exists()) {
                            addToZip(zos, file, "$IMAGES_DIR/${file.name}")
                            file.name
                        } else path
                    }

                    val parentLedger = customLedgers.find { it.id == entry.ledgerId }

                    if (!entry.isDeleted && parentLedger != null && !parentLedger.isDeleted) {
                        val ledgerName = parentLedger.name
                        result.customCounts[ledgerName] = (result.customCounts[ledgerName] ?: 0) + 1
                    }

                    val safeDeletedAt = if (entry.isDeleted) {
                        entry.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                    } else null
                    entry.copy(imagePaths = newPaths, deletedAt = safeDeletedAt)
                }

                val customDailyRecords = db.customLedgerDao().getAllRawDailyRecords()

                customDailyRecords.forEach { record ->
                    val parentLedger = customLedgers.find { it.id == record.ledgerId }
                    if (!record.isDeleted && parentLedger != null && !parentLedger.isDeleted) {
                        val ledgerName = parentLedger.name
                        result.customCounts[ledgerName] = (result.customCounts[ledgerName] ?: 0) + 1
                    }
                }

                val processedCustomLedgers = customLedgers.map { l ->
                    val safeDeletedAt = if (l.isDeleted) {
                        l.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                    } else null
                    l.copy(deletedAt = safeDeletedAt)
                }

                val processedDailyRecords = customDailyRecords.map { r ->
                    val safeDeletedAt = if (r.isDeleted) {
                        r.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                    } else null
                    r.copy(deletedAt = safeDeletedAt)
                }

                val backupData = BackupData(
                    timestamp = System.currentTimeMillis(),
                    expenses = processedExpenses,
                    electricity = processedElectricity,
                    milkRecords = processedMilk,
                    customLedgers = processedCustomLedgers,
                    customEntries = processedCustomEntries,
                    customDailyRecords = processedDailyRecords
                )

                zos.putNextEntry(ZipEntry(JSON_FILENAME))
                zos.write(Gson().toJson(backupData).toByteArray())
                zos.closeEntry()
            }
        }
        return@withContext result
    }

    class RestoreCancelledException : Exception("Restore halted by user")

    suspend fun restoreFromZip(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        checkCancel: () -> Boolean
    ): RestoreResult = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val internalImgDir = File(context.filesDir, "ledger_images").apply { mkdirs() }
        var backupData: BackupData? = null
        val result = RestoreResult()

        // 1. EXTRACT & PARSE
        cr.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (checkCancel()) throw RestoreCancelledException()
                    when {
                        entry.name.contains(JSON_FILENAME) -> {
                            val bytes = zis.readBytes()
                            val jsonString = String(bytes)
                            backupData = Gson().fromJson(jsonString, BackupData::class.java)
                        }
                        entry.name.startsWith(IMAGES_DIR) && !entry.isDirectory -> {
                            val fileName = entry.name.substringAfterLast("/")
                            val destFile = File(internalImgDir, fileName)
                            destFile.outputStream().use { fos -> zis.copyTo(fos) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (backupData == null) throw Exception("Invalid Backup: JSON data not found inside ZIP.")

        // 2. DATABASE TRANSACTION
        db.runInTransaction {
            runBlocking {
                val existingExpenses = db.expenseDao().getAllRaw()
                val existingElec = db.electricityDao().getAllRaw()
                val existingMilk = db.milkDao().getAllRaw()

                backupData?.let { data ->
                    // --- EXPENSES ---
                    data.expenses.forEach { incoming ->
                        if (checkCancel()) throw RestoreCancelledException()

                        val existing = existingExpenses
                            .sortedBy { it.deletedAt ?: 0L }
                            .find { it.date == incoming.date && it.amount == incoming.amount }

                        val safeDeletedAt = if (incoming.isDeleted) {
                            incoming.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                        } else null

                        if (existing == null) {
                            val paths = incoming.imagePaths.map { File(internalImgDir, it).absolutePath }
                            db.expenseDao().insertExpense(incoming.copy(id = 0, imagePaths = paths, deletedAt = safeDeletedAt))

                            if (!incoming.isDeleted) result.expenseAdded++
                        } else {
                            val needsUnDelete = existing.isDeleted && !incoming.isDeleted
                            val isStatusMismatch = existing.isDeleted != incoming.isDeleted

                            if (needsUnDelete || isStatusMismatch) {
                                val updatedRecord = incoming.copy(
                                    id = existing.id,
                                    isDeleted = incoming.isDeleted,
                                    deletedAt = safeDeletedAt
                                )
                                db.expenseDao().updateExpense(updatedRecord)

                                if (!incoming.isDeleted) result.expenseAdded++
                            } else {
                                if (!incoming.isDeleted) result.expenseSkipped++
                            }
                        }
                    }

                    // --- ELECTRICITY  ---
                    data.electricity.forEach { incoming ->
                        if (checkCancel()) throw RestoreCancelledException()

                        val existing = existingElec
                            .sortedBy { it.deletedAt ?: 0L }
                            .find { it.startDate == incoming.startDate && it.endDate == incoming.endDate }

                        val safeDeletedAt = if (incoming.isDeleted) {
                            incoming.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                        } else null

                        if (existing == null) {
                            val paths = incoming.imagePaths.map { File(internalImgDir, it).absolutePath }
                            db.electricityDao().insert(incoming.copy(id = 0, imagePaths = paths, deletedAt = safeDeletedAt))

                            if (!incoming.isDeleted) result.elecAdded++
                        } else {
                            val needsUnDelete = existing.isDeleted && !incoming.isDeleted
                            val isStatusMismatch = existing.isDeleted != incoming.isDeleted

                            if (needsUnDelete || isStatusMismatch) {
                                val updatedRecord = incoming.copy(
                                    id = existing.id,
                                    isDeleted = incoming.isDeleted,
                                    deletedAt = safeDeletedAt
                                )
                                db.electricityDao().update(updatedRecord)

                                if (!incoming.isDeleted) result.elecAdded++
                            } else {
                                if (!incoming.isDeleted) result.elecSkipped++
                            }
                        }
                    }

                    // --- MILK ---
                    data.milkRecords.forEach { incoming ->
                        if (checkCancel()) throw RestoreCancelledException()

                        val existing = existingMilk
                            .sortedBy { it.deletedAt ?: 0L }
                            .find { it.monthIndex == incoming.monthIndex && it.year == incoming.year }

                        val safeDeletedAt = if (incoming.isDeleted) {
                            incoming.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                        } else null

                        if (existing == null) {
                            db.milkDao().insert(incoming.copy(id = 0, deletedAt = safeDeletedAt))
                            if (!incoming.isDeleted) {
                                result.milkAdded++
                            }
                        } else {
                            val localIsDeleted = existing.isDeleted
                            val backupIsDeleted = incoming.isDeleted

                            val isDataChanged = existing.dailyEntries != incoming.dailyEntries
                            val needsUnDelete = localIsDeleted && !backupIsDeleted

                            if (isDataChanged || needsUnDelete || localIsDeleted != backupIsDeleted) {
                                val updatedRecord = incoming.copy(
                                    id = existing.id,
                                    isDeleted = backupIsDeleted,
                                    deletedAt = safeDeletedAt
                                )

                                db.milkDao().update(updatedRecord)

                                if (!backupIsDeleted) {
                                    result.milkAdded++
                                }
                            } else {
                                if (!backupIsDeleted) {
                                    result.milkSkipped++
                                }
                            }
                        }
                    }

                    // --- CUSTOM LEDGER RESTORE ---
                    data.customLedgers.forEach { incomingLedger ->
                        if (checkCancel()) throw RestoreCancelledException()
                        val existingLedger = db.customLedgerDao().getAllLedgersList().find {
                            it.name.equals(incomingLedger.name, ignoreCase = true)
                        }
                        val safeDeletedAt = if (incomingLedger.isDeleted) {
                            incomingLedger.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                        } else null

                        if (existingLedger == null) {
                            val safeLedger = incomingLedger.copy(
                                id = 0,
                                isDeleted = incomingLedger.isDeleted,
                                deletedAt = safeDeletedAt,
                                dateMode = incomingLedger.dateMode,
                                ledgerType = incomingLedger.ledgerType
                            )
                            db.customLedgerDao().insertLedger(safeLedger)
                        } else {
                            if (existingLedger.isDeleted != incomingLedger.isDeleted) {
                                val updatedLedger = existingLedger.copy(
                                    isDeleted = incomingLedger.isDeleted,
                                    deletedAt = safeDeletedAt
                                )
                                db.customLedgerDao().insertLedger(updatedLedger)
                            }
                        }
                    }

                    // 2. RESTORE CUSTOM ENTRIES
                    val localLedgers = db.customLedgerDao().getAllLedgersList()

                    data.customEntries.forEach { incomingEntry ->
                        if (checkCancel()) throw RestoreCancelledException()

                        val incomingLedgerName = data.customLedgers.find { it.id == incomingEntry.ledgerId }?.name
                        val targetLedger = localLedgers.find { it.name == incomingLedgerName }

                        if (targetLedger != null) {
                            val isDuplicate = db.customLedgerDao().checkEntryExists(
                                targetLedger.id,
                                incomingEntry.date,
                                incomingEntry.amount ?: 0.0,
                                incomingEntry.dataJson
                            )

                            if (!isDuplicate) {
                                val paths = incomingEntry.imagePaths.map { fileName ->
                                    File(internalImgDir, fileName).absolutePath
                                }
                                val safeDeletedAt = if (incomingEntry.isDeleted) {
                                    incomingEntry.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                                } else null
                                db.customLedgerDao().insertEntry(incomingEntry.copy(
                                    id = 0,
                                    ledgerId = targetLedger.id,
                                    imagePaths = paths,
                                    isDeleted = incomingEntry.isDeleted,
                                    deletedAt = safeDeletedAt
                                ))

                                if (!incomingEntry.isDeleted && !targetLedger.isDeleted) {
                                    val name = targetLedger.name
                                    result.customCounts[name] = (result.customCounts[name] ?: 0) + 1
                                }
                            } else {
                                result.customSkipped++
                            }
                        }
                    }

                    // --- RESTORE CUSTOM DAILY RECORDS ---
                    val dailyRecordsToRestore = data.customDailyRecords
                    dailyRecordsToRestore.forEach { incomingRecord ->
                        if (checkCancel()) throw RestoreCancelledException()

                        val incomingLedgerName = data.customLedgers.find { it.id == incomingRecord.ledgerId }?.name
                        val targetLedger = localLedgers.find { it.name == incomingLedgerName }

                        if (targetLedger != null) {
                            val existing = db.customLedgerDao().getDailyRecordByMonthYear(
                                targetLedger.id,
                                incomingRecord.monthIndex,
                                incomingRecord.year
                            )

                            val safeDeletedAt = if (incomingRecord.isDeleted) {
                                incomingRecord.deletedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
                            } else null

                            if (existing == null) {
                                db.customLedgerDao().insertDailyRecord(
                                    incomingRecord.copy(id = 0, ledgerId = targetLedger.id, deletedAt = safeDeletedAt)
                                )
                                if (!incomingRecord.isDeleted && !targetLedger.isDeleted) {
                                    val name = targetLedger.name
                                    result.customCounts[name] = (result.customCounts[name] ?: 0) + 1
                                }
                            } else {
                                if (existing.dailyEntries != incomingRecord.dailyEntries ||
                                    existing.totalAmount != incomingRecord.totalAmount ||
                                    (existing.isDeleted && !incomingRecord.isDeleted)) {

                                    db.customLedgerDao().updateDailyRecord(
                                        incomingRecord.copy(
                                            id = existing.id,
                                            ledgerId = targetLedger.id,
                                            isDeleted = incomingRecord.isDeleted,
                                            deletedAt = safeDeletedAt
                                        )
                                    )
                                    if (!incomingRecord.isDeleted && !targetLedger.isDeleted) {
                                        val name = targetLedger.name
                                        result.customCounts[name] = (result.customCounts[name] ?: 0) + 1
                                    }
                                } else {
                                    result.customSkipped++
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. AUTO-CLEANUP EXPIRED TRASH POST-RESTORE
        val fifteenDaysAgo = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(15)
        db.expenseDao().deleteExpiredTrash(fifteenDaysAgo)
        db.electricityDao().deleteExpiredTrash(fifteenDaysAgo)
        db.milkDao().deleteExpiredTrash(fifteenDaysAgo)
        db.customLedgerDao().deleteExpiredTrash(fifteenDaysAgo)
        db.customLedgerDao().deleteExpiredDailyRecords(fifteenDaysAgo)
        db.customLedgerDao().autoCleanExpiredLedgers(fifteenDaysAgo)

        return@withContext result
    }

    private fun addToZip(zos: ZipOutputStream, file: File, zipPath: String) {
        try {
            FileInputStream(file).use { fis ->
                zos.putNextEntry(ZipEntry(zipPath))
                fis.copyTo(zos)
                zos.closeEntry()
            }
        } catch (e: Exception) { /* Log skip if file is busy or missing */ }
    }
}

