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
                    if (expense.deletedAt == 0L || expense.deletedAt == null) {
                        result.expenseAdded++
                    }
                    expense.copy(imagePaths = newPaths)
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
                    if (elec.deletedAt == 0L || elec.deletedAt == null) {
                        result.elecAdded++
                    }
                    elec.copy(imagePaths = newPaths)
                }

                // 3. Process Milk
                result.milkAdded = milk.count { it.deletedAt == 0L || it.deletedAt == null }


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

                    entry.copy(imagePaths = newPaths)
                }

                val customDailyRecords = db.customLedgerDao().getAllRawDailyRecords()

                customDailyRecords.forEach { record ->
                    val parentLedger = customLedgers.find { it.id == record.ledgerId }
                    if (!record.isDeleted && parentLedger != null && !parentLedger.isDeleted) {
                        val ledgerName = parentLedger.name
                        result.customCounts[ledgerName] = (result.customCounts[ledgerName] ?: 0) + 1
                    }
                }

                val backupData = BackupData(
                    timestamp = System.currentTimeMillis(),
                    expenses = processedExpenses,
                    electricity = processedElectricity,
                    milkRecords = milk,
                    customLedgers = customLedgers,
                    customEntries = processedCustomEntries,
                    customDailyRecords = customDailyRecords
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

                        if (existing == null) {
                            val paths = incoming.imagePaths.map { File(internalImgDir, it).absolutePath }
                            db.expenseDao().insertExpense(incoming.copy(id = 0, imagePaths = paths))

                            if (!incoming.isDeleted) result.expenseAdded++
                        } else {
                            val needsUnDelete = existing.isDeleted && !incoming.isDeleted
                            val isStatusMismatch = existing.isDeleted != incoming.isDeleted

                            if (needsUnDelete || isStatusMismatch) {
                                val updatedRecord = incoming.copy(
                                    id = existing.id,
                                    isDeleted = incoming.isDeleted,
                                    deletedAt = if (incoming.isDeleted) incoming.deletedAt else null
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

                        if (existing == null) {
                            val paths = incoming.imagePaths.map { File(internalImgDir, it).absolutePath }
                            db.electricityDao().insert(incoming.copy(id = 0, imagePaths = paths))

                            if (!incoming.isDeleted) result.elecAdded++
                        } else {
                            val needsUnDelete = existing.isDeleted && !incoming.isDeleted
                            val isStatusMismatch = existing.isDeleted != incoming.isDeleted

                            if (needsUnDelete || isStatusMismatch) {
                                val updatedRecord = incoming.copy(
                                    id = existing.id,
                                    isDeleted = incoming.isDeleted,
                                    deletedAt = if (incoming.isDeleted) incoming.deletedAt else null
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

                        if (existing == null) {
                            db.milkDao().insert(incoming.copy(id = 0))
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
                                    deletedAt = if (backupIsDeleted) incoming.deletedAt else null
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

                        if (existingLedger == null) {
                            val safeLedger = incomingLedger.copy(
                                id = 0,
                                dateMode = incomingLedger.dateMode,
                                ledgerType = incomingLedger.ledgerType
                            )
                            db.customLedgerDao().insertLedger(safeLedger)
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
                                db.customLedgerDao().insertEntry(incomingEntry.copy(
                                    id = 0,
                                    ledgerId = targetLedger.id,
                                    imagePaths = paths
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

                            if (existing == null) {
                                db.customLedgerDao().insertDailyRecord(
                                    incomingRecord.copy(id = 0, ledgerId = targetLedger.id)
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
                                            deletedAt = if (incomingRecord.isDeleted) incomingRecord.deletedAt else null
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

