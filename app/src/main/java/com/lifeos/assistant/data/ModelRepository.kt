package com.lifeos.assistant.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import java.util.*

/**
 * ModelRepository - Manages AI model files for offline use
 *
 * Features:
 * - Import models from storage
 * - Track loaded models
 * - Storage usage monitoring
 * - Model metadata management
 */
class ModelRepository(private val context: Context) {

    companion object {
        private const val TAG = "ModelRepository"
        private const val MODELS_DIR = "models"
        private const val METADATA_FILE = "models.json"

        // Supported model file extensions
        private val SUPPORTED_EXTENSIONS = listOf(".bin", ".tflite", ".pt", ".onnx", ".ggml")
    }

    // Model directory
    private val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    // State flows
    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models

    private val _currentModel = MutableStateFlow<ModelInfo?>(null)
    val currentModel: StateFlow<ModelInfo?> = _currentModel

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _storageUsage = MutableStateFlow(0L)
    val storageUsage: StateFlow<Long> = _storageUsage

    init {
        // Load saved models on initialization
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            loadSavedModels()
            updateStorageUsage()
        }
    }

    /**
     * Import a model from URI
     */
    suspend fun importModel(uri: Uri): ModelInfo? = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true

            // Get file name
            val fileName = getFileNameFromUri(uri) ?: "model_${System.currentTimeMillis()}.bin"

            // Validate extension
            if (!SUPPORTED_EXTENSIONS.any { fileName.endsWith(it, ignoreCase = true) }) {
                throw IllegalArgumentException("Unsupported file format. Supported: ${SUPPORTED_EXTENSIONS.joinToString()}")
            }

            // Copy file to models directory
            val destFile = File(modelsDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Failed to open input stream")

            // Detect model type
            val modelType = detectModelType(fileName)

            // Detect language from filename
            val language = detectLanguageFromFilename(fileName)

            // Create model info
            val modelInfo = ModelInfo(
                id = UUID.randomUUID().toString(),
                name = fileName.removeSuffix(".bin").removeSuffix(".tflite")
                    .replace("ggml-", "")
                    .replace("-", " ")
                    .replace("_", " ")
                    .capitalize(),
                path = destFile.absolutePath,
                type = modelType,
                size = destFile.length(),
                language = language,
                isLoaded = false
            )

            // Save to list
            val currentModels = _models.value.toMutableList()
            currentModels.add(modelInfo)
            _models.value = currentModels

            // Save metadata
            saveModelsMetadata()

            // Update storage usage
            updateStorageUsage()

            Log.i(TAG, "Model imported: ${modelInfo.name} (${modelInfo.size} bytes)")

            modelInfo
        } catch (e: Exception) {
            Log.e(TAG, "Error importing model", e)
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Load a model
     */
    suspend fun loadModel(model: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true

            // Verify file exists
            val modelFile = File(model.path)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: ${model.path}")
                return@withContext false
            }

            // Unload current model if different
            if (_currentModel.value?.id != model.id) {
                unloadCurrentModel()
            }

            // Update model status
            val updatedModel = model.copy(isLoaded = true)
            _currentModel.value = updatedModel

            // Update in list
            updateModelInList(updatedModel)

            Log.i(TAG, "Model loaded: ${model.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            false
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Unload current model
     */
    suspend fun unloadCurrentModel() = withContext(Dispatchers.IO) {
        _currentModel.value?.let { model ->
            val updatedModel = model.copy(isLoaded = false)
            updateModelInList(updatedModel)
            _currentModel.value = null
            Log.i(TAG, "Model unloaded: ${model.name}")
        }
    }

    /**
     * Delete a model
     */
    suspend fun deleteModel(model: ModelInfo) = withContext(Dispatchers.IO) {
        try {
            // Unload if currently loaded
            if (_currentModel.value?.id == model.id) {
                unloadCurrentModel()
            }

            // Delete file
            val modelFile = File(model.path)
            if (modelFile.exists()) {
                modelFile.delete()
            }

            // Remove from list
            val currentModels = _models.value.toMutableList()
            currentModels.removeAll { it.id == model.id }
            _models.value = currentModels

            // Save metadata
            saveModelsMetadata()

            // Update storage usage
            updateStorageUsage()

            Log.i(TAG, "Model deleted: ${model.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model", e)
            throw e
        }
    }

    /**
     * Get model by ID
     */
    fun getModelById(id: String): ModelInfo? {
        return _models.value.find { it.id == id }
    }

    /**
     * Load saved models from metadata
     */
    private suspend fun loadSavedModels() = withContext(Dispatchers.IO) {
        try {
            val metadataFile = File(context.filesDir, METADATA_FILE)
            if (metadataFile.exists()) {
                val json = metadataFile.readText()
                val savedModels = parseModelsJson(json)

                // Verify files still exist
                val validModels = savedModels.filter { model ->
                    File(model.path).exists()
                }

                _models.value = validModels
                Log.i(TAG, "Loaded ${validModels.size} models from metadata")
            } else {
                // Scan models directory
                scanModelsDirectory()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved models", e)
            scanModelsDirectory()
        }
    }

    /**
     * Scan models directory for model files
     */
    private suspend fun scanModelsDirectory() = withContext(Dispatchers.IO) {
        try {
            val modelFiles = modelsDir.listFiles { file ->
                SUPPORTED_EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) }
            } ?: emptyArray()

            val scannedModels = modelFiles.map { file ->
                ModelInfo(
                    id = UUID.randomUUID().toString(),
                    name = file.nameWithoutExtension
                        .replace("ggml-", "")
                        .replace("-", " ")
                        .replace("_", " ")
                        .capitalize(),
                    path = file.absolutePath,
                    type = detectModelType(file.name),
                    size = file.length(),
                    language = detectLanguageFromFilename(file.name),
                    isLoaded = false
                )
            }

            _models.value = scannedModels
            saveModelsMetadata()

            Log.i(TAG, "Scanned ${scannedModels.size} models from directory")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning models directory", e)
        }
    }

    /**
     * Save models metadata to JSON
     */
    private suspend fun saveModelsMetadata() = withContext(Dispatchers.IO) {
        try {
            val metadataFile = File(context.filesDir, METADATA_FILE)
            val json = modelsToJson(_models.value)
            metadataFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving models metadata", e)
        }
    }

    /**
     * Update storage usage
     */
    private fun updateStorageUsage() {
        val totalSize = _models.value.sumOf { it.size }
        _storageUsage.value = totalSize
    }

    /**
     * Update model in list
     */
    private suspend fun updateModelInList(updatedModel: ModelInfo) {
        val currentModels = _models.value.toMutableList()
        val index = currentModels.indexOfFirst { it.id == updatedModel.id }
        if (index != -1) {
            currentModels[index] = updatedModel
            _models.value = currentModels
            saveModelsMetadata()
        }
    }

    /**
     * Get file name from URI
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    /**
     * Detect model type from filename
     */
    private fun detectModelType(fileName: String): ModelType {
        return when {
            fileName.contains("whisper", ignoreCase = true) -> ModelType.WHISPER
            fileName.contains("vosk", ignoreCase = true) -> ModelType.VOSK
            fileName.contains("tflite", ignoreCase = true) ||
                    fileName.endsWith(".tflite", ignoreCase = true) -> ModelType.TFLITE
            else -> ModelType.CUSTOM
        }
    }

    /**
     * Detect language from filename
     */
    private fun detectLanguageFromFilename(fileName: String): String {
        return when {
            fileName.contains("-fa-", ignoreCase = true) ||
                    fileName.contains("_fa_", ignoreCase = true) ||
                    fileName.contains("farsi", ignoreCase = true) ||
                    fileName.contains("persian", ignoreCase = true) -> "fa"

            fileName.contains("-en-", ignoreCase = true) ||
                    fileName.contains("_en_", ignoreCase = true) ||
                    fileName.contains("english", ignoreCase = true) -> "en"

            fileName.contains("tiny") ||
                    fileName.contains("base") ||
                    fileName.contains("small") -> "multilingual" // Whisper multilingual models

            else -> "auto"
        }
    }

    /**
     * Convert models list to JSON
     */
    private fun modelsToJson(models: List<ModelInfo>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        models.forEachIndexed { index, model ->
            sb.append("  {\n")
            sb.append("    \"id\": \"${model.id}\",\n")
            sb.append("    \"name\": \"${model.name}\",\n")
            sb.append("    \"path\": \"${model.path}\",\n")
            sb.append("    \"type\": \"${model.type.name}\",\n")
            sb.append("    \"size\": ${model.size},\n")
            sb.append("    \"language\": \"${model.language}\",\n")
            sb.append("    \"isLoaded\": ${model.isLoaded}\n")
            sb.append("  }")
            if (index < models.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    /**
     * Parse models from JSON
     */
    private fun parseModelsJson(json: String): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()
        try {
            // Simple JSON parsing (in production, use Gson or similar)
            val regex = """\{\s*"id"\s*:\s*"([^"]+)"\s*,\s*"name"\s*:\s*"([^"]+)"\s*,\s*"path"\s*:\s*"([^"]+)"\s*,\s*"type"\s*:\s*"([^"]+)"\s*,\s*"size"\s*:\s*(\d+)\s*,\s*"language"\s*:\s*"([^"]*)"\s*,\s*"isLoaded"\s*:\s*(true|false)\s*\}""".toRegex()

            regex.findAll(json).forEach { match ->
                models.add(ModelInfo(
                    id = match.groupValues[1],
                    name = match.groupValues[2],
                    path = match.groupValues[3],
                    type = ModelType.valueOf(match.groupValues[4]),
                    size = match.groupValues[5].toLong(),
                    language = match.groupValues[6],
                    isLoaded = match.groupValues[7].toBoolean()
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing models JSON", e)
        }
        return models
    }

    // Extension function to capitalize first letter
    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

/**
 * Model information data class
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val path: String,
    val type: ModelType,
    val size: Long,
    val language: String,
    val isLoaded: Boolean = false
)

/**
 * Model types
 */
enum class ModelType(val displayName: String) {
    WHISPER("Whisper STT"),
    VOSK("Vosk STT"),
    TFLITE("TensorFlow Lite"),
    CUSTOM("Custom Model")
}