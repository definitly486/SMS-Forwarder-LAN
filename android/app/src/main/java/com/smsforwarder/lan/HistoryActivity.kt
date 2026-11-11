package com.smsforwarder.lan

import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
        title = "История SMS"

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            setPadding(24, 24, 24, 24)
            clipToPadding = false
        }
        setContentView(recycler)

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
