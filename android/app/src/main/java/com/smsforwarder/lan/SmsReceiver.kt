package com.smsforwarder.lan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {
    private val codePattern = Pattern.compile(
        "\\b(\\d{4,8})\\b|код[:\\s]*(\\d{4,8})|code[:\\s]*(\\d{4,8})|otp[:\\s]*(\\d{4,8})",
        Pattern.CASE_INSENSITIVE
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val prefs = context.getSharedPreferences("sms_diagnostics", Context.MODE_PRIVATE)
        prefs.edit().putInt("broadcast_count", prefs.getInt("broadcast_count", 0) + 1)
            .putLong("last_broadcast_at", System.currentTimeMillis())
            .putString("last_event", "Получено событие SMS_RECEIVED").apply()
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                prefs.edit().putString("last_event", "Событие получено, SMS-сегменты пусты").apply()
                return
            }
            val sender = messages[0].displayOriginatingAddress ?: "unknown"
            val body = messages.joinToString("") { it.messageBody ?: "" }
            val isCode = codePattern.matcher(body).find()
            val settings = Settings(context)
            val forward = settings.enabled && (settings.mode == 1 || isCode)
            NetworkSender.handleIncoming(context, sender, body, isCode, forward)
            prefs.edit().putString("last_event", "SMS принято; отправитель=$sender; код=$isCode; пересылка=$forward").apply()
            Log.i("SmsReceiver", "SMS received; code=$isCode; forward=$forward")
        } catch (e: Exception) {
            prefs.edit().putString("last_event", "Ошибка: ${e.javaClass.simpleName}: ${e.localizedMessage}").apply()
            Log.e("SmsReceiver", "SMS processing failed", e)
        }
    }
}
