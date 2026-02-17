package com.example.smartledger.util

import android.content.Context
import android.net.Uri
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.BackupData
import com.example.smartledger.data.RestoreResult
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

                // 1. Process Expenses (Correctly counting only active)
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

                // 2. Process Electricity (Correctly counting only active)
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

                // Save the timestamp and the FULL lists (including trash) to the JSON
                val backupData = BackupData(
                    timestamp = System.currentTimeMillis(),
                    expenses = processedExpenses,
                    electricity = processedElectricity,
                    milkRecords = milk // Full list preserved in ZIP
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

        // 1. EXTRACT & PARSE (Remains the same)
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

                        // Priority Search: Look for Active version of this expense first
                        val existing = existingExpenses
                            .sortedBy { it.deletedAt ?: 0L }
                            .find { it.date == incoming.date && it.amount == incoming.amount }

                        if (existing == null) {
                            // Brand new record: Insert as is
                            val paths = incoming.imagePaths.map { File(internalImgDir, it).absolutePath }
                            db.expenseDao().insertExpense(incoming.copy(id = 0, imagePaths = paths))

                            if (!incoming.isDeleted) result.expenseAdded++
                        } else {
                            val needsUnDelete = existing.isDeleted && !incoming.isDeleted
                            val isStatusMismatch = existing.isDeleted != incoming.isDeleted

                            if (needsUnDelete || isStatusMismatch) {
                                // Force status to match backup (Reset isDeleted/deletedAt)
                                val updatedRecord = incoming.copy(
                                    id = existing.id,
                                    isDeleted = incoming.isDeleted,
                                    deletedAt = if (incoming.isDeleted) incoming.deletedAt else null
                                )
                                db.expenseDao().updateExpense(updatedRecord)

                                if (!incoming.isDeleted) result.expenseAdded++
                            } else {
                                // Everything identical (Duplicate)
                                if (!incoming.isDeleted) result.expenseSkipped++
                            }
                        }
                    }

                    // --- ELECTRICITY  ---
                    data.electricity.forEach { incoming ->
                        if (checkCancel()) throw RestoreCancelledException()

                        // Priority Search: Look for Active version of this bill cycle first
                        val existing = existingElec
                            .sortedBy { it.deletedAt ?: 0L }
                            .find { it.startDate == incoming.startDate && it.endDate == incoming.endDate }

                        if (existing == null) {
                            // Brand new record
                            val paths = incoming.imagePaths.map { File(internalImgDir, it).absolutePath }
                            db.electricityDao().insert(incoming.copy(id = 0, imagePaths = paths))

                            if (!incoming.isDeleted) result.elecAdded++
                        } else {
                            val needsUnDelete = existing.isDeleted && !incoming.isDeleted
                            val isStatusMismatch = existing.isDeleted != incoming.isDeleted

                            if (needsUnDelete || isStatusMismatch) {
                                // Force status to match backup
                                val updatedRecord = incoming.copy(
                                    id = existing.id,
                                    isDeleted = incoming.isDeleted,
                                    deletedAt = if (incoming.isDeleted) incoming.deletedAt else null
                                )
                                db.electricityDao().update(updatedRecord)

                                if (!incoming.isDeleted) result.elecAdded++
                            } else {
                                // Everything identical (Duplicate)
                                if (!incoming.isDeleted) result.elecSkipped++
                            }
                        }
                    }

                    // --- MILK ---
                    data.milkRecords.forEach { incoming ->
                        if (checkCancel()) throw RestoreCancelledException()

                        // 1. Search for Month/Year
                        val existing = existingMilk
                            .sortedBy { it.deletedAt ?: 0L } // Keep active ones first
                            .find { it.monthIndex == incoming.monthIndex && it.year == incoming.year }

                        if (existing == null) {
                            // Brand new: Insert exactly as it is in backup
                            db.milkDao().insert(incoming.copy(id = 0))
                            if (!incoming.isDeleted) {
                                result.milkAdded++
                            }
                        } else {
                            val localIsDeleted = existing.isDeleted
                            val backupIsDeleted = incoming.isDeleted

                            val isDataChanged = existing.dailyEntries != incoming.dailyEntries
                            // We check if the backup wants it active but local has it deleted
                            val needsUnDelete = localIsDeleted && !backupIsDeleted

                            if (isDataChanged || needsUnDelete || localIsDeleted != backupIsDeleted) {
                                // FIX: If backup says it's active, we MUST force isDeleted = false and deletedAt = null
                                val updatedRecord = incoming.copy(
                                    id = existing.id,
                                    isDeleted = backupIsDeleted,
                                    deletedAt = if (backupIsDeleted) incoming.deletedAt else null
                                )

                                db.milkDao().update(updatedRecord)

                                // Only count in the summary if it ended up as an active record
                                if (!backupIsDeleted) {
                                    result.milkAdded++
                                }
                            } else {
                                // Everything is identical (both active or both trashed)
                                if (!backupIsDeleted) {
                                    result.milkSkipped++
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

