package ru.aspid.nightmaster.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NightMasterDao {
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM models ORDER BY isSelected DESC, createdAt DESC")
    fun observeModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM benchmark_results ORDER BY createdAt DESC")
    fun observeBenchmarkResults(): Flow<List<BenchmarkResultEntity>>

    @Query("SELECT COUNT(*) FROM chats")
    fun observeChatCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM models")
    fun observeModelCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM benchmark_results")
    fun observeBenchmarkCount(): Flow<Int>

    @Upsert
    suspend fun upsertChat(chat: ChatEntity)

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Upsert
    suspend fun upsertModel(model: ModelEntity)

    @Upsert
    suspend fun upsertBenchmarkResult(result: BenchmarkResultEntity)

    @Query("UPDATE models SET isSelected = CASE WHEN id = :modelId THEN 1 ELSE 0 END")
    suspend fun selectModel(modelId: String)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)
}
