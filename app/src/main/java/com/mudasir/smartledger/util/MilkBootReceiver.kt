package com.mudasir.smartledger.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MilkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            MilkNotificationHandler.scheduleMorningNotification(context)
        }
    }
}