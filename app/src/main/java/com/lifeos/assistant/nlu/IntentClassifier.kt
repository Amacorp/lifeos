package com.lifeos.assistant.nlu

import android.content.Context
import android.util.Log
import com.lifeos.assistant.data.LocalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * IntentClassifier - Lightweight NLU for offline intent detection
 * 
 * Features:
 * - Rule-based intent classification
 * - Support for English and Farsi
 * - No ML model required (regex + keyword matching)
 * - Extensible intent types
 */
class IntentClassifier(private val context: Context) {

    companion object {
        private const val TAG = "IntentClassifier"
        
        // Intent types
        const val INTENT_UNKNOWN = "unknown"
        const val INTENT_GREETING = "greeting"
        const val INTENT_GOODBYE = "goodbye"
        const val INTENT_TIME = "time"
        const val INTENT_DATE = "date"
        const val INTENT_WEATHER = "weather"
        const val INTENT_REMINDER_SET = "reminder_set"
        const val INTENT_REMINDER_GET = "reminder_get"
        const val INTENT_NOTE_CREATE = "note_create"
        const val INTENT_NOTE_GET = "note_get"
        const val INTENT_CALCULATE = "calculate"
        const val INTENT_TRANSLATE = "translate"
        const val INTENT_SEARCH = "search"
        const val INTENT_HELP = "help"
        const val INTENT_THANKS = "thanks"
    }

