package com.smsforwarder.lan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import javax.net.ssl.HttpsURLConnection
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        requestPermissions()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 48)
        }
        layout.addView(TextView(this).apply {
            text = "SMS FORWARDER LAN — УПРАВЛЕНИЕ"
            textSize = 20f
            setPadding(0, 0, 0, 20)
        })

        val switchEnabled = Switch(this).apply {
            text = "ПЕРЕСЫЛКА ВКЛЮЧЕНА"
            textSize = 18f
            setPadding(0, 12, 0, 20)
            isChecked = settings.enabled
            setOnCheckedChangeListener { _, checked ->
                settings.enabled = checked
                if (checked) {
                    startForegroundService(Intent(this@MainActivity, SmsService::class.java))
                } else {
                    stopService(Intent(this@MainActivity, SmsService::class.java))
                }
            }
        }

        val modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(RadioButton(this@MainActivity).apply {
                text = "Только SMS с кодами"
                id = 0
            })
            addView(RadioButton(this@MainActivity).apply {
                text = "Все входящие SMS"
                id = 1
            })
            check(settings.mode)
            setOnCheckedChangeListener { _, checkedId -> settings.mode = checkedId }
        }

        val connGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(RadioButton(this@MainActivity).apply {
                text = "HTTPS (шифрование)"
                id = 0
            })
            addView(RadioButton(this@MainActivity).apply {
                text = "HTTP (локальная сеть)"
                id = 1
            })
            check(settings.connectionType)
            setOnCheckedChangeListener { _, checkedId -> settings.connectionType = checkedId }
        }

        val hostEdit = EditText(this).apply {
            setText(settings.host)
            hint = "Адрес сервера"
        }
        val portEdit = EditText(this).apply {
            setText(settings.port.toString())
            hint = "Порт"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val tokenEdit = EditText(this).apply {
            setText(settings.token)
            hint = "Токен авторизации"
        }

        val saveSettings = {
            settings.host = hostEdit.text.toString().trim()
            settings.port = portEdit.text.toString().toIntOrNull() ?: 8443
            settings.token = tokenEdit.text.toString().trim()
        }

        val saveBtn = Button(this).apply {
            text = "Сохранить"
            setOnClickListener {
                saveSettings()
                Toast.makeText(this@MainActivity, "Сохранено", Toast.LENGTH_SHORT).show()
            }
        }

        val testResult = TextView(this).apply {
            text = "Проверка соединения ещё не выполнялась"
            textSize = 14f
            setPadding(0, 20, 0, 20)
        }

        lateinit var testBtn: Button
        testBtn = Button(this).apply {
            text = "Проверить соединение"
            setOnClickListener {
                saveSettings()
                val testUrl = "${settings.baseUrl()}/api/messages?limit=1"
                testBtn.isEnabled = false
                testBtn.text = "Проверка..."
                testResult.text = "⏳ Подключение к серверу...\nАдрес: $testUrl"

                Thread {
                    val startedAt = System.currentTimeMillis()
                    var connection: HttpURLConnection? = null
                    val result = try {
                        val url = URL(testUrl)
                        connection = url.openConnection() as HttpURLConnection
                        if (connection is HttpsURLConnection && settings.connectionType == 0) {
                            NetworkSender.applyCustomTrust(this@MainActivity, connection)
                        }
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer ${settings.token}")
                        connection.connectTimeout = 8000
                        connection.readTimeout = 8000
                        val code = connection.responseCode
                        val elapsed = System.currentTimeMillis() - startedAt
                        when (code) {
                            in 200..299 -> "✓ Соединение успешно\nСервер доступен, токен принят.\nHTTP: $code\nВремя ответа: ${elapsed} мс"
                            401, 403 -> "✗ Сервер доступен, но авторизация не пройдена.\nHTTP: $code\nПроверьте токен."
                            404 -> "✗ Сервер доступен, но адрес API не найден.\nHTTP: $code"
                            else -> {
                                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                                val details = stream?.bufferedReader()?.use { it.readText() }?.take(500).orEmpty()
                                "✗ Сервер ответил ошибкой.\nHTTP: $code\nОтвет: $details"
                            }
                        }
                    } catch (e: javax.net.ssl.SSLException) {
                        "✗ Ошибка SSL-сертификата: ${e.localizedMessage ?: e.javaClass.simpleName}"
                    } catch (e: java.net.SocketTimeoutException) {
                        "✗ Превышено время ожидания. Сервер не ответил за 8 секунд."
                    } catch (e: java.net.UnknownHostException) {
                        "✗ Не удалось найти сервер. Проверьте адрес."
                    } catch (e: Exception) {
                        "✗ Ошибка соединения: ${e.localizedMessage ?: e.javaClass.simpleName}"
                    } finally {
                        connection?.disconnect()
                    }
                    runOnUiThread {
                        testResult.text = result
                        testBtn.isEnabled = true
                        testBtn.text = "Проверить соединение"
                    }
                }.start()
            }
        }

        val diagnosticsText = TextView(this).apply { textSize = 14f; setPadding(0, 16, 0, 12) }
        fun refreshDiagnostics() {
            val p = getSharedPreferences("sms_diagnostics", MODE_PRIVATE)
            val count = p.getInt("broadcast_count", 0)
            val at = p.getLong("last_broadcast_at", 0L)
            val time = if (at == 0L) "никогда" else java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(at))
            val receive = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            diagnosticsText.text = "ДИАГНОСТИКА SMS\nПересылка: ${if (settings.enabled) "ВКЛ" else "ВЫКЛ"}\nРежим: ${if (settings.mode == 1) "все SMS" else "только коды"}\nRECEIVE_SMS: ${if (receive) "разрешено" else "НЕТ РАЗРЕШЕНИЯ"}\nREAD_SMS: ${if (read) "разрешено" else "НЕТ РАЗРЕШЕНИЯ"}\nSMS_RECEIVED принято: $count\nПоследнее событие: $time\nЧто произошло: ${p.getString("last_event", "пока событий нет")}\nОтправка: ${p.getString("last_send_result", "ещё не выполнялась")}"
        }
        val refreshDiagnosticsButton = Button(this).apply {
            text = "Обновить диагностику SMS"
            setOnClickListener { refreshDiagnostics() }
        }
        val requestPermissionsButton = Button(this).apply {
            text = "Запросить разрешения SMS"
            setOnClickListener { requestPermissions(); refreshDiagnostics() }
        }

        val historyBtn = Button(this).apply {
            text = "История сообщений"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, HistoryActivity::class.java))
            }
        }

        layout.addView(switchEnabled)
        layout.addView(TextView(this).apply { text = "\nРежим пересылки" })
        layout.addView(modeGroup)
        layout.addView(TextView(this).apply { text = "\nТип соединения" })
        layout.addView(connGroup)
        layout.addView(TextView(this).apply { text = "\nАдрес сервера" })
        layout.addView(hostEdit)
        layout.addView(TextView(this).apply { text = "Порт" })
        layout.addView(portEdit)
        layout.addView(TextView(this).apply { text = "Токен" })
        layout.addView(tokenEdit)
        layout.addView(saveBtn)
        layout.addView(testBtn)
        layout.addView(testResult)
        layout.addView(TextView(this).apply { text = "\nДИАГНОСТИКА ПРИЁМА SMS"; textSize = 16f })
        layout.addView(diagnosticsText)
        layout.addView(refreshDiagnosticsButton)
        layout.addView(requestPermissionsButton)
        layout.addView(historyBtn)
        refreshDiagnostics()

        setContentView(ScrollView(this).apply { addView(layout) })

        if (settings.enabled) {
            startForegroundService(Intent(this, SmsService::class.java))
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val need = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 100)
        }
    }
}
