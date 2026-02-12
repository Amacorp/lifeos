package com.lifeos.assistant.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioRecorder - Handles audio recording for speech recognition
 * 
 * Features:
 * - 16kHz, 16-bit PCM mono recording (Whisper compatible)
 * - Voice Activity Detection (VAD)
 * - Automatic silence detection
 * - WAV file output
 */
class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
        
        // Audio settings optimized for Whisper
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_FACTOR = 2
        
        // VAD settings
        const val VAD_THRESHOLD = 500  // Amplitude threshold for voice detection
        const val SILENCE_TIMEOUT_MS = 2000  // Stop after 2 seconds of silence
        const val MIN_RECORDING_MS = 500  // Minimum recording duration
        const val MAX_RECORDING_MS = 30000  // Maximum recording duration (30 seconds)
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    private var outputFile: File? = null
    private var voiceActivityDetected = false
    private var lastVoiceActivityTime = 0L
    private var recordingStartTime = 0L
    
    // Buffer size calculation
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    ) * BUFFER_SIZE_FACTOR

    /**
     * Initialize the audio recorder
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            // Pre-allocate buffer
            if (bufferSize < 0) {
                throw IllegalStateException("Invalid buffer size: $bufferSize")
            }
            Log.d(TAG, "AudioRecorder initialized with buffer size: $bufferSize")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioRecorder", e)
            throw e
        }
    }

    /**
     * Start recording audio
     */
    suspend fun startRecording(): File = withContext(Dispatchers.IO) {
        if (isRecording) {
            stopRecording()
        }

        try {
            // Create output file
            outputFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")
            
            // Initialize AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }

            // Start recording
            audioRecord?.startRecording()
            isRecording = true
            voiceActivityDetected = false
            recordingStartTime = System.currentTimeMillis()
            lastVoiceActivityTime = recordingStartTime

            // Start recording thread
            recordingThread = Thread { recordAudio() }
            recordingThread?.start()

            Log.d(TAG, "Recording started: ${outputFile?.absolutePath}")
            
            outputFile!!
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            release()
            throw e
        }
    }

    /**
     * Stop recording and return the audio file
     */
    suspend fun stopRecording(): File? = withContext(Dispatchers.IO) {
        if (!isRecording) {
            return@withContext outputFile
        }

        try {
            isRecording = false
            recordingThread?.join(1000)
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            // Write WAV header to the file
            outputFile?.let { file ->
                if (file.exists() && file.length() > 44) {
                    addWavHeader(file)
                    Log.d(TAG, "Recording saved: ${file.absolutePath} (${file.length()} bytes)")
                    return@withContext file
                }
            }
            
            Log.w(TAG, "Recording file is empty or doesn't exist")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            null
        }
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Check if voice activity was detected during recording
     */
    fun hasVoiceActivity(): Boolean = voiceActivityDetected

    /**
     * Release resources
     */
    suspend fun release() = withContext(Dispatchers.IO) {
        try {
            isRecording = false
            recordingThread?.interrupt()
            recordingThread?.join(500)
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            Log.d(TAG, "AudioRecorder released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecorder", e)
        }
    }

    /**
     * Recording loop running in background thread
     */
    private fun recordAudio() {
        val audioData = ShortArray(bufferSize / 2)
        var outputStream: FileOutputStream? = null
        
        try {
            outputStream = FileOutputStream(outputFile)
            
            // Reserve space for WAV header (44 bytes)
            outputStream.write(ByteArray(44))
            
            while (isRecording) {
                val read = audioRecord?.read(audioData, 0, audioData.size) ?: 0
                
                if (read > 0) {
                    // Check for voice activity
                    val amplitude = calculateAmplitude(audioData, read)
                    
                    if (amplitude > VAD_THRESHOLD) {
                        voiceActivityDetected = true
                        lastVoiceActivityTime = System.currentTimeMillis()
                    }
                    
                    // Convert to bytes and write
                    val byteBuffer = ByteBuffer.allocate(read * 2)
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until read) {
                        byteBuffer.putShort(audioData[i])
                    }
                    outputStream.write(byteBuffer.array())
                    
                    // Check silence timeout (only after minimum recording time)
                    val recordingDuration = System.currentTimeMillis() - recordingStartTime
                    val silenceDuration = System.currentTimeMillis() - lastVoiceActivityTime
                    
                    if (recordingDuration > MIN_RECORDING_MS && 
                        silenceDuration > SILENCE_TIMEOUT_MS &&
                        voiceActivityDetected) {
                        Log.d(TAG, "Silence detected, stopping recording")
                        isRecording = false
                    }
                    
                    // Check max recording duration
                    if (recordingDuration > MAX_RECORDING_MS) {
                        Log.d(TAG, "Max recording duration reached")
                        isRecording = false
                    }
                }
            }
            
            outputStream.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error in recording thread", e)
        } finally {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing output stream", e)
            }
        }
    }

    /**
     * Calculate amplitude for VAD
     */
    private fun calculateAmplitude(buffer: ShortArray, readSize: Int): Int {
        var sum = 0L
        for (i in 0 until readSize) {
            sum += kotlin.math.abs(buffer[i].toInt())
        }
        return (sum / readSize).toInt()
    }

    /**
     * Add WAV header to recorded PCM data
     */
    private fun addWavHeader(file: File) {
        val pcmDataSize = file.length() - 44
        val byteRate = SAMPLE_RATE * 2  // 16-bit mono
        
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF chunk descriptor
        buffer.put("RIFF".toByteArray())
        buffer.putInt((36 + pcmDataSize).toInt())
        buffer.put("WAVE".toByteArray())
        
        // fmt sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)  // Subchunk1Size (16 for PCM)
        buffer.putShort(1)  // AudioFormat (1 for PCM)
        buffer.putShort(1)  // NumChannels (1 for mono)
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(byteRate)
        buffer.putShort(2)  // BlockAlign
        buffer.putShort(16)  // BitsPerSample
        
        // data sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(pcmDataSize.toInt())
        
        // Write header to file
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }
}