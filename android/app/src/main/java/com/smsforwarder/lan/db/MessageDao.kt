package com.smsforwarder.lan.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(msg: MessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC")
    fun getAllFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<MessageEntity>

    @Query("UPDATE messages SET sentToServer = 1, serverResponse = :resp WHERE id = :id")
    suspend fun markSent(id: Long, resp: String?)

    @Query("DELETE FROM messages WHERE receivedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT * FROM messages WHERE sentToServer = 0 ORDER BY receivedAt ASC")
    suspend fun getPending(): List<MessageEntity>
}
