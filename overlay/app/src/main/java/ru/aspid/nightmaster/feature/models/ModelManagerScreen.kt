package ru.aspid.nightmaster.feature.models

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.launch
import ru.aspid.nightmaster.data.database.ModelEntity
import ru.aspid.nightmaster.data.models.ModelCatalogRepository

@Composable
fun ModelManagerScreen(
    repository: ModelCatalogRepository,
    onOpenChat: () -> Unit,
) {
    val models by repository.models.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<ModelEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                notice = runCatching {
                    val model = repository.importDocument(uri, select = true)
                    "Добавлена и выбрана: ${model.displayName}"
                }.getOrElse { error ->
                    "Не удалось добавить модель: ${error.message ?: error.javaClass.simpleName}"
                }
                busy = false
            }
        }
    }

    val selectedModel = models.firstOrNull(ModelEntity::isSelected)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Локальные GGUF-модели",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Text(
                text = "Модель подключается через системный проводник Android. " +
                    "Приложение сохраняет доступ к исходному файлу и не создаёт вторую многогигабайтную копию.",
                style = MaterialTheme.typography.body2,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Добавить GGUF")
                }
                Button(
                    onClick = onOpenChat,
                    enabled = selectedModel != null && !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Открыть чат")
                }
            }
        }

        if (busy) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }
        }

        notice?.let { message ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(message, modifier = Modifier.padding(12.dp))
                }
            }
        }

        if (models.isEmpty() && !busy) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Каталог пуст", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Нажмите «Добавить GGUF» и выберите модель в памяти телефона.")
                    }
                }
            }
        } else {
            items(models, key = ModelEntity::id) { model ->
                ModelCard(
                    model = model,
                    busy = busy,
                    onSelect = {
                        scope.launch {
                            busy = true
                            notice = runCatching {
                                repository.selectModel(model.id)
                                "Выбрана модель: ${model.displayName}"
                            }.getOrElse { error ->
                                "Не удалось выбрать модель: ${error.message ?: error.javaClass.simpleName}"
                            }
                            busy = false
                        }
                    },
                    onRemove = { pendingRemoval = model },
                )
            }
        }
    }

    pendingRemoval?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Убрать модель из каталога?") },
            text = {
                Text(
                    "Исходный GGUF-файл не удалится. Приложение только забудет его и освободит сохранённое разрешение доступа.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        scope.launch {
                            busy = true
                            notice = runCatching {
                                repository.removeModel(model)
                                "Модель убрана из каталога: ${model.displayName}"
                            }.getOrElse { error ->
                                "Не удалось убрать модель: ${error.message ?: error.javaClass.simpleName}"
                            }
                            busy = false
                        }
                    },
                ) {
                    Text("Убрать")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelEntity,
    busy: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = if (model.isSelected) 10.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = model.displayName,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
                if (model.isSelected) {
                    Text("ВЫБРАНА", style = MaterialTheme.typography.caption)
                }
            }

            val details = buildList {
                model.family?.let(::add)
                model.quantization?.let(::add)
                model.sizeBytes?.let { add(formatBytes(it)) }
            }
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.body2)
            }

            Text(
                text = if (model.documentUri != null) {
                    "Источник: исходный файл через Android"
                } else {
                    "Источник: внутренняя копия старой версии"
                },
                style = MaterialTheme.typography.caption,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSelect,
                    enabled = !busy && !model.isSelected,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (model.isSelected) "Выбрана" else "Выбрать")
                }
                OutlinedButton(
                    onClick = onRemove,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Убрать")
                }
            }
        }
    }
}

private fun formatBytes(value: Long): String {
    if (value < 1024L) return "$value Б"
    val units = arrayOf("КБ", "МБ", "ГБ", "ТБ")
    var size = value.toDouble()
    var index = -1
    while (size >= 1024.0 && index < units.lastIndex) {
        size /= 1024.0
        index += 1
    }
    return String.format(Locale.getDefault(), "%.2f %s", size, units[index])
}
