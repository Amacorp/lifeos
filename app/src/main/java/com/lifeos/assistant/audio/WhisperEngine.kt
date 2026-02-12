package com.lifeos.assistant.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class WhisperEngine(private val context: Context) {

    companion object {
        private const val TAG = "VoskEngine"
        private const val SAMPLE_RATE = 16000.0f
        private const val MODEL_DIR_NAME = "vosk-model"
    }

    private var model: Model? = null
    private var isInitialized = false

    /**
     * Try to initialize from previously imported model
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = File(context.filesDir, MODEL_DIR_NAME)
            if (modelDir.exists() && modelDir.isDirectory && isValidModelDir(modelDir)) {
                Log.d(TAG, "Loading Vosk model from: ${modelDir.absolutePath}")
                model = Model(modelDir.absolutePath)
                isInitialized = true
                Log.d(TAG, "Vosk model loaded successfully")
                return@withContext true
            } else {
                Log.d(TAG, "No model found at: ${modelDir.absolutePath}")
                isInitialized = false
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Vosk model: ${e.message}", e)
            isInitialized = false
            return@withContext false
        }
    }

    /**
     * Import model from a URI (zip file picked by user)
     */
    suspend fun importModelFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Importing model from URI: $uri")

            // Clean up old model
            val modelDir = File(context.filesDir, MODEL_DIR_NAME)
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }
            modelDir.mkdirs()

            // Extract zip
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry = zipInputStream.nextEntry
                    // Track the root folder name inside zip
                    var rootFolder: String? = null

                    while (entry != null) {
                        val entryName = entry.name

                        // Detect root folder (e.g., "vosk-model-small-en-us-0.15/")
                        if (rootFolder == null && entryName.contains("/")) {
                            rootFolder = entryName.substringBefore("/")
                        }

                        // Strip the root folder prefix so files go directly into modelDir
                        val relativePath = if (rootFolder != null && entryName.startsWith("$rootFolder/")) {
                            entryName.removePrefix("$rootFolder/")
                        } else {
                            entryName
                        }

                        if (relativePath.isEmpty()) {
                            entry = zipInputStream.nextEntry
                            continue
                        }

                        val outputFile = File(modelDir, relativePath)

                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { fos ->
                                zipInputStream.copyTo(fos)
                            }
                        }

                        Log.d(TAG, "Extracted: $relativePath")
                        entry = zipInputStream.nextEntry
                    }
                }
            } ?: run {
                Log.e(TAG, "Failed to open input stream from URI")
                return@withContext false
            }

            // Verify the extracted model
            if (!isValidModelDir(modelDir)) {
                Log.e(TAG, "Extracted model is not valid. Contents: ${modelDir.listFiles()?.map { it.name }}")
                modelDir.deleteRecursively()
                return@withContext false
            }

            // Load the model
            Log.d(TAG, "Loading extracted model...")
            model?.close()
            model = Model(modelDir.absolutePath)
            isInitialized = true
            Log.d(TAG, "Model imported and loaded successfully!")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error importing model: ${e.message}", e)
            isInitialized = false
            return@withContext false
        }
    }

    /**
     * Check if model directory has required Vosk files
     */
    private fun isValidModelDir(dir: File): Boolean {
        val files = dir.listFiles()?.map { it.name } ?: emptyList()
        Log.d(TAG, "Model directory contents: $files")

        // Vosk models typically contain these files/folders
        // At minimum we need some model files
        val hasConf = files.contains("conf") || files.any { it.endsWith(".conf") }
        val hasAmOrGraph = files.contains("am") ||
                files.contains("graph") ||
                files.contains("ivector") ||
                files.any { it.endsWith(".fst") || it.endsWith(".mdl") }

        // Some small models just have a few files, so be lenient
        return files.isNotEmpty() && (hasConf || hasAmOrGraph || files.size >= 3)
    }

    /**
     * Check if a model is currently loaded
     */
    fun hasModel(): Boolean {
        val modelDir = File(context.filesDir, MODEL_DIR_NAME)
        return modelDir.exists() && modelDir.isDirectory && isValidModelDir(modelDir)
    }

    /**
     * Get model directory info
     */
    fun getModelInfo(): String {
        val modelDir = File(context.filesDir, MODEL_DIR_NAME)
        if (!modelDir.exists()) return "No model installed"

        val files = modelDir.listFiles() ?: return "No model installed"
        val totalSize = files.sumOf { if (it.isFile) it.length() else 0L }
        val sizeMB = totalSize / (1024.0 * 1024.0)
        return "Model loaded (%.1f MB)".format(sizeMB)
    }

    /**
     * Delete the current model
     */
    fun deleteModel() {
        try {
            model?.close()
            model = null
            isInitialized = false
            val modelDir = File(context.filesDir, MODEL_DIR_NAME)
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }
            Log.d(TAG, "Model deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model: ${e.message}", e)
        }
    }

    fun transcribe(audioData: ShortArray): String {
        if (!isInitialized || model == null) {
            Log.e(TAG, "Vosk model not initialized")
            return ""
        }

        return try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            recognizer.acceptWaveForm(audioData, audioData.size)
            val result = recognizer.result
            recognizer.close()

            val jsonResult = JSONObject(result)
            val text = jsonResult.optString("text", "")
            Log.d(TAG, "Transcription result: $text")
            text
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription: ${e.message}", e)
            ""
        }
    }

    fun transcribeBytes(audioData: ByteArray): String {
        if (!isInitialized || model == null) {
            Log.e(TAG, "Vosk model not initialized")
            return ""
        }

        return try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            recognizer.acceptWaveForm(audioData, audioData.size)
            val result = recognizer.result
            recognizer.close()

            val jsonResult = JSONObject(result)
            val text = jsonResult.optString("text", "")
            Log.d(TAG, "Transcription result: $text")
            text
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription: ${e.message}", e)
            ""
        }
    }

    /**
     * Create a new recognizer for streaming recognition
     */
    fun createRecognizer(): Recognizer? {
        if (!isInitialized || model == null) {
            Log.e(TAG, "Cannot create recognizer - model not initialized")
            return null
        }
        return try {
            Recognizer(model, SAMPLE_RATE)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating recognizer: ${e.message}", e)
            null
        }
    }

    fun isReady(): Boolean = isInitialized

    fun close() {
        try {
            model?.close()
            model = null
            isInitialized = false
            Log.d(TAG, "Vosk model closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Vosk model: ${e.message}", e)
        }
    }
}