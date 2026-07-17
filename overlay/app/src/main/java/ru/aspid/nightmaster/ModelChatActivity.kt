package ru.aspid.nightmaster

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.aspid.nightmaster.data.database.ModelEntity
import ru.aspid.nightmaster.data.models.ModelCatalogRepository
import ru.aspid.nightmaster.data.models.OpenModelHandle

class ModelChatActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var list: RecyclerView
    private lateinit var input: EditText
    private lateinit var action: Button
    private lateinit var benchmarkButton: Button
    private lateinit var engine: InferenceEngine

    private val messages = mutableListOf<Message>()
    private val adapter = MessageAdapter(messages)
    private val modelCatalog: ModelCatalogRepository by lazy {
        (application as NightMasterApplication).modelCatalogRepository
    }

    private var modelReady = false
    private var engineReady = false
    private var hasUserConversation = false
    private var generation: Job? = null
    private var benchmarkJob: Job? = null
    private var currentModel: ModelEntity? = null
    private var currentModelHandle: OpenModelHandle? = null
    private var currentSystemPrompt: String = ""

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importAndLoadModel)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        list = findViewById(R.id.messages)
        input = findViewById(R.id.user_input)
        action = findViewById(R.id.action_button)
        benchmarkButton = findViewById(R.id.benchmark_button)

        list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        list.adapter = adapter
        addMessage(getString(R.string.initial_message), false)
        input.isEnabled = false
        benchmarkButton.visibility = View.GONE

        action.setOnClickListener {
            when {
                generation?.isActive == true || benchmarkJob?.isActive == true -> Unit
                modelReady -> send()
                engineReady -> picker.launch(arrayOf("*/*"))
                else -> toast("Движок ещё запускается")
            }
        }

        benchmarkButton.setOnClickListener {
            if (!hasUserConversation) runSpeedTest()
        }

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine = AiChat.getInferenceEngine(applicationContext)
                engineReady = true
                modelCatalog.migrateLegacyModels()
                val selected = modelCatalog.getSelectedModel()
                if (selected != null) loadModel(selected)
                else ui {
                    status.text = getString(R.string.status_choose_model)
                    action.isEnabled = true
                }
            } catch (error: Exception) {
                failLoad("Не удалось запустить локальный движок", error)
            }
        }
    }

    private fun importAndLoadModel(uri: Uri) {
        uiState("Подключаем GGUF-модель…", false)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val model = modelCatalog.importDocument(uri, select = true)
                loadModel(model)
            } catch (error: Exception) {
                failLoad("Не удалось подключить модель", error)
            }
        }
    }

    private suspend fun loadModel(model: ModelEntity) {
        uiState(getString(R.string.status_loading_model), false)

        if (currentModelHandle != null) {
            modelReady = false
            runCatching { engine.cleanUp() }
            currentModelHandle?.close()
            currentModelHandle = null
        }

        val prompt = resources.openRawResource(R.raw.master_prompt)
            .bufferedReader()
            .use { it.readText() }

        var resolvedModel = model
        var nextHandle = modelCatalog.openModel(resolvedModel)

        try {
            try {
                engine.loadModel(nextHandle.path)
            } catch (directError: Throwable) {
                val canCreateFallback = model.documentUri != null && model.localPath == null
                if (!canCreateFallback) throw directError

                nextHandle.close()
                runCatching { engine.cleanUp() }
                ui {
                    status.text = "Прямой доступ не поддержан. Создаю локальную копию…"
                }

                resolvedModel = modelCatalog.createLocalFallback(model) { copiedBytes, totalBytes ->
                    ui {
                        status.text = localCopyStatus(copiedBytes, totalBytes)
                    }
                }
                nextHandle = modelCatalog.openModel(resolvedModel)
                engine.loadModel(nextHandle.path)
            }

            engine.setSystemPrompt(prompt)
            currentModel = resolvedModel
            currentModelHandle = nextHandle
            currentSystemPrompt = prompt
            modelReady = true
            uiReady(resolvedModel.displayName)
        } catch (error: Throwable) {
            nextHandle.close()
            throw error
        }
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        hasUserConversation = true
        benchmarkButton.visibility = View.GONE
        input.text.clear()
        addMessage(text, true)
        addMessage("…", false)
        uiState(getString(R.string.status_generating), false)

        val rawBuffer = StringBuilder()
        generation = lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine.sendUserPrompt("$text\n/no_think", predictLength = 512).collect { token ->
                    rawBuffer.append(token)
                    val visible = stripReasoning(rawBuffer.toString()).ifBlank { "…" }
                    ui {
                        val index = messages.lastIndex
                        messages[index] = messages[index].copy(content = visible)
                        adapter.notifyItemChanged(index)
                        list.scrollToPosition(index)
                    }
                }
                uiReady(currentModel?.displayName)
            } catch (error: Exception) {
                ui {
                    status.text = "Ошибка генерации: ${error.message ?: error.javaClass.simpleName}"
                    action.isEnabled = true
                    input.isEnabled = true
                    input.requestFocus()
                    toast("Ошибка генерации")
                }
            }
        }
    }

    private fun runSpeedTest() {
        val model = currentModel ?: return
        val handle = currentModelHandle ?: return
        if (!modelReady || hasUserConversation || benchmarkJob?.isActive == true) return

        benchmarkJob = lifecycleScope.launch(Dispatchers.Default) {
            var chatContextTouched = false
            try {
                ui {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    status.text = getString(R.string.status_benchmark_warmup)
                    action.isEnabled = false
                    input.isEnabled = false
                    benchmarkButton.isEnabled = false
                }

                engine.bench(pp = 32, tg = 16, pl = 1, nr = 1)
                ui { status.text = getString(R.string.status_benchmark_native) }

                val nativeRaw = engine.bench(pp = 128, tg = 128, pl = 1, nr = 2)
                val native = parseNativeBenchmark(nativeRaw)
                ui { status.text = getString(R.string.status_benchmark_text) }

                val rawText = StringBuilder()
                val startNs = SystemClock.elapsedRealtimeNanos()
                var firstVisibleNs: Long? = null
                chatContextTouched = true

                engine.sendUserPrompt(
                    "$BENCHMARK_PROMPT\n/no_think",
                    predictLength = 128,
                ).collect { token ->
                    rawText.append(token)
                    val visible = stripReasoning(rawText.toString())
                    if (firstVisibleNs == null && visible.isNotBlank()) {
                        firstVisibleNs = SystemClock.elapsedRealtimeNanos()
                    }
                }

                val endNs = SystemClock.elapsedRealtimeNanos()
                val visibleText = stripReasoning(rawText.toString())
                val charCount = visibleText.codePointCount(0, visibleText.length)
                val totalSeconds = nanosToSeconds(endNs - startNs)
                val firstVisibleSeconds = firstVisibleNs?.let { nanosToSeconds(it - startNs) }
                val generationStartNs = firstVisibleNs ?: startNs
                val visibleGenerationSeconds = nanosToSeconds(endNs - generationStartNs).coerceAtLeast(0.001)
                val charsPerSecond = charCount / visibleGenerationSeconds

                ui { status.text = getString(R.string.status_benchmark_restore) }
                restoreCleanModel(handle.path, currentSystemPrompt)
                chatContextTouched = false

                val report = buildBenchmarkReport(
                    native = native,
                    fallbackModelName = model.displayName,
                    firstVisibleSeconds = firstVisibleSeconds,
                    charsPerSecond = charsPerSecond,
                    charCount = charCount,
                    totalSeconds = totalSeconds,
                )

                ui {
                    AlertDialog.Builder(this@ModelChatActivity)
                        .setTitle(getString(R.string.benchmark_title))
                        .setMessage(report)
                        .setPositiveButton("Готово", null)
                        .show()
                }
            } catch (error: Exception) {
                if (chatContextTouched) {
                    try {
                        ui { status.text = getString(R.string.status_benchmark_restore) }
                        restoreCleanModel(handle.path, currentSystemPrompt)
                    } catch (_: Exception) {
                        modelReady = false
                    }
                }
                ui {
                    status.text = "Ошибка теста: ${error.message ?: error.javaClass.simpleName}"
                    toast("Тест скорости не завершён")
                }
            } finally {
                ui {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    action.isEnabled = modelReady
                    input.isEnabled = modelReady
                    benchmarkButton.isEnabled = modelReady && !hasUserConversation
                    benchmarkButton.visibility = if (modelReady && !hasUserConversation) View.VISIBLE else View.GONE
                    if (modelReady) status.text = readyStatus(currentModel?.displayName)
                    input.requestFocus()
                }
            }
        }
    }

    private suspend fun restoreCleanModel(path: String, prompt: String) {
        modelReady = false
        engine.cleanUp()
        engine.loadModel(path)
        engine.setSystemPrompt(prompt)
        modelReady = true
    }

    private fun parseNativeBenchmark(raw: String): NativeBenchmark {
        var model = ""
        var size = ""
        var params = ""
        var backend = ""
        var promptTokensPerSecond: Double? = null
        var generationTokensPerSecond: Double? = null

        raw.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("|") && !it.contains("---") && !it.contains("| model |") }
            .forEach { row ->
                val columns = row.trim('|').split('|').map(String::trim)
                if (columns.size < 6) return@forEach
                model = columns[0]
                size = columns[1]
                params = columns[2]
                backend = columns[3]
                val speed = columns[5]
                    .substringBefore('±')
                    .trim()
                    .replace(',', '.')
                    .toDoubleOrNull()
                when {
                    columns[4].startsWith("pp") -> promptTokensPerSecond = speed
                    columns[4].startsWith("tg") -> generationTokensPerSecond = speed
                }
            }

        return NativeBenchmark(
            model = model,
            size = size,
            params = params,
            backend = backend,
            promptTokensPerSecond = promptTokensPerSecond,
            generationTokensPerSecond = generationTokensPerSecond,
        )
    }

    private fun buildBenchmarkReport(
        native: NativeBenchmark,
        fallbackModelName: String,
        firstVisibleSeconds: Double?,
        charsPerSecond: Double,
        charCount: Int,
        totalSeconds: Double,
    ): String {
        val tokenSpeed = native.generationTokensPerSecond
        val estimated128Seconds = tokenSpeed?.takeIf { it > 0.0 }?.let { 128.0 / it }
        val modelName = native.model.ifBlank { fallbackModelName }

        return buildString {
            appendLine("Модель: $modelName")
            if (native.size.isNotBlank() || native.params.isNotBlank()) {
                appendLine("Размер: ${native.size.ifBlank { "—" }} · параметры: ${native.params.ifBlank { "—" }}")
            }
            if (native.backend.isNotBlank()) appendLine("Движок: ${native.backend}")
            appendLine()
            appendLine("Прогрев: выполнен автоматически")
            appendLine("Первый видимый текст: ${formatSeconds(firstVisibleSeconds)}")
            appendLine("Генерация: ${formatRate(tokenSpeed, "токена/с")}")
            appendLine("Скорость текста: ${formatRate(charsPerSecond, "символа/с")}")
            appendLine("Обработка запроса: ${formatRate(native.promptTokensPerSecond, "токена/с")}")
            appendLine("Реальный текст: $charCount символов за ${formatNumber(totalSeconds)} с")
            appendLine("Расчётные 128 токенов: ${formatSeconds(estimated128Seconds)}")
            appendLine()
            append("Основной нативный замер: 2 прохода по 128 токенов. После теста контекст очищен.")
        }
    }

    private fun stripReasoning(raw: String): String {
        val trimmed = raw.trimStart()
        val endTag = "</think>"
        val visible = when {
            trimmed.startsWith("<think>") && trimmed.contains(endTag) ->
                trimmed.substringAfterLast(endTag)
            trimmed.startsWith("<think>") -> ""
            else -> raw.replace(Regex("(?s)<think>.*?</think>"), "")
        }
        return visible.trimStart()
    }

    private fun addMessage(text: String, user: Boolean) {
        messages += Message(UUID.randomUUID().toString(), text, user)
        adapter.notifyItemInserted(messages.lastIndex)
        list.scrollToPosition(messages.lastIndex)
    }

    private fun uiState(text: String, enabled: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            status.text = text
            action.isEnabled = enabled
            input.isEnabled = enabled && modelReady
            benchmarkButton.isEnabled = enabled && modelReady && !hasUserConversation
        }
    }

    private suspend fun uiReady(modelName: String?) = ui {
        status.text = readyStatus(modelName)
        action.text = getString(R.string.send)
        action.isEnabled = true
        input.isEnabled = true
        benchmarkButton.visibility = if (hasUserConversation) View.GONE else View.VISIBLE
        benchmarkButton.isEnabled = !hasUserConversation
        input.requestFocus()
    }

    private fun readyStatus(modelName: String?): String =
        modelName?.let { "Готово · $it" } ?: getString(R.string.status_ready)

    private fun localCopyStatus(copiedBytes: Long, totalBytes: Long?): String {
        val copiedGiB = copiedBytes / GIBIBYTE
        val totalGiB = totalBytes?.takeIf { it > 0L }?.div(GIBIBYTE)
        return if (totalGiB != null) {
            val percent = ((copiedBytes * 100L) / totalBytes).coerceIn(0L, 100L)
            "Прямой доступ не поддержан. Копирую: $percent% · ${formatNumber(copiedGiB)} / ${formatNumber(totalGiB)} ГБ"
        } else {
            "Прямой доступ не поддержан. Копирую: ${formatNumber(copiedGiB)} ГБ"
        }
    }

    private suspend fun failLoad(prefix: String, error: Exception) {
        ui {
            modelReady = false
            status.text = "$prefix: ${error.message ?: error.javaClass.simpleName}"
            action.text = getString(R.string.choose_model)
            action.isEnabled = engineReady
            input.isEnabled = false
            benchmarkButton.visibility = View.GONE
            toast(prefix)
        }
    }

    private fun nanosToSeconds(nanos: Long): Double = nanos / 1_000_000_000.0

    private fun formatNumber(value: Double?): String =
        value?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "—"

    private fun formatSeconds(value: Double?): String =
        value?.let { "${formatNumber(it)} с" } ?: "—"

    private fun formatRate(value: Double?, unit: String): String =
        value?.let { "${formatNumber(it)} $unit" } ?: "—"

    private suspend fun ui(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    override fun onStop() {
        generation?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        if (engineReady) engine.destroy()
        currentModelHandle?.close()
        currentModelHandle = null
        super.onDestroy()
    }

    private data class NativeBenchmark(
        val model: String,
        val size: String,
        val params: String,
        val backend: String,
        val promptTokensPerSecond: Double?,
        val generationTokensPerSecond: Double?,
    )

    private companion object {
        const val BENCHMARK_PROMPT =
            "Напиши связный художественный абзац на русском языке. Не используй списки, заголовки и пояснения."
        const val GIBIBYTE = 1024.0 * 1024.0 * 1024.0
    }
}
