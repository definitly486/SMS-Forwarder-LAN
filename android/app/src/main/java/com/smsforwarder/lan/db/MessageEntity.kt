package com.smsforwarder.lan.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val isCode: Boolean,
    val sentToServer: Boolean = false,
    val serverResponse: String? = null
)
