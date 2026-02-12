package com.lifeos.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Bundle
import java.util.*

/**
 * OfflineTTS - Text-to-Speech using Android's built-in TTS engine
 * 
 * Features:
 * - Works offline with pre-installed voices
 * - Supports English and Farsi (if voices available)
 * - Queue management
 * - Callback on completion
 */
class OfflineTTS(private val context: Context) {

    companion object {
        private const val TAG = "OfflineTTS"
        private const val UTTERANCE_ID = "lifeos_tts"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false
    private val pendingMessages = mutableListOf<String>()
    private var completionCallback: (() -> Unit)? = null

    init {
        initializeTTS()
    }

    /**
     * Initialize Text-to-Speech engine
     */
    private fun initializeTTS() {
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    configureTTS()
                    Log.i(TAG, "TTS initialized successfully")
                    
                    // Process any pending messages
                    processPendingMessages()
                } else {
                    Log.e(TAG, "TTS initialization failed with status: $status")
                }
            }
            
            // Set up utterance progress listener
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    Log.d(TAG, "TTS started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    Log.d(TAG, "TTS completed: $utteranceId")
                    completionCallback?.invoke()
                    completionCallback = null
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    Log.e(TAG, "TTS error: $utteranceId")
                    completionCallback?.invoke()
                    completionCallback = null
                }
                
                override fun onError(utteranceId: String?, errorCode: Int) {
                    isSpeaking = false
                    Log.e(TAG, "TTS error: $utteranceId, code: $errorCode")
                    completionCallback?.invoke()
                    completionCallback = null
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TTS", e)
        }
    }

    /**
     * Configure TTS settings
     */
    private fun configureTTS() {
        tts?.let { engine ->
            // Set default language
            val result = engine.setLanguage(Locale.ENGLISH)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || 
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "English language not available")
            }
            
            // Set speech rate (1.0 is normal)
            engine.setSpeechRate(1.0f)
            
            // Set pitch (1.0 is normal)
            engine.setPitch(1.0f)
            
            // Check available languages
            val availableLanguages = engine.availableLanguages
            Log.d(TAG, "Available TTS languages: ${availableLanguages.joinToString()}")
        }
    }

    /**
     * Speak text
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet, queuing message")
            pendingMessages.add(text)
            completionCallback = onComplete
            return
        }

        // Stop any current speech
        stop()

        // Set completion callback
        completionCallback = onComplete

        // Speak the text
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
        
        val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        } else {
            @Suppress("DEPRECATION")
            val map = HashMap<String, String>()
            map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = UTTERANCE_ID
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, map)
        }

        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "Error speaking text")
            isSpeaking = false
            onComplete?.invoke()
        }
    }

    /**
     * Speak text in specific language
     */
    fun speakInLanguage(text: String, languageCode: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            pendingMessages.add(text)
            completionCallback = onComplete
            return
        }

        // Set language
        val locale = when (languageCode) {
            "fa", "fas" -> Locale("fa")
            "en" -> Locale.ENGLISH
            else -> Locale.ENGLISH
        }

        val result = tts?.setLanguage(locale)
        
        if (result == TextToSpeech.LANG_MISSING_DATA || 
            result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language $languageCode not available, falling back to English")
            tts?.setLanguage(Locale.ENGLISH)
        }

        speak(text, onComplete)
    }

    /**
     * Stop speaking
     */
    fun stop() {
        if (isInitialized) {
            tts?.stop()
            isSpeaking = false
        }
    }

    /**
     * Check if TTS is speaking
     */
    fun isSpeaking(): Boolean = isSpeaking

    /**
     * Check if TTS is initialized
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * Get available languages
     */
    fun getAvailableLanguages(): Set<Locale> {
        return tts?.availableLanguages ?: emptySet()
    }

    /**
     * Check if language is available
     */
    fun isLanguageAvailable(languageCode: String): Boolean {
        val locale = when (languageCode) {
            "fa", "fas" -> Locale("fa")
            "en" -> Locale.ENGLISH
            else -> Locale(languageCode)
        }
        
        val result = tts?.isLanguageAvailable(locale)
        return result == TextToSpeech.LANG_AVAILABLE || 
               result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
               result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    /**
     * Process pending messages
     */
    private fun processPendingMessages() {
        pendingMessages.forEach { message ->
            speak(message)
        }
        pendingMessages.clear()
    }

    /**
     * Release TTS resources
     */
    fun release() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            Log.i(TAG, "TTS released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TTS", e)
        }
    }

    /**
     * Set speech rate
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    /**
     * Set pitch
     */
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }
}