package ru.aspid.nightmaster

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var list: RecyclerView
    private lateinit var input: EditText
    private lateinit var action: Button
    private lateinit var benchmarkButton: Button
    private lateinit var engine: InferenceEngine

    private val messages = mutableListOf<Message>()
    private val adapter = MessageAdapter(messages)
    private val prefs by lazy { getSharedPreferences("night_master", MODE_PRIVATE) }

    private var modelReady = false
    private var engineReady = false
    private var hasUserConversation = false
    private var generation: Job? = null
    private var benchmarkJob: Job? = null
    private var currentModelFile: File? = null
    private var currentSystemPrompt: String = ""

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importModel)
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
                val path = prefs.getString("model_path", null)
                if (path != null && File(path).isFile) loadModel(File(path))
                else ui {
                    status.text = getString(R.string.status_choose_model)
                    action.isEnabled = true
                }
            } catch (e: Exception) {
                failLoad("Не удалось запустить локальный движок", e)
            }
        }
    }

    private fun importModel(uri: Uri) {
        uiState(getString(R.string.status_copying_model), false)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val name = displayName(uri)?.takeIf { it.endsWith(".gguf", true) }
                    ?: "night-master-model.gguf"
                val dir = File(filesDir, "models").apply { mkdirs() }
                val target = File(dir, name.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
                contentResolver.openInputStream(uri)?.use { source ->
                    FileOutputStream(target).use { destination ->
                        source.copyTo(destination)
                    }
                } ?: error("Не удалось открыть выбранный файл")
                prefs.edit().putString("model_path", target.absolutePath).apply()
                loadModel(target)
            } catch (e: Exception) {
                failLoad("Не удалось загрузить модель", e)
            }
        }
    }

    private suspend fun loadModel(file: File) {
        uiState(getString(R.string.status_loading_model), false)
        engine.loadModel(file.absolutePath)
        val prompt = resources.openRawResource(R.raw.master_prompt).bufferedReader().use { it.readText() }
        engine.setSystemPrompt(prompt)
        currentModelFile = file
        currentSystemPrompt = prompt
        modelReady = true
        uiReady()
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
                        val i = messages.lastIndex
                        messages[i] = messages[i].copy(content = visible)
                        adapter.notifyItemChanged(i)
                        list.scrollToPosition(i)
                    }
                }
                uiReady()
            } catch (e: Exception) {
                ui {
                    status.text = "Ошибка генерации: ${e.message ?: e.javaClass.simpleName}"
                    action.isEnabled = true
                    input.isEnabled = true
                    input.requestFocus()
                    toast("Ошибка генерации")
                }
            }
        }
    }

    private fun runSpeedTest() {
        val modelFile = currentModelFile ?: return
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

                // Скрытый прогрев в отдельном нативном контексте.
                engine.bench(pp = 32, tg = 16, pl = 1, nr = 1)

                ui { status.text = getString(R.string.status_benchmark_native) }

                // Два основных прохода; движок возвращает среднее и разброс токенов/с.
                val nativeRaw = engine.bench(pp = 128, tg = 128, pl = 1, nr = 2)
                val native = parseNativeBenchmark(nativeRaw)

                ui { status.text = getString(R.string.status_benchmark_text) }

                // Реальная скрытая генерация нужна для первого видимого текста и символов/с.
                val rawText = StringBuilder()
                val startNs = SystemClock.elapsedRealtimeNanos()
                var firstVisibleNs: Long? = null
                chatContextTouched = true

                engine.sendUserPrompt(
                    "$BENCHMARK_PROMPT\n/no_think",
                    predictLength = 128
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
                restoreCleanModel(modelFile, currentSystemPrompt)
                chatContextTouched = false

                val report = buildBenchmarkReport(
                    native = native,
                    fallbackModelName = modelFile.name,
                    firstVisibleSeconds = firstVisibleSeconds,
                    charsPerSecond = charsPerSecond,
                    charCount = charCount,
                    totalSeconds = totalSeconds
                )

                ui {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.benchmark_title))
                        .setMessage(report)
                        .setPositiveButton("Готово", null)
                        .show()
                }
            } catch (e: Exception) {
                if (chatContextTouched) {
                    try {
                        ui { status.text = getString(R.string.status_benchmark_restore) }
                        restoreCleanModel(modelFile, currentSystemPrompt)
                    } catch (_: Exception) {
                        modelReady = false
                    }
                }
                ui {
                    status.text = "Ошибка теста: ${e.message ?: e.javaClass.simpleName}"
                    toast("Тест скорости не завершён")
                }
            } finally {
                ui {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    action.isEnabled = modelReady
                    input.isEnabled = modelReady
                    benchmarkButton.isEnabled = modelReady && !hasUserConversation
                    benchmarkButton.visibility = if (modelReady && !hasUserConversation) View.VISIBLE else View.GONE
                    if (modelReady) status.text = getString(R.string.status_ready)
                    input.requestFocus()
                }
            }
        }
    }

    private suspend fun restoreCleanModel(file: File, prompt: String) {
        modelReady = false
        engine.cleanUp()
        engine.loadModel(file.absolutePath)
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
            .map { it.trim() }
            .filter { it.startsWith("|") && !it.contains("---") && !it.contains("| model |") }
            .forEach { row ->
                val columns = row.trim('|').split('|').map { it.trim() }
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
            generationTokensPerSecond = generationTokensPerSecond
        )
    }

    private fun buildBenchmarkReport(
        native: NativeBenchmark,
        fallbackModelName: String,
        firstVisibleSeconds: Double?,
        charsPerSecond: Double,
        charCount: Int,
        totalSeconds: Double
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

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }

    private fun uiState(text: String, enabled: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            status.text = text
            action.isEnabled = enabled
            input.isEnabled = enabled && modelReady
            benchmarkButton.isEnabled = enabled && modelReady && !hasUserConversation
        }
    }

    private suspend fun uiReady() = ui {
        status.text = getString(R.string.status_ready)
        action.text = getString(R.string.send)
        action.isEnabled = true
        input.isEnabled = true
        benchmarkButton.visibility = if (hasUserConversation) View.GONE else View.VISIBLE
        benchmarkButton.isEnabled = !hasUserConversation
        input.requestFocus()
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
        super.onDestroy()
    }

    private data class NativeBenchmark(
        val model: String,
        val size: String,
        val params: String,
        val backend: String,
        val promptTokensPerSecond: Double?,
        val generationTokensPerSecond: Double?
    )

    private companion object {
        const val BENCHMARK_PROMPT =
            "Напиши связный художественный абзац на русском языке. Не используй списки, заголовки и пояснения."
    }
}
