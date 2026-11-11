package com.smsforwarder.lan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = Settings(context)
        if (settings.enabled) {
            context.startForegroundService(Intent(context, SmsService::class.java))
        }
    }
}
