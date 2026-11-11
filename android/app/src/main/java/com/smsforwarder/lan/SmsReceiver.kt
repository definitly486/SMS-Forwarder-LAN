package com.smsforwarder.lan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    private val codePattern = Pattern.compile(
        "\\b(\\d{4,8})\\b|код[:\\s]*(\\d{4,8})|code[:\\s]*(\\d{4,8})|otp[:\\s]*(\\d{4,8})",
        Pattern.CASE_INSENSITIVE
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settings = Settings(context)
        if (!settings.enabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: "unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }

        val isCode = codePattern.matcher(body).find()

        when (settings.mode) {
            0 -> if (isCode) NetworkSender.send(context, sender, body, true)
            1 -> NetworkSender.send(context, sender, body, isCode)
        }
    }
}
