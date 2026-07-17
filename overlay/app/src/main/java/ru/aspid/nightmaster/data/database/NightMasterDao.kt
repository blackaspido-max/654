package ru.aspid.nightmaster.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM models WHERE isSelected = 1 LIMIT 1")
    fun observeSelectedModel(): Flow<ModelEntity?>

    @Query("SELECT * FROM benchmark_results ORDER BY createdAt DESC")
    fun observeBenchmarkResults(): Flow<List<BenchmarkResultEntity>>

    @Query("SELECT COUNT(*) FROM chats")
    fun observeChatCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM models")
    fun observeModelCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM benchmark_results")
    fun observeBenchmarkCount(): Flow<Int>

    @Query("SELECT * FROM models WHERE id = :modelId LIMIT 1")
    suspend fun getModel(modelId: String): ModelEntity?

    @Query("SELECT * FROM models WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedModel(): ModelEntity?

    @Query("SELECT * FROM models WHERE localPath = :localPath LIMIT 1")
    suspend fun getModelByLocalPath(localPath: String): ModelEntity?

    @Query("SELECT * FROM models ORDER BY createdAt DESC LIMIT 1")
    suspend fun getNewestModel(): ModelEntity?

    @Upsert
    suspend fun upsertChat(chat: ChatEntity)

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Upsert
    suspend fun upsertModel(model: ModelEntity)

    @Transaction
    suspend fun upsertModelPreservingSelection(model: ModelEntity) {
        val existing = getModel(model.id)
        upsertModel(model.copy(isSelected = existing?.isSelected ?: model.isSelected))
    }

    @Upsert
    suspend fun upsertBenchmarkResult(result: BenchmarkResultEntity)

    @Query("UPDATE models SET isSelected = 0")
    suspend fun clearModelSelection()

    @Query("UPDATE models SET isSelected = 1 WHERE id = :modelId")
    suspend fun markModelSelected(modelId: String)

    @Transaction
    suspend fun selectModel(modelId: String) {
        clearModelSelection()
        markModelSelected(modelId)
    }

    @Query("DELETE FROM models WHERE id = :modelId")
    suspend fun deleteModel(modelId: String)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)
}
