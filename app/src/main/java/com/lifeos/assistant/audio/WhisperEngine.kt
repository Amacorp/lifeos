package com.lifeos.assistant.audio

import android.content.Context
import android.util.Log
import com.lifeos.assistant.data.ModelInfo
import com.lifeos.assistant.data.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * WhisperEngine - JNI wrapper for Whisper.cpp speech recognition
 *
 * Features:
 * - Load and run Whisper models
 * - Transcribe audio files
 * - Support for multiple languages
 * - Memory-efficient processing
 */
class WhisperEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperEngine"

        // Load native library
        init {
            try {
                System.loadLibrary("whisper")
                Log.i(TAG, "Whisper native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load Whisper native library", e)
            }
        }
    }

    // Native methods
    private external fun whisperInit(modelPath: String): Long
    private external fun whisperFree(contextPtr: Long)
    private external fun whisperTranscribe(
        contextPtr: Long,
        samples: FloatArray,
        numSamples: Int,
        language: String,
        translate: Boolean
    ): String
    private external fun whisperGetSystemInfo(): String

    // Current model context
    private var contextPtr: Long = 0
    private var currentModel: ModelInfo? = null
    private val lock = ReentrantLock()

    /**
     * Load a Whisper model
     */
    suspend fun loadModel(model: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                // Free previous model if any
                if (contextPtr != 0L) {
                    whisperFree(contextPtr)
                    contextPtr = 0
                }

                // Validate model file
                val modelFile = File(model.path)
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file not found: ${model.path}")
                    return@withLock false
                }

                // Initialize new model
                Log.i(TAG, "Loading Whisper model: ${model.name}")
                contextPtr = whisperInit(model.path)

                if (contextPtr == 0L) {
                    Log.e(TAG, "Failed to initialize Whisper model")
                    return@withLock false
                }

                currentModel = model
                Log.i(TAG, "Whisper model loaded successfully")

                // Log system info
                Log.d(TAG, "Whisper system info: ${whisperGetSystemInfo()}")

                true
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Whisper model", e)
                contextPtr = 0
                false
            }
        }
    }

    /**
     * Unload current model
     */
    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                if (contextPtr != 0L) {
                    whisperFree(contextPtr)
                    contextPtr = 0
                    currentModel = null
                    Log.i(TAG, "Whisper model loaded")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading model", e)
            }
        }
    }

    /**
     * Transcribe audio file
     */
    suspend fun transcribe(audioFile: File?, model: ModelInfo? = null): String =
        withContext(Dispatchers.IO) {
            // Check and load model outside the lock to avoid suspension point in critical section
            val needsModelLoad = lock.withLock {
                contextPtr == 0L || model?.id != currentModel?.id
            }

            if (needsModelLoad) {
                val modelToLoad = model
                if (modelToLoad != null) {
                    if (!loadModel(modelToLoad)) {
                        return@withContext "Error: Failed to load model"
                    }
                } else {
                    return@withContext "Error: No model loaded"
                }
            }

            lock.withLock {
                try {
                    // Validate audio file
                    if (audioFile == null || !audioFile.exists()) {
                        return@withLock "Error: Audio file not found"
                    }

                    Log.d(TAG, "Transcribing: ${audioFile.absolutePath}")

                    // Read and convert audio file
                    val samples = readWavFile(audioFile)
                    if (samples.isEmpty()) {
                        return@withLock "Error: Failed to read audio file"
                    }

                    // Determine language
                    val language = when (model?.language) {
                        "fa", "fas", "per" -> "fa"
                        "en", "eng" -> "en"
                        else -> "auto"
                    }

                    // Transcribe
                    val result = whisperTranscribe(
                        contextPtr,
                        samples,
                        samples.size,
                        language,
                        false  // Don't translate, keep original language
                    )

                    Log.d(TAG, "Transcription result: $result")

                    // Clean up audio file
                    audioFile.delete()

                    result.trim()
                } catch (e: Exception) {
                    Log.e(TAG, "Error transcribing audio", e)
                    "Error: ${e.message}"
                }
            }
        }

    /**
     * Check if a model is loaded
     */
    fun isModelLoaded(): Boolean = contextPtr != 0L

    /**
     * Get current model info
     */
    fun getCurrentModel(): ModelInfo? = currentModel

    /**
     * Release resources
     */
    suspend fun release() {
        unloadModel()
    }

    /**
     * Read WAV file and convert to float samples
     */
    private fun readWavFile(file: File): FloatArray {
        try {
            val bytes = file.readBytes()

            // Parse WAV header
            if (bytes.size < 44) {
                Log.e(TAG, "WAV file too small")
                return floatArrayOf()
            }

            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)

            // Verify RIFF header
            val riff = String(bytes, 0, 4)
            if (riff != "RIFF") {
                Log.e(TAG, "Invalid RIFF header: $riff")
                return floatArrayOf()
            }

            // Verify WAVE format
            val wave = String(bytes, 8, 4)
            if (wave != "WAVE") {
                Log.e(TAG, "Invalid WAVE format: $wave")
                return floatArrayOf()
            }

            // Get audio format info
            buffer.position(22)
            val numChannels = buffer.short.toInt()
            val sampleRate = buffer.int
            buffer.position(34)
            val bitsPerSample = buffer.short.toInt()

            Log.d(TAG, "WAV info: channels=$numChannels, rate=$sampleRate, bits=$bitsPerSample")

            // Find data chunk
            var dataOffset = 44
            while (dataOffset < bytes.size - 8) {
                val chunkId = String(bytes, dataOffset, 4)
                val chunkSize = ByteBuffer.wrap(bytes, dataOffset + 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int

                if (chunkId == "data") {
                    dataOffset += 8
                    break
                }
                dataOffset += 8 + chunkSize
            }

            // Read audio data
            val dataSize = bytes.size - dataOffset
            val numSamples = dataSize / (bitsPerSample / 8) / numChannels

            val samples = FloatArray(numSamples)
            buffer.position(dataOffset)

            for (i in 0 until numSamples) {
                // Read sample (convert to mono if stereo)
                var sample = 0
                for (ch in 0 until numChannels) {
                    sample += when (bitsPerSample) {
                        16 -> buffer.short.toInt()
                        8 -> (buffer.get().toInt() - 128) * 256
                        else -> buffer.short.toInt()
                    }
                }
                sample /= numChannels

                // Normalize to [-1.0, 1.0]
                samples[i] = sample / 32768.0f
            }

            Log.d(TAG, "Read $numSamples samples")
            return samples

        } catch (e: Exception) {
            Log.e(TAG, "Error reading WAV file", e)
            return floatArrayOf()
        }
    }

    /**
     * Transcribe from raw PCM data (for real-time streaming)
     */
    suspend fun transcribePcm(
        pcmData: ShortArray,
        sampleRate: Int = 16000,
        language: String = "auto"
    ): String = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                if (contextPtr == 0L) {
                    return@withLock "Error: No model loaded"
                }

                // Convert ShortArray to FloatArray
                val samples = FloatArray(pcmData.size) { i ->
                    pcmData[i] / 32768.0f
                }

                whisperTranscribe(
                    contextPtr,
                    samples,
                    samples.size,
                    language,
                    false
                ).trim()

            } catch (e: Exception) {
                Log.e(TAG, "Error transcribing PCM data", e)
                "Error: ${e.message}"
            }
        }
    }
}