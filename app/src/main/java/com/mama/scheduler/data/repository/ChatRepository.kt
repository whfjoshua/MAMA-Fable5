package com.mama.scheduler.data.repository

import com.mama.scheduler.data.local.ChatDao
import com.mama.scheduler.data.local.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessagesFlow()

    suspend fun insertMessage(message: ChatMessage): Long = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun updateMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        chatDao.updateMessage(message)
    }

    suspend fun getAllMessages(): List<ChatMessage> = withContext(Dispatchers.IO) {
        chatDao.getAllMessages()
    }

    suspend fun clearAllMessages() = withContext(Dispatchers.IO) {
        chatDao.clearAllMessages()
    }
}
