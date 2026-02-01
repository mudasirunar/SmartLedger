package com.example.smartledger.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.work.*
import com.example.smartledger.R
import com.example.smartledger.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object MilkNotificationConstants {
    const val CHANNEL_ID = "milk_notifications"
    const val NOTIFICATION_ID = 5001
    const val MISSED_NOTIFICATION_ID = 5002
    const val KEY_TEXT_REPLY = "key_milk_liters"
    const val WORK_NAME_MORNING = "MilkMorningWork"
    const val WORK_NAME_MIDNIGHT = "MilkMidnightWork"
}

/**
 * 1. MORNING WORKER: Checks DB and triggers the 20s delayed notification
 */
class MilkMorningWorker(val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(context)
        val cal = Calendar.getInstance()
        val monthIdx = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)

        val allRecords = db.milkDao().getAllRaw()
        val activeMonth = allRecords.find {
            it.monthIndex == monthIdx && it.year == year && !it.isDeleted
        }

        // Inside MilkMorningWorker.doWork()
        val todayDay = cal.get(Calendar.DAY_OF_MONTH)
        val todayEntry = activeMonth?.dailyEntries?.find { it.day == todayDay }

        if (todayEntry != null && todayEntry.liters > 0.0) {
            // Already filled! Just schedule midnight and skip notification
            MilkNotificationHandler.scheduleMidnightCheck(context)
            return Result.success()
        }

        // Safety Net: Month must exist and NOT be in trash
        if (activeMonth == null) {
            MilkNotificationHandler.scheduleMorningNotification(context)
            return Result.success()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure()
        }

        showMilkInputNotification(context, activeMonth)
        MilkNotificationHandler.scheduleMidnightCheck(context)
        return Result.success()
    }

    private fun showMilkInputNotification(context: Context, activeMonth: com.example.smartledger.data.MilkRecord) {
        // Build Intent for ViewMilkActivity
        val contentIntent = Intent(context, com.example.smartledger.activity.ViewMilkActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("milk_data", activeMonth)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context, 100, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Setup Action Intents
        val replyIntent = Intent(context, MilkNotificationReceiver::class.java).apply { action = "ACTION_MILK_REPLY" }
        val replyPendingIntent = PendingIntent.getBroadcast(context, 0, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        val quickIntent = Intent(context, MilkNotificationReceiver::class.java).apply {
            action = "ACTION_MILK_REPLY"
            putExtra("QUICK_VALUE", 1.5)
        }
        val quickPendingIntent = PendingIntent.getBroadcast(context, 1, quickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        val remoteInput = RemoteInput.Builder(MilkNotificationConstants.KEY_TEXT_REPLY)
            .setLabel("Enter Liters")
            .build()

        val typeAction = NotificationCompat.Action.Builder(R.drawable.ic_water_drop, "Submit", replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build()

        val quickAction = NotificationCompat.Action.Builder(R.drawable.ic_water_drop, "1.5 Liters", quickPendingIntent)
            .build()

        val morningPattern = longArrayOf(0, 200, 100, 200)

        val builder = NotificationCompat.Builder(context, MilkNotificationConstants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle("Milk Entry")
            .setContentText("Enter liters for today")
            .setVibrate(morningPattern)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(typeAction)
            .addAction(quickAction)

        MilkNotificationHandler.createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(MilkNotificationConstants.NOTIFICATION_ID, builder.build())
    }
}

/**
 * 2. MIDNIGHT WORKER: Checks DB and shows MISSED notification
 */
class MilkMidnightWorker(val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(MilkNotificationConstants.NOTIFICATION_ID)

        val db = AppDatabase.getDatabase(context)
        val cal = Calendar.getInstance()

        // At Midnight, we check "Yesterday"
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val targetDay = cal.get(Calendar.DAY_OF_MONTH)
        val monthIdx = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)

        val allRecords = withContext(Dispatchers.IO) { db.milkDao().getAllRaw() }
        val activeMonth = allRecords.find {
            it.monthIndex == monthIdx && it.year == year && !it.isDeleted
        }

        // --- ROBUST CHECK ---
        val yesterdayEntry = activeMonth?.dailyEntries?.find { it.day == targetDay }

        if (yesterdayEntry != null && yesterdayEntry.liters > 0.0) {
            MilkNotificationHandler.scheduleMorningNotification(context)
            return Result.success()
        }

        // --- IF DATA IS MISSING, SHOW NOTIFICATION ---
        val contentIntent = Intent(context, com.example.smartledger.activity.ViewMilkActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("milk_data", activeMonth)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 101, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val missedDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(cal.time)

        // RESTORED VIBRATION PATTERN
        val missedPattern = longArrayOf(0, 500, 200, 500, 200, 500)

        val builder = NotificationCompat.Builder(context, MilkNotificationConstants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle("Missed Yesterday's Milk")
            .setContentText("You forgot to record milk for $missedDate")
            .setVibrate(missedPattern)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        MilkNotificationHandler.createNotificationChannel(context)
        manager.notify(MilkNotificationConstants.MISSED_NOTIFICATION_ID, builder.build())
        MilkNotificationHandler.scheduleMorningNotification(context)
        return Result.success()
    }
}

/**
 * 3. RECEIVER: Handles input, dismisses instantly, and shows TOAST
 */
class MilkNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "ACTION_MILK_REPLY") {
            // 1. DISMISS NOTIFICATION INSTANTLY
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(MilkNotificationConstants.NOTIFICATION_ID)

            // 2. EXTRACT DATA
            val quickValue = intent.getDoubleExtra("QUICK_VALUE", -1.0)
            val results = RemoteInput.getResultsFromIntent(intent)
            val input = results?.getCharSequence(MilkNotificationConstants.KEY_TEXT_REPLY)?.toString()

            val liters = if (quickValue != -1.0) quickValue else (input?.toDoubleOrNull() ?: 0.0)

            // 3. DATABASE UPDATE & TOAST
            val db = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                updateTodayMilkRecord(db, liters)

                // Show confirmation Toast on Main Thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "$liters Liters added successfully", Toast.LENGTH_SHORT).show()
                }

                WorkManager.getInstance(context).cancelUniqueWork(MilkNotificationConstants.WORK_NAME_MIDNIGHT)
                MilkNotificationHandler.scheduleMorningNotification(context)
            }
        }
    }

    private suspend fun updateTodayMilkRecord(db: AppDatabase, liters: Double) {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthIdx = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)

        val all = db.milkDao().getAllRaw()
        val record = all.find { it.monthIndex == monthIdx && it.year == year && !it.isDeleted } ?: return

        val updatedEntries = record.dailyEntries.map {
            if (it.day == day) it.copy(liters = liters) else it
        }

        val totalL = updatedEntries.sumOf { it.liters }
        db.milkDao().update(record.copy(
            dailyEntries = updatedEntries,
            totalLiters = totalL,
            totalAmount = totalL * record.pricePerLiter
        ))
    }
}

