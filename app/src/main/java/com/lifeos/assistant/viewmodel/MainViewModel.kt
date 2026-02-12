package com.lifeos.assistant.viewmodel

import android.app.Application
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.assistant.ai.LLMEngine
import com.lifeos.assistant.audio.TTSEngine
import com.lifeos.assistant.audio.VoiceOption
import com.lifeos.assistant.audio.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class MainUiState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isSpeaking: Boolean = false,
    val isModelLoaded: Boolean = false,
    val isModelLoading: Boolean = true,
    val isLLMLoaded: Boolean = false,
    val isLLMLoading: Boolean = false,
    val hasLLMBinary: Boolean = true,
    val recognizedText: String = "",
    val responseText: String = "",
    val errorMessage: String? = null,
    val statusMessage: String = "Initializing...",
    val voskModelInfo: String = "No speech model",
    val llmModelInfo: String = "No AI model",
    val llmBinaryInfo: String = "Built-in engine ✓",
    val conversationHistory: List<ConversationItem> = emptyList(),
    val availableVoices: List<VoiceOption> = emptyList(),
    val currentVoiceName: String = "Default",
    val currentPitch: Float = 1.0f,
    val currentSpeed: Float = 1.0f,
    val isFarsiTTSAvailable: Boolean = false,
    val installedTTSLanguages: List<String> = emptyList()
)

