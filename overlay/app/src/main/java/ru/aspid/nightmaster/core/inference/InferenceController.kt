package ru.aspid.nightmaster.core.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface InferenceStatus {
    data object NotInitialized : InferenceStatus
    data object EngineReady : InferenceStatus
    data class LoadingModel(val path: String) : InferenceStatus
    data class ModelReady(val path: String) : InferenceStatus
    data object Generating : InferenceStatus
    data object Benchmarking : InferenceStatus
    data class Failed(val message: String, val cause: Throwable? = null) : InferenceStatus
}

interface InferenceController {
    val status: StateFlow<InferenceStatus>

    suspend fun initialize()
    suspend fun loadModel(path: String, systemPrompt: String)
    fun generate(prompt: String, predictLength: Int): Flow<String>
    suspend fun benchmark(pp: Int, tg: Int, pl: Int, nr: Int = 1): String
    suspend fun reset(modelPath: String, systemPrompt: String)
    fun close()
}