/**
 * 4. SCHEDULER: Staggered timings for testing
 */
object MilkNotificationHandler {

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                MilkNotificationConstants.CHANNEL_ID,
                "Milk Records",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    // IMPORTANT: SET TO TRUE FOR TESTING ONLY
    private const val IS_TESTING = false

    fun scheduleMorningNotification(context: Context) {
        val delay = if (IS_TESTING) 20L else calculateDelay(5, 0)
        val unit = if (IS_TESTING) TimeUnit.SECONDS else TimeUnit.MILLISECONDS

        val request = OneTimeWorkRequestBuilder<MilkMorningWorker>()
            .setInitialDelay(delay, unit)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MilkNotificationConstants.WORK_NAME_MORNING,
            ExistingWorkPolicy.REPLACE, request
        )
    }

    fun scheduleMidnightCheck(context: Context) {
        val delay = if (IS_TESTING) 60L else calculateDelay(0, 0)
        val unit = if (IS_TESTING) TimeUnit.SECONDS else TimeUnit.MILLISECONDS

        val request = OneTimeWorkRequestBuilder<MilkMidnightWorker>()
            .setInitialDelay(delay, unit)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MilkNotificationConstants.WORK_NAME_MIDNIGHT,
            ExistingWorkPolicy.REPLACE, request
        )
    }

    private fun calculateDelay(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
