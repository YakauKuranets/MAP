package com.mapv12.dutytracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * DAO для доступа к таблице chat_messages.
 */
@Dao
interface ChatMessageDao {
    /**
     * Получить все сообщения для канала в порядке возрастания времени.
     */
    @Query("SELECT * FROM chat_messages WHERE channelId = :channelId ORDER BY createdAt ASC")
    suspend fun getMessagesForChannel(channelId: String): List<ChatMessageEntity>

    /**
     * Вставить новое сообщение в базу данных. Возвращает id строки.
     */
    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    /**
     * Обновить сообщение (например, когда сервер присвоит messageId или статус поменяется).
     */
    @Update
    suspend fun update(message: ChatMessageEntity)

    /**
     * Удалить сообщение (использовать для очистки кеша/retention).
     */
    @Delete
    suspend fun delete(message: ChatMessageEntity)

    /**
     * Получить сообщения по статусу (например, queued) для отправки.
     */
    @Query("SELECT * FROM chat_messages WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getMessagesByStatus(status: String): List<ChatMessageEntity>
}