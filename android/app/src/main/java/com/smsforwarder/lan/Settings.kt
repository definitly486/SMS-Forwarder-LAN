package com.smsforwarder.lan

import android.content.Context
import android.content.SharedPreferences

class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_forwarder", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    /** 0 = только коды, 1 = все SMS */
    var mode: Int
        get() = prefs.getInt("mode", 0)
        set(v) = prefs.edit().putInt("mode", v).apply()

    /** 0 = HTTPS, 1 = HTTP */
    var connectionType: Int
        get() = prefs.getInt("conn_type", 0)
        set(v) = prefs.edit().putInt("conn_type", v).apply()

    var host: String
        get() = prefs.getString("host", "192.168.8.102") ?: "192.168.8.102"
        set(v) = prefs.edit().putString("host", v).apply()

    var port: Int
        get() = prefs.getInt("port", 8443)
        set(v) = prefs.edit().putInt("port", v).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v).apply()

    fun baseUrl(): String {
        val scheme = if (connectionType == 0) "https" else "http"
        return "$scheme://$host:$port"
    }
}
