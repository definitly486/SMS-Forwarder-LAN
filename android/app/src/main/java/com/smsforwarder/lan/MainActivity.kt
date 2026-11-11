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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        requestPermissions()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val switchEnabled = Switch(this).apply {
            text = "Пересылка включена"
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

        val saveBtn = Button(this).apply {
            text = "Сохранить"
            setOnClickListener {
                settings.host = hostEdit.text.toString().trim()
                settings.port = portEdit.text.toString().toIntOrNull() ?: 8443
                settings.token = tokenEdit.text.toString().trim()
                Toast.makeText(this@MainActivity, "Сохранено", Toast.LENGTH_SHORT).show()
            }
        }

        val testBtn = Button(this).apply {
            text = "Проверить соединение"
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "URL: ${settings.baseUrl()}",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
        layout.addView(historyBtn)

        setContentView(layout)

        // Если уже включено — поднимаем службу
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
