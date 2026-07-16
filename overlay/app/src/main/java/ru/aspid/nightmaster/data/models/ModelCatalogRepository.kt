package ru.aspid.nightmaster.data.models

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.Closeable
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import ru.aspid.nightmaster.data.database.ModelEntity
import ru.aspid.nightmaster.data.database.NightMasterDao

class ModelCatalogRepository(
    context: Context,
    private val dao: NightMasterDao,
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val legacyPreferences = appContext.getSharedPreferences("night_master", Context.MODE_PRIVATE)

    val models: Flow<List<ModelEntity>> = dao.observeModels()
    val selectedModel: Flow<ModelEntity?> = dao.observeSelectedModel()

    suspend fun importDocument(uri: Uri, select: Boolean = true): ModelEntity = withContext(Dispatchers.IO) {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            "Нужно выбрать GGUF через системный проводник Android"
        }

        persistReadPermission(uri)
        verifySeekable(uri)

        val metadata = queryMetadata(uri)
        require(metadata.displayName.endsWith(".gguf", ignoreCase = true)) {
            "Выбранный файл не похож на GGUF-модель"
        }

        val model = ModelEntity(
            id = stableId("uri:${uri}"),
            displayName = metadata.displayName,
            documentUri = uri.toString(),
            localPath = null,
            sizeBytes = metadata.sizeBytes,
            family = ModelFilenameMetadata.family(metadata.displayName),
            quantization = ModelFilenameMetadata.quantization(metadata.displayName),
            isSelected = false,
            createdAt = System.currentTimeMillis(),
        )

        dao.upsertModelPreservingSelection(model)
        if (select || dao.getSelectedModel() == null) dao.selectModel(model.id)
        dao.getModel(model.id) ?: model
    }

    suspend fun selectModel(modelId: String) = withContext(Dispatchers.IO) {
        requireNotNull(dao.getModel(modelId)) { "Модель не найдена в каталоге" }
        dao.selectModel(modelId)
    }

    suspend fun removeModel(model: ModelEntity) = withContext(Dispatchers.IO) {
        dao.deleteModel(model.id)
        model.documentUri?.let { value ->
            val uri = Uri.parse(value)
            runCatching {
                resolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    suspend fun getSelectedModel(): ModelEntity? = withContext(Dispatchers.IO) {
        dao.getSelectedModel()
    }

    suspend fun migrateLegacyModels(): Int = withContext(Dispatchers.IO) {
        val modelDirectory = File(appContext.filesDir, "models")
        val preferredPath = legacyPreferences.getString("model_path", null)
        val files = buildList {
            preferredPath?.let(::File)?.takeIf(File::isFile)?.let(::add)
            modelDirectory.listFiles()
                ?.asSequence()
                ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
                ?.filterNot { candidate -> any { it.absolutePath == candidate.absolutePath } }
                ?.forEach(::add)
        }

        var imported = 0
        files.forEach { file ->
            val existing = dao.getModelByLocalPath(file.absolutePath)
            if (existing == null) {
                dao.upsertModel(
                    ModelEntity(
                        id = stableId("file:${file.absolutePath}"),
                        displayName = file.name,
                        documentUri = null,
                        localPath = file.absolutePath,
                        sizeBytes = file.length(),
                        family = ModelFilenameMetadata.family(file.name),
                        quantization = ModelFilenameMetadata.quantization(file.name),
                        isSelected = false,
                        createdAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                    ),
                )
                imported += 1
            }
        }

        if (dao.getSelectedModel() == null) {
            val preferred = preferredPath?.let { dao.getModelByLocalPath(it) }
            val fallback = preferred ?: dao.getNewestModel()
            fallback?.let { dao.selectModel(it.id) }
        }

        imported
    }

    suspend fun openModel(model: ModelEntity): OpenModelHandle = withContext(Dispatchers.IO) {
        model.localPath?.let { path ->
            val file = File(path)
            require(file.isFile) { "Файл модели больше не найден: ${model.displayName}" }
            return@withContext OpenModelHandle(model = model, path = file.absolutePath)
        }

        val uri = model.documentUri?.let(Uri::parse)
            ?: error("У модели не сохранён источник")
        val descriptor = resolver.openFileDescriptor(uri, "r")
            ?: error("Android не открыл файл модели")

        try {
            verifySeekable(descriptor)
            OpenModelHandle(
                model = model,
                path = "/proc/self/fd/${descriptor.fd}",
                descriptor = descriptor,
            )
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
    }

    private fun persistReadPermission(uri: Uri) {
        val alreadyPersisted = resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (alreadyPersisted) return

        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (error: SecurityException) {
            throw IllegalArgumentException(
                "Проводник не дал постоянный доступ к файлу. Выберите модель из локального хранилища через другой источник.",
                error,
            )
        }
    }

    private fun verifySeekable(uri: Uri) {
        val descriptor = resolver.openFileDescriptor(uri, "r")
            ?: error("Android не открыл файл модели")
        descriptor.use(::verifySeekable)
    }

    private fun verifySeekable(descriptor: ParcelFileDescriptor) {
        try {
            Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
        } catch (error: ErrnoException) {
            throw IllegalArgumentException(
                "Этот источник отдаёт файл потоком. Для GGUF нужен локальный файл с произвольным доступом.",
                error,
            )
        }
    }

    private fun queryMetadata(uri: Uri): DocumentMetadata {
        var displayName: String? = null
        var sizeBytes: Long? = null

        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
            }
        }

        return DocumentMetadata(
            displayName = displayName?.takeIf(String::isNotBlank) ?: "model.gguf",
            sizeBytes = sizeBytes?.takeIf { it >= 0L },
        )
    }

    private fun stableId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()

    private data class DocumentMetadata(
        val displayName: String,
        val sizeBytes: Long?,
    )
}

class OpenModelHandle(
    val model: ModelEntity,
    val path: String,
    private val descriptor: ParcelFileDescriptor? = null,
) : Closeable {
    override fun close() {
        descriptor?.close()
    }
}

object ModelFilenameMetadata {
    private val quantizationPattern = Regex(
        pattern = "(?i)(?:^|[-_.])(Q\\d(?:_[A-Z0-9]+){0,3})(?:[-_.]|$)",
    )

    fun quantization(filename: String): String? =
        quantizationPattern.find(filename)?.groupValues?.get(1)?.uppercase(Locale.ROOT)

    fun family(filename: String): String? {
        val normalized = filename.lowercase(Locale.ROOT)
        return when {
            "qwen" in normalized -> "Qwen"
            "llama" in normalized -> "Llama"
            "mistral" in normalized || "mixtral" in normalized -> "Mistral"
            "gemma" in normalized -> "Gemma"
            "deepseek" in normalized -> "DeepSeek"
            "phi" in normalized -> "Phi"
            else -> null
        }
    }
}
