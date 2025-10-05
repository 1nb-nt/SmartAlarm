package com.example.alarmchatapp.chatroom

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(msg: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE timeMillis >= :cutoff ORDER BY timeMillis DESC")
    suspend fun lastSince(cutoff: Long): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE timeMillis < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)

    @Query("DELETE FROM chat_messages WHERE text = :exact")
    suspend fun deleteByExactText(exact: String)

}
