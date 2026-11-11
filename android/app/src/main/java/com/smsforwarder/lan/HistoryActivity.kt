package com.smsforwarder.lan

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smsforwarder.lan.db.AppDatabase
import com.smsforwarder.lan.db.MessageEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var adapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val header = TextView(this).apply {
            text = "История SMS"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF202124.toInt())
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(0xFFF5F5F5.toInt())
        }

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            setPadding(dp(16), dp(8), dp(16), dp(16))
            clipToPadding = false
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            addView(header, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(recycler, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }

        // Apply system-bar insets to the whole screen exactly once.
        // The title and message list are laid out below the status bar.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }

        setContentView(root)
        ViewCompat.requestApplyInsets(root)

        adapter = MessageAdapter()
        recycler.adapter = adapter

        val dao = AppDatabase.get(this).messageDao()
        lifecycleScope.launch {
            dao.getAllFlow().collectLatest { list ->
                adapter.submit(list)
            }
        }
    }

    private class MessageAdapter : RecyclerView.Adapter<MessageAdapter.VH>() {
        private var items = listOf<MessageEntity>()
        private val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

        fun submit(list: List<MessageEntity>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                setPadding(16, 20, 16, 20)
                textSize = 14f
                setTextIsSelectable(true)
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            val status = when (m.serverResponse) {
                "filtered_by_mode" -> "↳ сохранено локально (фильтр кодов)"
                "not_forwarding_disabled" -> "↳ сохранено локально (пересылка выключена)"
                "fail" -> "⚠ ошибка отправки, будет повтор"
                else -> if (m.sentToServer) "✓ отправлено" else "⏳ в очереди"
            }
            val codeMark = if (m.isCode) " [КОД]" else ""
            holder.text.text = buildString {
                append(sdf.format(Date(m.receivedAt)))
                append("  $status$codeMark\n")
                append("От: ${m.sender}\n")
                append(m.body)
            }
            holder.text.setBackgroundColor(
                if (m.isCode) 0xFFFFF3CD.toInt() else 0xFFFFFFFF.toInt()
            )
        }

        override fun getItemCount() = items.size

        class VH(val text: TextView) : RecyclerView.ViewHolder(text)
    }
}
