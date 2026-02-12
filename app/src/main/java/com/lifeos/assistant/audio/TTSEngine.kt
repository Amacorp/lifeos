package com.lifeos.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class VoiceOption(
    val name: String,
    val displayName: String,
    val locale: Locale,
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val voice: Voice? = null,
    val isFarsi: Boolean = false
)

class TTSEngine(private val context: Context) {

    companion object {
        private const val TAG = "TTSEngine"

        // Farsi to phonetic mapping for TTS fallback
        private val FARSI_TO_PHONETIC = mapOf(
            'آ' to "aa", 'ا' to "a", 'ب' to "b", 'پ' to "p",
            'ت' to "t", 'ث' to "s", 'ج' to "j", 'چ' to "ch",
            'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "z",
            'ر' to "r", 'ز' to "z", 'ژ' to "zh", 'س' to "s",
            'ش' to "sh", 'ص' to "s", 'ض' to "z", 'ط' to "t",
            'ظ' to "z", 'ع' to "a", 'غ' to "gh", 'ف' to "f",
            'ق' to "gh", 'ک' to "k", 'گ' to "g", 'ل' to "l",
            'م' to "m", 'ن' to "n", 'و' to "v", 'ه' to "h",
            'ی' to "y", 'ي' to "y", 'ئ' to "y", 'ة' to "h",
            'ك' to "k", 'إ' to "e", 'أ' to "a", 'ؤ' to "o",
            '‌' to " ", // half-space
            'ـ' to "",  // kashida
            // Vowel marks
            'َ' to "a", 'ِ' to "e", 'ُ' to "o",
            'ً' to "an", 'ٍ' to "en", 'ٌ' to "on",
            'ّ' to "", 'ْ' to ""
        )

        // Common Farsi words with proper pronunciation
        private val FARSI_WORD_PRONUNCIATIONS = mapOf(
            "سلام" to "salaam",
            "خوبی" to "khoobi",
            "ممنون" to "mamnoon",
            "مرسی" to "mersi",
            "خداحافظ" to "khodaa haafez",
            "بله" to "baleh",
            "نه" to "na",
            "من" to "man",
            "تو" to "to",
            "او" to "oo",
            "ما" to "maa",
            "شما" to "shomaa",
            "چطوری" to "chetori",
            "حالت" to "haalet",
            "چطوره" to "chetoreh",
            "خوبم" to "khoobam",
            "عالیم" to "aaliam",
            "خواهش" to "khaahesh",
            "می‌کنم" to "mikonam",
            "لطفاً" to "lotfan",
            "ببخشید" to "bebakhshid",
            "اسمت" to "esmet",
            "چیه" to "chiyeh",
            "کجا" to "kojaa",
            "کی" to "key",
            "چرا" to "cheraa",
            "چی" to "chi",
            "هستم" to "hastam",
            "هستی" to "hasti",
            "است" to "ast",
            "هستیم" to "hastim",
            "هستید" to "hastid",
            "هستند" to "hastand",
            "می‌تونم" to "mitoonam",
            "می‌خوام" to "mikhaam",
            "دارم" to "daaram",
            "داری" to "daari",
            "داره" to "daareh",
            "کمک" to "komak",
            "دستیار" to "dastyaar",
            "هوشمند" to "hooshmand",
            "آفلاین" to "offline",
            "ساعت" to "saa-at",
            "تاریخ" to "taarikh",
            "امروز" to "emrooz",
            "فردا" to "fardaa",
            "دیروز" to "dirooz",
            "صبح" to "sobh",
            "ظهر" to "zohr",
            "شب" to "shab",
            "روز" to "rooz",
            "خوب" to "khoob",
            "بد" to "bad",
            "بزرگ" to "bozorg",
            "کوچک" to "koochak",
            "زیبا" to "zibaa",
            "دوست" to "doost",
            "عشق" to "eshgh",
            "زندگی" to "zendegi",
            "جوک" to "joke",
            "داستان" to "daastaan",
            "حقیقت" to "haghighat",
            "انگیزشی" to "angizeshi",
            "خنده‌دار" to "khandeh daar",
            "لایف‌اواس" to "life O S",
            "گوشی" to "gooshi",
            "برنامه" to "barnaameh",
            "تنظیمات" to "tanzimaat",
            "مدل" to "model",
            "وارد" to "vaared",
            "کنید" to "konid",
            "بگویید" to "begoyid",
            "بپرسید" to "beporsid",
            "نیست" to "nist",
            "نمی‌تونم" to "nemitoonam",
            "خصوصی" to "khosoosi",
            "اطلاعات" to "ettelaa-aat"
        )
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var nativeFarsiAvailable = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _currentVoice = MutableStateFlow<VoiceOption?>(null)
    val currentVoice: StateFlow<VoiceOption?> = _currentVoice.asStateFlow()

    private val _farsiAvailable = MutableStateFlow(true) // Always true now with transliteration
    val farsiAvailable: StateFlow<Boolean> = _farsiAvailable.asStateFlow()

    private val _languages = MutableStateFlow<List<String>>(emptyList())
    val languages: StateFlow<List<String>> = _languages.asStateFlow()

    private var speechRate = 1.0f
    private var speechPitch = 1.0f

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                Log.d(TAG, "TTS initialized")
                tts?.setLanguage(Locale.US)
                setupListener()
                checkNativeFarsi()
                loadAvailableVoices()
                checkLanguages()
            } else {
                Log.e(TAG, "TTS init failed: $status")
                isInitialized = false
            }
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
            }
        })
    }

    private fun checkNativeFarsi() {
        val f1 = tts?.isLanguageAvailable(Locale("fa", "IR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
        val f2 = tts?.isLanguageAvailable(Locale("fa")) ?: TextToSpeech.LANG_NOT_SUPPORTED
        nativeFarsiAvailable = f1 >= TextToSpeech.LANG_AVAILABLE || f2 >= TextToSpeech.LANG_AVAILABLE
        Log.d(TAG, "Native Farsi TTS: $nativeFarsiAvailable")

        // Always true because we have transliteration fallback
        _farsiAvailable.value = true
    }

    private fun checkLanguages() {
        try {
            val langs = mutableListOf<String>()
            val locales = listOf(
                Locale.US, Locale.UK, Locale("fa", "IR"), Locale("fa"),
                Locale.FRENCH, Locale.GERMAN, Locale("ar"), Locale("es"),
                Locale("tr"), Locale("hi")
            )

            for (locale in locales) {
                val result = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (result >= TextToSpeech.LANG_AVAILABLE) {
                    val name = locale.displayLanguage
                    if (name !in langs) langs.add(name)
                }
            }

            // Always add Farsi since we have transliteration
            if ("Persian" !in langs && "فارسی" !in langs) {
                langs.add("Farsi (via transliteration)")
            }

            _languages.value = langs
            Log.d(TAG, "Languages: $langs")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking languages: ${e.message}", e)
        }
    }

    private fun loadAvailableVoices() {
        try {
            val options = mutableListOf<VoiceOption>()

            // English presets
            options.add(VoiceOption("default_en", "🇺🇸 English - Default", Locale.US, 1.0f, 1.0f))
            options.add(VoiceOption("deep_male_en", "🇺🇸 English - Deep Male", Locale.US, 0.7f, 0.9f))
            options.add(VoiceOption("high_female_en", "🇺🇸 English - High Female", Locale.US, 1.4f, 1.0f))
            options.add(VoiceOption("fast_en", "🇺🇸 English - Fast", Locale.US, 1.0f, 1.5f))
            options.add(VoiceOption("slow_en", "🇺🇸 English - Slow & Clear", Locale.US, 0.9f, 0.7f))
            options.add(VoiceOption("robot_en", "🤖 English - Robot", Locale.US, 0.5f, 1.2f))
            options.add(VoiceOption("chipmunk_en", "🐿️ English - Chipmunk", Locale.US, 2.0f, 1.3f))
            options.add(VoiceOption("narrator_en", "📖 English - Narrator", Locale.US, 0.85f, 0.8f))

            // Farsi presets (always available via transliteration)
            val farsiLocale = if (nativeFarsiAvailable) {
                val f1 = tts?.isLanguageAvailable(Locale("fa", "IR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (f1 >= TextToSpeech.LANG_AVAILABLE) Locale("fa", "IR") else Locale("fa")
            } else {
                Locale.US // Will use transliteration
            }

            val farsiSuffix = if (nativeFarsiAvailable) "" else " (transliterated)"

            options.add(VoiceOption("default_fa", "🇮🇷 فارسی - پیش‌فرض$farsiSuffix", farsiLocale, 1.0f, 0.9f, isFarsi = true))
            options.add(VoiceOption("deep_male_fa", "🇮🇷 فارسی - مرد$farsiSuffix", farsiLocale, 0.7f, 0.85f, isFarsi = true))
            options.add(VoiceOption("high_female_fa", "🇮🇷 فارسی - زن$farsiSuffix", farsiLocale, 1.4f, 0.9f, isFarsi = true))
            options.add(VoiceOption("slow_fa", "🇮🇷 فارسی - آهسته$farsiSuffix", farsiLocale, 0.9f, 0.65f, isFarsi = true))
            options.add(VoiceOption("fast_fa", "🇮🇷 فارسی - سریع$farsiSuffix", farsiLocale, 1.0f, 1.3f, isFarsi = true))

            // System voices
            val voices = tts?.voices ?: emptySet()
            for (voice in voices) {
                val lang = voice.locale.language
                if ((lang == "en" || lang == "fa") && !voice.isNetworkConnectionRequired) {
                    val flag = if (lang == "en") "🇺🇸" else "🇮🇷"
                    val langName = if (lang == "en") "English" else "فارسی"
                    val shortName = voice.name.replace(Regex(".*#"), "").take(25)
                    options.add(
                        VoiceOption(
                            name = "sys_${voice.name}",
                            displayName = "$flag $langName - $shortName",
                            locale = voice.locale,
                            pitch = 1.0f,
                            speed = 1.0f,
                            voice = voice,
                            isFarsi = lang == "fa"
                        )
                    )
                }
            }

            _availableVoices.value = options
            _currentVoice.value = options.firstOrNull()
            Log.d(TAG, "Loaded ${options.size} voices, native Farsi: $nativeFarsiAvailable")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading voices: ${e.message}", e)
        }
    }

    fun refreshVoices() {
        if (isInitialized) {
            checkNativeFarsi()
            loadAvailableVoices()
            checkLanguages()
        }
    }

    fun setVoice(voiceOption: VoiceOption) {
        if (!isInitialized) return
        try {
            _currentVoice.value = voiceOption

            if (voiceOption.isFarsi && nativeFarsiAvailable) {
                tts?.setLanguage(voiceOption.locale)
            } else if (!voiceOption.isFarsi) {
                tts?.setLanguage(voiceOption.locale)
            } else {
                // Farsi transliteration mode - use English locale
                tts?.setLanguage(Locale.US)
            }

            voiceOption.voice?.let { tts?.voice = it }
            speechPitch = voiceOption.pitch
            speechRate = voiceOption.speed
            tts?.setPitch(speechPitch)
            tts?.setSpeechRate(speechRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting voice: ${e.message}", e)
        }
    }

    fun setCustomPitch(pitch: Float) {
        speechPitch = pitch.coerceIn(0.3f, 2.0f)
        tts?.setPitch(speechPitch)
        _currentVoice.value = _currentVoice.value?.copy(pitch = speechPitch)
    }

    fun setCustomSpeed(speed: Float) {
        speechRate = speed.coerceIn(0.3f, 2.5f)
        tts?.setSpeechRate(speechRate)
        _currentVoice.value = _currentVoice.value?.copy(speed = speechRate)
    }

    fun speak(text: String) {
        if (!isInitialized) return
        stop()

        val currentVoiceOption = _currentVoice.value
        val isFarsiVoice = currentVoiceOption?.isFarsi == true
        val textIsFarsi = isFarsiText(text)

        val processedText: String
        var useLocale = currentVoiceOption?.locale ?: Locale.US

        if (textIsFarsi || isFarsiVoice) {
            if (nativeFarsiAvailable) {
                // Use native Farsi TTS
                useLocale = if (tts?.isLanguageAvailable(Locale("fa", "IR")) ?: TextToSpeech.LANG_NOT_SUPPORTED >= TextToSpeech.LANG_AVAILABLE) {
                    Locale("fa", "IR")
                } else {
                    Locale("fa")
                }
                tts?.setLanguage(useLocale)
                processedText = cleanTextForSpeech(text)
            } else {
                // Transliterate Farsi to phonetic English
                tts?.setLanguage(Locale.US)
                processedText = transliterateFarsi(text)
                Log.d(TAG, "Transliterated: $processedText")
            }
        } else {
            tts?.setLanguage(useLocale)
            processedText = cleanTextForSpeech(text)
        }

        val utteranceId = "tts_${System.currentTimeMillis()}"
        tts?.speak(processedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Transliterate Farsi text to phonetic English that TTS can pronounce
     */
    private fun transliterateFarsi(text: String): String {
        var result = text

        // First, replace known words with their pronunciations
        for ((farsi, phonetic) in FARSI_WORD_PRONUNCIATIONS) {
            result = result.replace(farsi, " $phonetic ")
        }

        // Then transliterate remaining Farsi characters
        val sb = StringBuilder()
        for (char in result) {
            val mapped = FARSI_TO_PHONETIC[char]
            if (mapped != null) {
                sb.append(mapped)
            } else if (char.isLetterOrDigit() || char.isWhitespace() || char in ".,!?;:'-()") {
                sb.append(char)
            } else {
                sb.append(' ')
            }
        }

        // Clean up
        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isFarsiText(text: String): Boolean {
        val farsiPattern = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
        val farsiCount = farsiPattern.findAll(text).count()
        return farsiCount > text.length * 0.2
    }

    private fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("[✓✅❌👋😄🎤🔒🤖🧠⚡●○🔊🐿️📖🇺🇸🇮🇷🏆🥈🥉💪✨📚🚀🌟🌳⏰🏃🌈🎯🍯🐙🦩🍌🐱🗼🐬⚔️🔬⚡👃🦈🐻🥚😮🐕🚲🦕❄️🧀🪐🐆💎🦴🎲🪙🐟📝🏰🌍💙🎉⚛️🌊🌾🐿🐻🥶🍝🌙☀️🤔😊🌟♟️]"), "")
            .replace("\n", ". ")
            .replace("•", "")
            .replace("  ", " ")
            .trim()
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun isReady(): Boolean = isInitialized

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Shutdown error: ${e.message}", e)
        }
    }
}