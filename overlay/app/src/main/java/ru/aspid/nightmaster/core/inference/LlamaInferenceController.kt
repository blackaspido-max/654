package ru.aspid.nightmaster.core.inference

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LlamaInferenceController(context: Context) : InferenceController {
    private val appContext = context.applicationContext
    private val operationMutex = Mutex()
    private val mutableStatus = MutableStateFlow<InferenceStatus>(InferenceStatus.NotInitialized)

    private var engine: InferenceEngine? = null
    private var loadedModelPath: String? = null

    override val status: StateFlow<InferenceStatus> = mutableStatus.asStateFlow()

    override suspend fun initialize() {
        operationMutex.withLock {
            if (engine != null) return
            runCatching {
                engine = AiChat.getInferenceEngine(appContext)
                mutableStatus.value = InferenceStatus.EngineReady
            }.getOrElse { error ->
                mutableStatus.value = InferenceStatus.Failed(
                    message = error.message ?: "Не удалось запустить локальный движок",
                    cause = error,
                )
                throw error
            }
        }
    }

    override suspend fun loadModel(path: String, systemPrompt: String) {
        operationMutex.withLock {
            initializeIfNeeded()
            mutableStatus.value = InferenceStatus.LoadingModel(path)
            runCatching {
                requireEngine().loadModel(path)
                requireEngine().setSystemPrompt(systemPrompt)
                loadedModelPath = path
                mutableStatus.value = InferenceStatus.ModelReady(path)
            }.getOrElse { error ->
                mutableStatus.value = InferenceStatus.Failed(
                    message = error.message ?: "Не удалось загрузить модель",
                    cause = error,
                )
                throw error
            }
        }
    }

    override fun generate(prompt: String, predictLength: Int): Flow<String> {
        val currentEngine = requireEngine()
        return currentEngine.sendUserPrompt(prompt, predictLength)
            .onStart { mutableStatus.value = InferenceStatus.Generating }
            .onCompletion { error ->
                if (error == null) {
                    mutableStatus.value = loadedModelPath
                        ?.let(InferenceStatus::ModelReady)
                        ?: InferenceStatus.EngineReady
                }
            }
            .catch { error ->
                mutableStatus.value = InferenceStatus.Failed(
                    message = error.message ?: "Ошибка генерации",
                    cause = error,
                )
                throw error
            }
    }

    override suspend fun benchmark(pp: Int, tg: Int, pl: Int, nr: Int): String =
        operationMutex.withLock {
            mutableStatus.value = InferenceStatus.Benchmarking
            try {
                requireEngine().bench(pp = pp, tg = tg, pl = pl, nr = nr)
            } catch (error: Throwable) {
                mutableStatus.value = InferenceStatus.Failed(
                    message = error.message ?: "Ошибка benchmark",
                    cause = error,
                )
                throw error
            } finally {
                if (mutableStatus.value !is InferenceStatus.Failed) {
                    mutableStatus.value = loadedModelPath
                        ?.let(InferenceStatus::ModelReady)
                        ?: InferenceStatus.EngineReady
                }
            }
        }

    override suspend fun reset(modelPath: String, systemPrompt: String) {
        operationMutex.withLock {
            initializeIfNeeded()
            mutableStatus.value = InferenceStatus.LoadingModel(modelPath)
            requireEngine().cleanUp()
            requireEngine().loadModel(modelPath)
            requireEngine().setSystemPrompt(systemPrompt)
            loadedModelPath = modelPath
            mutableStatus.value = InferenceStatus.ModelReady(modelPath)
        }
    }

    override fun close() {
        engine?.destroy()
        engine = null
        loadedModelPath = null
        mutableStatus.value = InferenceStatus.NotInitialized
    }

    private fun initializeIfNeeded() {
        if (engine == null) {
            engine = AiChat.getInferenceEngine(appContext)
            mutableStatus.value = InferenceStatus.EngineReady
        }
    }

    private fun requireEngine(): InferenceEngine =
        checkNotNull(engine) { "Inference engine is not initialized" }
}
