package com.malik.lmai.data.repository

import com.malik.lmai.data.database.dao.ChatPlatformModelV2Dao
import com.malik.lmai.data.database.dao.ChatRoomV2Dao
import com.malik.lmai.data.database.dao.MessageV2Dao
import com.malik.lmai.data.database.entity.ChatPlatformModelV2
import com.malik.lmai.data.database.entity.ChatRoomV2
import com.malik.lmai.data.database.entity.MessageV2
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatRoomV2Dao: ChatRoomV2Dao,
    private val messageV2Dao: MessageV2Dao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
) : ChatRepository {

    override suspend fun fetchChatListV2(): List<ChatRoomV2> = chatRoomV2Dao.getChatRooms()

    override suspend fun searchChatsV2(query: String): List<ChatRoomV2> {
        if (query.isBlank()) {
            return chatRoomV2Dao.getChatRooms()
        }

        val titleMatches = chatRoomV2Dao.searchChatRoomsByTitle(query)
        val messageMatchChatIds = messageV2Dao.searchMessagesByContent(query)
        val allChatRooms = chatRoomV2Dao.getChatRooms()
        val messageMatches = allChatRooms.filter { it.id in messageMatchChatIds }

        return (titleMatches + messageMatches)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun fetchMessagesV2(chatId: Int): List<MessageV2> = messageV2Dao.loadMessages(chatId)

    override suspend fun fetchChatPlatformModels(chatId: Int): Map<String, String> =
        chatPlatformModelV2Dao.getByChatId(chatId).associate {
            it.platformUid to it.model
        }

    override suspend fun saveChatPlatformModels(chatId: Int, models: Map<String, String>) {
        val rows = models
            .filterKeys { it.isNotBlank() }
            .map { (platformUid, model) ->
                ChatPlatformModelV2(
                    chatId = chatId,
                    platformUid = platformUid,
                    model = model.trim(),
                )
            }

        if (rows.isNotEmpty()) {
            chatPlatformModelV2Dao.upsertAll(*rows.toTypedArray())
        }
    }

    override fun generateDefaultChatTitle(messages: List<MessageV2>): String? =
        messages.sortedBy { it.createdAt }
            .firstOrNull { it.platformType == null }
            ?.content
            ?.replace('\n', ' ')
            ?.take(50)

    override suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String) {
        chatRoomV2Dao.editChatRoom(chatRoom.copy(title = title.replace('\n', ' ').take(50)))
    }

    override suspend fun saveChat(
        chatRoom: ChatRoomV2,
        messages: List<MessageV2>,
        chatPlatformModels: Map<String, String>,
    ): ChatRoomV2 {
        if (chatRoom.id == 0) {
            val chatId = chatRoomV2Dao.addChatRoom(chatRoom).toInt()
            val updatedMessages = messages.map { it.copy(chatId = chatId) }

            if (updatedMessages.isNotEmpty()) {
                messageV2Dao.addMessages(*updatedMessages.toTypedArray())
            }
            saveChatPlatformModels(
                chatId = chatId,
                models = chatPlatformModels.filterKeys { it in chatRoom.enabledPlatform },
            )

            val savedChatRoom = chatRoom.copy(id = chatId)
            val firstContent = updatedMessages.firstOrNull()?.content
                ?.takeIf { it.isNotBlank() }

            if (firstContent != null) {
                updateChatTitle(savedChatRoom, firstContent)
                return savedChatRoom.copy(
                    title = firstContent.replace('\n', ' ').take(50),
                )
            }

            // A provider can fail/cancel before producing a persistable message. Saving an
            // empty new room must not index updatedMessages[0] and crash the application.
            return savedChatRoom
        }

        val savedMessages = fetchMessagesV2(chatRoom.id)
        val updatedMessages = messages.map { it.copy(chatId = chatRoom.id) }

        val shouldBeDeleted = savedMessages.filter { saved ->
            updatedMessages.none { it.id == saved.id }
        }
        val shouldBeUpdated = updatedMessages.filter { updated ->
            savedMessages.any { saved -> saved.id == updated.id && saved != updated }
        }
        val shouldBeAdded = updatedMessages.filter { updated ->
            savedMessages.none { it.id == updated.id }
        }

        chatRoomV2Dao.editChatRoom(chatRoom)
        if (shouldBeDeleted.isNotEmpty()) {
            messageV2Dao.deleteMessages(*shouldBeDeleted.toTypedArray())
        }
        if (shouldBeUpdated.isNotEmpty()) {
            messageV2Dao.editMessages(*shouldBeUpdated.toTypedArray())
        }
        if (shouldBeAdded.isNotEmpty()) {
            messageV2Dao.addMessages(*shouldBeAdded.toTypedArray())
        }
        saveChatPlatformModels(
            chatId = chatRoom.id,
            models = chatPlatformModels.filterKeys { it in chatRoom.enabledPlatform },
        )

        return chatRoom
    }

    override suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>) {
        if (chatRooms.isNotEmpty()) {
            chatRoomV2Dao.deleteChatRooms(*chatRooms.toTypedArray())
        }
    }

    override suspend fun deleteMessagesByChatId(chatId: Int) {
        messageV2Dao.deleteMessagesByChatId(chatId)
    }
}
