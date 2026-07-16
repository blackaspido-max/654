package ru.aspid.nightmaster

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var list: RecyclerView
    private lateinit var input: EditText
    private lateinit var action: Button
    private lateinit var engine: InferenceEngine

    private val messages = mutableListOf<Message>()
    private val adapter = MessageAdapter(messages)
    private val prefs by lazy { getSharedPreferences("night_master", MODE_PRIVATE) }
    private var modelReady = false
    private var engineReady = false
    private var generation: Job? = null

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

        list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        list.adapter = adapter
        addMessage(getString(R.string.initial_message), false)
        input.isEnabled = false

        action.setOnClickListener {
            when {
                generation?.isActive == true -> Unit
                modelReady -> send()
                engineReady -> picker.launch(arrayOf("*/*"))
                else -> toast("Движок ещё запускается")
            }
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
                fail("Не удалось запустить локальный движок", e)
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
                    FileOutputStream(target).use(source::copyTo)
                } ?: error("Не удалось открыть выбранный файл")
                prefs.edit().putString("model_path", target.absolutePath).apply()
                loadModel(target)
            } catch (e: Exception) {
                fail("Не удалось загрузить модель", e)
            }
        }
    }

    private suspend fun loadModel(file: File) {
        uiState(getString(R.string.status_loading_model), false)
        engine.loadModel(file.absolutePath)
        val prompt = resources.openRawResource(R.raw.master_prompt).bufferedReader().use { it.readText() }
        engine.setSystemPrompt(prompt)
        modelReady = true
        ui {
            status.text = getString(R.string.status_ready)
            action.text = getString(R.string.send)
            action.isEnabled = true
            input.isEnabled = true
            input.requestFocus()
        }
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.text.clear()
        addMessage(text, true)
        addMessage("…", false)
        uiState(getString(R.string.status_generating), false)

        val buffer = StringBuilder()
        generation = lifecycleScope.launch(Dispatchers.Default) {
            engine.sendUserPrompt(text, predictLength = 512)
                .catch { fail("Ошибка генерации", it as? Exception ?: Exception(it)) }
                .onCompletion {
                    ui {
                        status.text = getString(R.string.status_ready)
                        action.isEnabled = true
                        input.isEnabled = true
                        input.requestFocus()
                    }
                }
                .collect { token ->
                    buffer.append(token)
                    ui {
                        val i = messages.lastIndex
                        messages[i] = messages[i].copy(content = buffer.toString())
                        adapter.notifyItemChanged(i)
                        list.scrollToPosition(i)
                    }
                }
        }
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
        }
    }

    private suspend fun fail(prefix: String, error: Exception) {
        ui {
            modelReady = false
            status.text = "$prefix: ${error.message ?: error.javaClass.simpleName}"
            action.text = getString(R.string.choose_model)
            action.isEnabled = engineReady
            input.isEnabled = false
            toast(prefix)
        }
    }

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
}
