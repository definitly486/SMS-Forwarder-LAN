package com.smsforwarder.lan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SmsService : Service() {

    override fun onCreate() {
        super.onCreate()
        val channelId = "sms_forwarder"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "SMS Forwarder", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SMS Forwarder LAN")
            .setContentText("Служба работает")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(1, notif)

        // Периодически пытаемся отправить очередь
        Thread {
            while (true) {
                try {
                    NetworkSender.flushQueue(this)
                    Thread.sleep(30_000)
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
