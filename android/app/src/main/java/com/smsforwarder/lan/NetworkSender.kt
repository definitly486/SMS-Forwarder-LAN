package com.smsforwarder.lan

import android.content.Context
import android.util.Log
import com.smsforwarder.lan.db.AppDatabase
import com.smsforwarder.lan.db.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object NetworkSender {
    private const val TAG = "NetworkSender"
    private val scope = CoroutineScope(Dispatchers.IO)

    fun send(context: Context, sender: String, body: String, isCode: Boolean) {
        scope.launch {
            val dao = AppDatabase.get(context).messageDao()
            val entity = MessageEntity(
                sender = sender,
                body = body,
                receivedAt = System.currentTimeMillis(),
                isCode = isCode,
                sentToServer = false
            )
            val id = dao.insert(entity)

            val settings = Settings(context)
            if (!settings.enabled) return@launch

            val ok = doSend(context, settings, sender, body, isCode)
            dao.markSent(id, if (ok) "ok" else "fail")
        }
    }

    fun flushQueue(context: Context) {
        scope.launch {
            val dao = AppDatabase.get(context).messageDao()
            val pending = dao.getPending()
            val settings = Settings(context)
            if (!settings.enabled) return@launch

            for (msg in pending) {
                val ok = doSend(context, settings, msg.sender, msg.body, msg.isCode)
                if (ok) dao.markSent(msg.id, "ok")
                else break
            }
        }
    }

    private fun doSend(
        context: Context,
        settings: Settings,
        sender: String,
        body: String,
        isCode: Boolean
    ): Boolean {
        return try {
            val payload = JSONObject().apply {
                put("sender", sender)
                put("body", body)
                put("is_code", isCode)
            }.toString()

            val url = URL("${settings.baseUrl()}/api/sms")
            val conn = url.openConnection() as HttpURLConnection

            if (conn is HttpsURLConnection && settings.connectionType == 0) {
                applyCustomTrust(context, conn)
            }

            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer ${settings.token}")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(payload) }

            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "send failed", e)
            false
        }
    }

    /**
     * Загружает cert.pem из assets и создаёт TrustManager,
     * который доверяет этому сертификату + системным CA.
     * Если файла нет — используется системное доверие.
     */
    private fun applyCustomTrust(context: Context, conn: HttpsURLConnection) {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            context.assets.open("cert.pem").use { certInput ->
                val ca = cf.generateCertificate(certInput) as X509Certificate

                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setCertificateEntry("ca", ca)
                }

                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(keyStore)

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, tmf.trustManagers, null)
                conn.sslSocketFactory = sslContext.socketFactory
            }
        } catch (e: Exception) {
            Log.w(TAG, "cert.pem not found in assets, using system trust", e)
        }
    }
}