    // Intent patterns for English
    private val englishPatterns = mapOf(
        INTENT_GREETING to listOf(
            Pattern.compile("\\b(hello|hi|hey|good morning|good afternoon|good evening|howdy|greetings)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_GOODBYE to listOf(
            Pattern.compile("\\b(bye|goodbye|see you|farewell|take care|later|good night)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_TIME to listOf(
            Pattern.compile("\\b(what time|current time|time is it|what's the time|tell me the time)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(time\\?)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_DATE to listOf(
            Pattern.compile("\\b(what date|current date|date today|what's the date|what day is it|what day)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(date\\?)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_WEATHER to listOf(
            Pattern.compile("\\b(weather|temperature|forecast|is it raining|will it rain|sunny|cloudy)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(what's the weather|how's the weather)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_REMINDER_SET to listOf(
            Pattern.compile("\\b(remind me|set reminder|create reminder|add reminder|don't let me forget)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(remind me to|remind me at|remind me in)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_REMINDER_GET to listOf(
            Pattern.compile("\\b(what are my reminders|show reminders|list reminders|my reminders)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(any reminders|do i have reminders)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_NOTE_CREATE to listOf(
            Pattern.compile("\\b(take note|write note|create note|save note|note that|remember that)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(note down|jot down)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_NOTE_GET to listOf(
            Pattern.compile("\\b(what are my notes|show notes|list notes|my notes|read notes)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(any notes|do i have notes)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_CALCULATE to listOf(
            Pattern.compile("\\b(calculate|compute|what is|what's|how much is|sum of|product of)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d+\\s*[+\\-*/]\\s*\\d+)\\b"),
            Pattern.compile("\\b(plus|minus|times|divided by|multiplied by)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_TRANSLATE to listOf(
            Pattern.compile("\\b(translate|how do you say|what is.*in|say.*in)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(translate to|translate from)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_SEARCH to listOf(
            Pattern.compile("\\b(search for|look up|find|google|what is|who is|where is|when is|why is|how to)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(tell me about|information about)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_HELP to listOf(
            Pattern.compile("\\b(help|what can you do|what do you do|how do you work|commands)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(assist me|i need help|can you help)\\b", Pattern.CASE_INSENSITIVE)
        ),
        INTENT_THANKS to listOf(
            Pattern.compile("\\b(thanks|thank you|appreciate it|grateful|cheers)\\b", Pattern.CASE_INSENSITIVE)
        )
    )

    // Intent patterns for Farsi (Persian)
    private val farsiPatterns = mapOf(
        INTENT_GREETING to listOf(
            Pattern.compile("(سلام|درود|صبح بخیر|عصر بخیر|شب بخیر|خوش آمدی|سلام علیکم)")
        ),
        INTENT_GOODBYE to listOf(
            Pattern.compile("(خداحافظ|بدرود|خوش بگذره|موفق باشی|شب بخیر|به سلامت)")
        ),
        INTENT_TIME to listOf(
            Pattern.compile("(ساعت چنده|ساعت چند است|زمان چنده|چه ساعتیه|ساعت رو بگو)")
        ),
        INTENT_DATE to listOf(
            Pattern.compile("(امروز چندمه|تاریخ چنده|تاریخ امروز|چندمین روز|چه روزیه)")
        ),
        INTENT_WEATHER to listOf(
            Pattern.compile("(هوا چطوره|آب و هوا|دما|باران|آفتابی|ابری|هواشناسی)")
        ),
        INTENT_REMINDER_SET to listOf(
            Pattern.compile("(یادم بنداز|یادآوری|یادآور|یادم بده|فراموش نکنم)")
        ),
        INTENT_REMINDER_GET to listOf(
            Pattern.compile("(یادآوری هام|یادآوری ها|چی یادم دادی|یادآوری های من)")
        ),
        INTENT_NOTE_CREATE to listOf(
            Pattern.compile("(یادداشت|یادداشت کن|بنویس|ثبت کن|به خاطر بسپار)")
        ),
        INTENT_NOTE_GET to listOf(
            Pattern.compile("(یادداشت هام|یادداشت ها|چی نوشتم|یادداشت های من)")
        ),
        INTENT_CALCULATE to listOf(
            Pattern.compile("(محاسبه|حساب کن|چند میشه|جمع|تفریق|ضرب|تقسیم)")
        ),
        INTENT_TRANSLATE to listOf(
            Pattern.compile("(ترجمه|ترجمه کن|چی میشه|به انگلیسی|به فارسی)")
        ),
        INTENT_SEARCH to listOf(
            Pattern.compile("(جستجو|پیدا کن|چیست|کیست|کجاست|چگونه|چطور)")
        ),
        INTENT_HELP to listOf(
            Pattern.compile("(کمک|راهنما|چیکار میتونی بکنی|چجوری کار میکنی)")
        ),
        INTENT_THANKS to listOf(
            Pattern.compile("(ممنون|متشکرم|سپاس|سپاسگزارم|ممنونم)")
        )
    )

    /**
     * Classify intent from text
     */
    suspend fun classify(text: String): IntentResult = withContext(Dispatchers.Default) {
        try {
            val normalizedText = text.trim().lowercase()
            
            // Detect language
            val language = detectLanguage(normalizedText)
            
            // Select pattern set based on language
            val patterns = when (language) {
                "fa" -> farsiPatterns
                else -> englishPatterns
            }
            
            // Match patterns
            for ((intent, patternList) in patterns) {
                for (pattern in patternList) {
                    if (pattern.matcher(normalizedText).find()) {
                        Log.d(TAG, "Intent matched: $intent for text: $text")
                        return@withContext IntentResult(
                            intent = intent,
                            confidence = 0.9f,
                            language = language,
                            originalText = text,
                            entities = extractEntities(normalizedText, intent)
                        )
                    }
                }
            }
            
            // No match found
            Log.d(TAG, "No intent matched for text: $text")
            IntentResult(
                intent = INTENT_UNKNOWN,
                confidence = 0.0f,
                language = language,
                originalText = text,
                entities = emptyMap()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying intent", e)
            IntentResult(
                intent = INTENT_UNKNOWN,
                confidence = 0.0f,
                language = "en",
                originalText = text,
                entities = emptyMap()
            )
        }
    }

    /**
     * Detect language (simple heuristic)
     */
    private fun detectLanguage(text: String): String {
        // Check for Farsi characters (Unicode range: \u0600-\u06FF)
        val farsiPattern = Pattern.compile("[\\u0600-\\u06FF]")
        return if (farsiPattern.matcher(text).find()) {
            "fa"
        } else {
            "en"
        }
    }

    /**
     * Extract entities from text based on intent
     */
    private fun extractEntities(text: String, intent: String): Map<String, String> {
        val entities = mutableMapOf<String, String>()
        
        when (intent) {
            INTENT_REMINDER_SET -> {
                // Extract time expressions
                extractTimeEntities(text, entities)
                // Extract reminder content
                extractContentAfterKeyword(text, entities, "remind me to", "content")
            }
            INTENT_NOTE_CREATE -> {
                extractContentAfterKeyword(text, entities, "note that", "content")
                extractContentAfterKeyword(text, entities, "take note", "content")
            }
            INTENT_CALCULATE -> {
                // Extract mathematical expression
                extractMathExpression(text, entities)
            }
            INTENT_TRANSLATE -> {
                // Extract text to translate
                extractContentAfterKeyword(text, entities, "translate", "text")
            }
        }
        
        return entities
    }

    /**
     * Extract time entities
     */
    private fun extractTimeEntities(text: String, entities: MutableMap<String, String>) {
        // Simple time patterns
        val timePatterns = listOf(
            Pattern.compile("\\b(at|in)\\s+(\\d+)\\s*(minutes?|hours?|days?|seconds?)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(at)\\s+(\\d{1,2}):?(\\d{2})?\\s*(am|pm)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(tomorrow|today|tonight|morning|afternoon|evening)\\b", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in timePatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                entities["time"] = matcher.group()
                break
            }
        }
    }

    /**
     * Extract content after a keyword
     */
    private fun extractContentAfterKeyword(
        text: String,
        entities: MutableMap<String, String>,
        keyword: String,
        entityKey: String
    ) {
        val index = text.indexOf(keyword, ignoreCase = true)
        if (index != -1) {
            val content = text.substring(index + keyword.length).trim()
            if (content.isNotEmpty()) {
                entities[entityKey] = content
            }
        }
    }

    /**
     * Extract mathematical expression
     */
    private fun extractMathExpression(text: String, entities: MutableMap<String, String>) {
        // Match simple math expressions
        val mathPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)")
        val matcher = mathPattern.matcher(text)
        if (matcher.find()) {
            entities["expression"] = matcher.group()
            entities["operand1"] = matcher.group(1)
            entities["operator"] = matcher.group(2)
            entities["operand2"] = matcher.group(3)
        }
    }
}

/**
 * Intent classification result
 */
data class IntentResult(
    val intent: String,
    val confidence: Float,
    val language: String,
    val originalText: String,
    val entities: Map<String, String>
)