data class ConversationItem(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var whisperEngine: WhisperEngine? = null
    private var ttsEngine: TTSEngine? = null
    private var llmEngine: LLMEngine? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val conversationPairs = mutableListOf<Pair<String, String>>()

    init {
        initializeTTS()
        initializeEngines()
    }

    private fun initializeTTS() {
        ttsEngine = TTSEngine(getApplication())

        viewModelScope.launch {
            ttsEngine!!.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(isSpeaking = speaking)
            }
        }

        viewModelScope.launch {
            delay(2000)
            ttsEngine!!.availableVoices.collect { voices ->
                _uiState.value = _uiState.value.copy(availableVoices = voices)
            }
        }

        viewModelScope.launch {
            ttsEngine!!.currentVoice.collect { voice ->
                voice?.let {
                    _uiState.value = _uiState.value.copy(
                        currentVoiceName = it.displayName,
                        currentPitch = it.pitch,
                        currentSpeed = it.speed
                    )
                }
            }
        }

        viewModelScope.launch {
            delay(2000)
            ttsEngine!!.farsiAvailable.collect { available ->
                _uiState.value = _uiState.value.copy(isFarsiTTSAvailable = available)
            }
        }

        viewModelScope.launch {
            delay(2000)
            ttsEngine!!.languages.collect { langs ->
                _uiState.value = _uiState.value.copy(installedTTSLanguages = langs)
            }
        }
    }

    private fun initializeEngines() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isModelLoading = true,
                    statusMessage = "Loading speech model..."
                )

                whisperEngine = WhisperEngine(getApplication())
                val voskSuccess = whisperEngine?.initialize() ?: false

                _uiState.value = _uiState.value.copy(statusMessage = "Loading AI brain...")

                llmEngine = LLMEngine(getApplication())
                val llmSuccess = llmEngine?.initialize() ?: false

                _uiState.value = _uiState.value.copy(
                    isModelLoaded = voskSuccess,
                    isModelLoading = false,
                    isLLMLoaded = llmSuccess,
                    hasLLMBinary = true,
                    voskModelInfo = whisperEngine?.getModelInfo() ?: "No speech model",
                    llmModelInfo = llmEngine?.getModelInfo() ?: "No AI model",
                    llmBinaryInfo = "Built-in engine ✓",
                    statusMessage = when {
                        voskSuccess && llmSuccess -> "Ready (Full AI mode)"
                        voskSuccess -> "Ready (import AI model for smarter responses)"
                        else -> "Setup needed"
                    },
                    errorMessage = if (!voskSuccess) "No speech model. Import in Settings." else null
                )

                if (voskSuccess) {
                    val greeting = if (llmSuccess) {
                        "Hello! I am LifeOS with full AI power! Ask me anything."
                    } else {
                        "Hello! I am LifeOS. Import an AI model in Settings to make me smarter!"
                    }
                    speak(greeting)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isModelLoaded = false,
                    isModelLoading = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    // ===== VOICE =====

    fun setVoice(voiceOption: VoiceOption) {
        ttsEngine?.setVoice(voiceOption)
        _uiState.value = _uiState.value.copy(
            currentVoiceName = voiceOption.displayName,
            currentPitch = voiceOption.pitch,
            currentSpeed = voiceOption.speed
        )
        val test = if (voiceOption.locale.language == "fa") "سلام! من لایف‌اواس هستم."
        else "Hello! This is how I sound now."
        speak(test)
    }

    fun setCustomPitch(pitch: Float) {
        ttsEngine?.setCustomPitch(pitch)
        _uiState.value = _uiState.value.copy(currentPitch = pitch)
    }

    fun setCustomSpeed(speed: Float) {
        ttsEngine?.setCustomSpeed(speed)
        _uiState.value = _uiState.value.copy(currentSpeed = speed)
    }

    fun testVoice() {
        val locale = ttsEngine?.currentVoice?.value?.locale
        val text = if (locale?.language == "fa") "سلام! من لایف‌اواس هستم، دستیار هوشمند شما."
        else "Hello! I am LifeOS, your smart AI assistant."
        speak(text)
    }

    fun refreshVoices() {
        ttsEngine?.refreshVoices()
        viewModelScope.launch {
            delay(500)
            val engine = ttsEngine ?: return@launch
            _uiState.value = _uiState.value.copy(
                availableVoices = engine.availableVoices.value,
                isFarsiTTSAvailable = engine.farsiAvailable.value,
                installedTTSLanguages = engine.languages.value
            )
        }
    }

    fun getTTSSettingsIntent(): Intent {
        return Intent("com.android.settings.TTS_SETTINGS")
    }

    fun getInstallTTSDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }

    // ===== MODEL MANAGEMENT =====

    fun importVoskModel(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isModelLoading = true,
                    statusMessage = "Importing speech model...",
                    errorMessage = null
                )
                val engine = whisperEngine ?: WhisperEngine(getApplication()).also { whisperEngine = it }
                val success = engine.importModelFromUri(uri)
                _uiState.value = _uiState.value.copy(
                    isModelLoaded = success,
                    isModelLoading = false,
                    voskModelInfo = engine.getModelInfo(),
                    statusMessage = if (success) "Speech model ready!" else "Failed",
                    errorMessage = if (!success) "Invalid Vosk model." else null
                )
                if (success) speak("Speech model imported!")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isModelLoading = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun deleteVoskModel() {
        whisperEngine?.deleteModel()
        _uiState.value = _uiState.value.copy(
            isModelLoaded = false,
            voskModelInfo = "No speech model"
        )
    }

    fun importLLMModel(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLLMLoading = true,
                    statusMessage = "Importing AI model...",
                    errorMessage = null
                )
                val engine = llmEngine ?: LLMEngine(getApplication()).also { llmEngine = it }
                val success = engine.importModelFromUri(uri)
                _uiState.value = _uiState.value.copy(
                    isLLMLoaded = success,
                    isLLMLoading = false,
                    llmModelInfo = engine.getModelInfo(),
                    statusMessage = if (success) "AI model loaded! Ready." else "Failed",
                    errorMessage = if (!success) "Invalid GGUF file." else null
                )
                if (success) speak("AI model imported! I'm now fully powered!")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLLMLoading = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun importLLMBinary(uri: Uri) {
        // No binary needed - engine is built-in
    }

    fun deleteLLMModel() {
        llmEngine?.deleteModel()
        conversationPairs.clear()
        _uiState.value = _uiState.value.copy(
            isLLMLoaded = false,
            llmModelInfo = "No AI model"
        )
    }

    fun deleteLLMBinary() {
        // No binary needed - engine is built-in
    }

    // ===== LISTENING =====

    fun startListening() {
        if (!_uiState.value.isModelLoaded) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "No speech model. Import in Settings."
            )
            return
        }
        ttsEngine?.stop()
        _uiState.value = _uiState.value.copy(
            isListening = true,
            errorMessage = null,
            statusMessage = "Listening..."
        )

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
                ).coerceAtLeast(4096)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isListening = false,
                            errorMessage = "Mic failed",
                            statusMessage = "Ready"
                        )
                    }
                    return@launch
                }

                val recognizer = whisperEngine?.createRecognizer()
                if (recognizer == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isListening = false,
                            errorMessage = "Recognizer failed",
                            statusMessage = "Ready"
                        )
                    }
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(bufferSize)
                var lastPartial = ""
                var silenceCount = 0

                while (isActive && _uiState.value.isListening) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        if (recognizer.acceptWaveForm(buffer, read)) {
                            val text = JSONObject(recognizer.result).optString("text", "")
                            if (text.isNotBlank()) {
                                withContext(Dispatchers.Main) { onSpeechResult(text) }
                                break
                            } else {
                                silenceCount++
                                if (silenceCount >= 30) {
                                    withContext(Dispatchers.Main) {
                                        _uiState.value = _uiState.value.copy(
                                            isListening = false,
                                            statusMessage = "Ready"
                                        )
                                    }
                                    break
                                }
                            }
                        } else {
                            val partial = JSONObject(recognizer.partialResult).optString("partial", "")
                            if (partial.isNotBlank() && partial != lastPartial) {
                                lastPartial = partial
                                silenceCount = 0
                                withContext(Dispatchers.Main) {
                                    _uiState.value = _uiState.value.copy(
                                        recognizedText = partial,
                                        statusMessage = "Hearing: $partial"
                                    )
                                }
                            }
                        }
                    }
                }

                if (!_uiState.value.isListening) {
                    val finalText = JSONObject(recognizer.finalResult).optString("text", "")
                    if (finalText.isNotBlank()) {
                        withContext(Dispatchers.Main) { onSpeechResult(finalText) }
                    } else {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(statusMessage = "Ready")
                        }
                    }
                }

                recognizer.close()
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isListening = false,
                        errorMessage = "Mic permission denied",
                        statusMessage = "Ready"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isListening = false,
                        errorMessage = "Error: ${e.message}",
                        statusMessage = "Ready"
                    )
                }
            }
        }
    }

    fun stopListening() {
        _uiState.value = _uiState.value.copy(
            isListening = false,
            statusMessage = "Processing..."
        )
    }

    fun stopSpeaking() {
        ttsEngine?.stop()
    }

    private fun speak(text: String) {
        ttsEngine?.speak(text)
    }

    private fun onSpeechResult(text: String) {
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isListening = false,
                statusMessage = "Ready"
            )
            return
        }
        val userItem = ConversationItem(text = text, isUser = true)
        _uiState.value = _uiState.value.copy(
            recognizedText = text,
            isListening = false,
            isProcessing = true,
            statusMessage = "Thinking...",
            conversationHistory = _uiState.value.conversationHistory + userItem
        )
        processCommand(text)
    }

    private fun processCommand(text: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(statusMessage = "AI is thinking...")
                val response = llmEngine?.generate(text, conversationPairs)
                    ?: "Sorry, something went wrong."

                conversationPairs.add(Pair(text, response))
                if (conversationPairs.size > 10) conversationPairs.removeAt(0)

                val responseItem = ConversationItem(text = response, isUser = false)
                _uiState.value = _uiState.value.copy(
                    responseText = response,
                    isProcessing = false,
                    statusMessage = "Speaking...",
                    conversationHistory = _uiState.value.conversationHistory + responseItem
                )
                speak(response)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    statusMessage = "Ready",
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun clearConversation() {
        conversationPairs.clear()
        _uiState.value = _uiState.value.copy(
            conversationHistory = emptyList(),
            recognizedText = "",
            responseText = ""
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun reloadModels() {
        initializeEngines()
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        whisperEngine?.close()
        llmEngine?.close()
        ttsEngine?.shutdown()
    }
}