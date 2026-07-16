package ru.aspid.nightmaster.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chatId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String = "complete",
)

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val documentUri: String?,
    val localPath: String?,
    val sizeBytes: Long?,
    val family: String?,
    val quantization: String?,
    val isSelected: Boolean = false,
    val createdAt: Long,
)

@Entity(
    tableName = "benchmark_results",
    foreignKeys = [
        ForeignKey(
            entity = ModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("modelId")],
)
data class BenchmarkResultEntity(
    @PrimaryKey val id: String,
    val modelId: String?,
    val modelName: String,
    val promptTokensPerSecond: Double?,
    val generationTokensPerSecond: Double?,
    val charactersPerSecond: Double?,
    val firstVisibleSeconds: Double?,
    val rawReport: String,
    val createdAt: Long,
)
