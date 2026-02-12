package com.lifeos.assistant.nlu

import android.content.Context
import android.util.Log
import com.lifeos.assistant.R
import com.lifeos.assistant.data.LocalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * ResponseGenerator - Generates responses based on classified intents
 * 
 * Features:
 * - Template-based response generation
 * - Multi-language support (English/Farsi)
 * - Dynamic content (time, date, calculations)
 * - Context-aware responses
 */
class ResponseGenerator(private val context: Context) {

    companion object {
        private const val TAG = "ResponseGenerator"
        
        // Response templates for English
        private val englishResponses = mapOf(
            IntentClassifier.INTENT_GREETING to listOf(
                "Hello! How can I help you today?",
                "Hi there! What can I do for you?",
                "Hey! Ready to assist you.",
                "Greetings! How may I help?"
            ),
            IntentClassifier.INTENT_GOODBYE to listOf(
                "Goodbye! Have a great day!",
                "See you later! Take care!",
                "Bye! Come back anytime!",
                "Farewell! Have a wonderful day!"
            ),
            IntentClassifier.INTENT_THANKS to listOf(
                "You're welcome!",
                "Happy to help!",
                "Anytime!",
                "Glad I could assist!",
                "No problem at all!"
            ),
            IntentClassifier.INTENT_UNKNOWN to listOf(
                "I'm not sure I understand. Could you rephrase that?",
                "I didn't catch that. Can you say it differently?",
                "Sorry, I don't understand. Try asking about time, weather, or reminders.",
                "I'm still learning. Can you try asking something else?"
            ),
            IntentClassifier.INTENT_HELP to listOf(
                "I can help you with:\n" +
                "• Time and date\n" +
                "• Setting reminders\n" +
                "• Taking notes\n" +
                "• Calculations\n" +
                "• Basic questions\n" +
                "Just speak naturally!",
                
                "Here's what I can do:\n" +
                "• Tell you the time\n" +
                "• Set reminders\n" +
                "• Save notes\n" +
                "• Do math\n" +
                "Try saying 'What time is it?' or 'Remind me to...'"
            )
        )

        // Response templates for Farsi
        private val farsiResponses = mapOf(
            IntentClassifier.INTENT_GREETING to listOf(
                "سلام! چطور می‌توانم کمک کنم؟",
                "درود! چه کاری می‌توانم انجام دهم؟",
                "سلام! آماده کمک هستم."
            ),
            IntentClassifier.INTENT_GOODBYE to listOf(
                "خداحافظ! روز خوبی داشته باشید!",
                "به سلامت! مراقب خود باشید!",
                "خداحافظ! هر وقت خواستید برگردید!"
            ),
            IntentClassifier.INTENT_THANKS to listOf(
                "خواهش می‌کنم!",
                "خوشحالم که کمک کردم!",
                "در خدمتم!",
                "اشکالی ندارد!"
            ),
            IntentClassifier.INTENT_UNKNOWN to listOf(
                "متوجه نشدم. می‌توانید دوباره بگویید؟",
                "نفهمیدم. لطفاً با کلمات دیگری بگویید.",
                "ببخشید، نمی‌فهمم. درباره زمان، یادآوری یا یادداشت بپرسید."
            ),
            IntentClassifier.INTENT_HELP to listOf(
                "می‌توانم در این موارد کمک کنم:\n" +
                "• گفتن ساعت و تاریخ\n" +
                "• تنظیم یادآوری\n" +
                "• نوشتن یادداشت\n" +
                "• محاسبات\n" +
                "• سوالات ساده\n" +
                "طبیعی صحبت کنید!"
            )
        )
    }

    private val database by lazy { LocalDatabase.getDatabase(context) }
    private val random = Random()

    /**
     * Generate response for an intent
     */
    suspend fun generateResponse(intent: IntentResult, originalText: String): String = 
        withContext(Dispatchers.Default) {
            try {
                when (intent.intent) {
                    IntentClassifier.INTENT_TIME -> getCurrentTime(intent.language)
                    IntentClassifier.INTENT_DATE -> getCurrentDate(intent.language)
                    IntentClassifier.INTENT_WEATHER -> getWeatherResponse(intent.language)
                    IntentClassifier.INTENT_REMINDER_SET -> setReminder(intent)
                    IntentClassifier.INTENT_REMINDER_GET -> getReminders(intent.language)
                    IntentClassifier.INTENT_NOTE_CREATE -> createNote(intent)
                    IntentClassifier.INTENT_NOTE_GET -> getNotes(intent.language)
                    IntentClassifier.INTENT_CALCULATE -> calculate(intent)
                    IntentClassifier.INTENT_TRANSLATE -> translate(intent)
                    else -> getTemplateResponse(intent.intent, intent.language)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating response", e)
                getErrorResponse(intent.language)
            }
        }

    /**
     * Get template-based response
     */
    private fun getTemplateResponse(intent: String, language: String): String {
        val templates = when (language) {
            "fa" -> farsiResponses[intent]
            else -> englishResponses[intent]
        } ?: englishResponses[IntentClassifier.INTENT_UNKNOWN]!!
        
        return templates[random.nextInt(templates.size)]
    }

    /**
     * Get current time
     */
    private fun getCurrentTime(language: String): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        return when (language) {
            "fa" -> {
                val timeStr = String.format("%02d:%02d", hour, minute)
                "ساعت الان $timeStr است."
            }
            else -> {
                val amPm = if (hour < 12) "AM" else "PM"
                val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                String.format("The current time is %d:%02d %s.", hour12, minute, amPm)
            }
        }
    }

    /**
     * Get current date
     */
    private fun getCurrentDate(language: String): String {
        val calendar = Calendar.getInstance()
        
        return when (language) {
            "fa" -> {
                // Persian date (simplified)
                val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                val date = dateFormat.format(calendar.time)
                "امروز $date است."
            }
            else -> {
                val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH)
                val date = dateFormat.format(calendar.time)
                "Today is $date."
            }
        }
    }

    /**
     * Weather response (offline - no real data)
     */
    private fun getWeatherResponse(language: String): String {
        return when (language) {
            "fa" -> "ببخشید، من به اینترنت دسترسی ندارم و نمی‌توانم اطلاعات آب و هوا را بگیرم."
            else -> "Sorry, I don't have internet access to get weather information. " +
                    "I'm an offline assistant!"
        }
    }

    /**
     * Set a reminder
     */
    private suspend fun setReminder(intent: IntentResult): String {
        val content = intent.entities["content"] ?: intent.originalText
        
        // Save to database
        val reminder = com.lifeos.assistant.data.Reminder(
            content = content,
            createdAt = System.currentTimeMillis(),
            triggerAt = intent.entities["time"]?.let { parseTime(it) } 
                ?: (System.currentTimeMillis() + 3600000) // Default: 1 hour
        )
        
        database.reminderDao().insert(reminder)
        
        return when (intent.language) {
            "fa" -> "یادآوری تنظیم شد: $content"
            else -> "Reminder set: $content"
        }
    }

    /**
     * Get reminders
     */
    private suspend fun getReminders(language: String): String {
        val reminders = database.reminderDao().getAllReminders()
        
        if (reminders.isEmpty()) {
            return when (language) {
                "fa" -> "هیچ یادآوری ندارید."
                else -> "You have no reminders."
            }
        }
        
        val reminderList = reminders.take(5).joinToString("\n") { "• ${it.content}" }
        
        return when (language) {
            "fa" -> "یادآوری‌های شما:\n$reminderList"
            else -> "Your reminders:\n$reminderList"
        }
    }

    /**
     * Create a note
     */
    private suspend fun createNote(intent: IntentResult): String {
        val content = intent.entities["content"] ?: intent.originalText
        
        val note = com.lifeos.assistant.data.Note(
            content = content,
            createdAt = System.currentTimeMillis()
        )
        
        database.noteDao().insert(note)
        
        return when (intent.language) {
            "fa" -> "یادداشت ذخیره شد: $content"
            else -> "Note saved: $content"
        }
    }

    /**
     * Get notes
     */
    private suspend fun getNotes(language: String): String {
        val notes = database.noteDao().getAllNotes()
        
        if (notes.isEmpty()) {
            return when (language) {
                "fa" -> "هیچ یادداشتی ندارید."
                else -> "You have no notes."
            }
        }
        
        val noteList = notes.take(5).joinToString("\n") { "• ${it.content}" }
        
        return when (language) {
            "fa" -> "یادداشت‌های شما:\n$noteList"
            else -> "Your notes:\n$noteList"
        }
    }

    /**
     * Calculate mathematical expression
     */
    private fun calculate(intent: IntentResult): String {
        return try {
            val expr = intent.entities["expression"] ?: intent.originalText
            val result = evaluateExpression(expr)
            
            when (intent.language) {
                "fa" -> "نتیجه: $result"
                else -> "The result is $result"
            }
        } catch (e: Exception) {
            when (intent.language) {
                "fa" -> "نمی‌توانم این محاسبه را انجام دهم."
                else -> "I couldn't calculate that."
            }
        }
    }

    /**
     * Evaluate simple mathematical expression
     */
    private fun evaluateExpression(expression: String): String {
        // Extract numbers and operator
        val pattern = Regex("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)")
        val match = pattern.find(expression)
        
        if (match != null) {
            val num1 = match.groupValues[1].toDouble()
            val operator = match.groupValues[2]
            val num2 = match.groupValues[3].toDouble()
            
            val result = when (operator) {
                "+" -> num1 + num2
                "-" -> num1 - num2
                "*" -> num1 * num2
                "/" -> if (num2 != 0.0) num1 / num2 else throw ArithmeticException("Division by zero")
                else -> throw IllegalArgumentException("Unknown operator")
            }
            
            // Format result
            return if (result == result.roundToInt().toDouble()) {
                result.roundToInt().toString()
            } else {
                String.format("%.2f", result)
            }
        }
        
        throw IllegalArgumentException("Could not parse expression")
    }

    /**
     * Translate text (simplified - just returns the text)
     */
    private fun translate(intent: IntentResult): String {
        val text = intent.entities["text"] ?: ""
        
        return when (intent.language) {
            "fa" -> "ترجمه: $text (نیاز به اتصال اینترنت برای ترجمه دقیق)"
            else -> "Translation: $text (Internet connection needed for accurate translation)"
        }
    }

    /**
     * Parse time string to milliseconds
     */
    private fun parseTime(timeStr: String): Long {
        // Simple time parsing
        val now = System.currentTimeMillis()
        
        return when {
            timeStr.contains("minute", ignoreCase = true) -> {
                val minutes = Regex("\\d+").find(timeStr)?.value?.toInt() ?: 5
                now + minutes * 60 * 1000
            }
            timeStr.contains("hour", ignoreCase = true) -> {
                val hours = Regex("\\d+").find(timeStr)?.value?.toInt() ?: 1
                now + hours * 60 * 60 * 1000
            }
            timeStr.contains("tomorrow", ignoreCase = true) -> {
                now + 24 * 60 * 60 * 1000
            }
            else -> now + 3600000 // Default: 1 hour
        }
    }

    /**
     * Get error response
     */
    private fun getErrorResponse(language: String): String {
        return when (language) {
            "fa" -> "متأسفانه خطایی رخ داد. لطفاً دوباره امتحان کنید."
            else -> "Sorry, something went wrong. Please try again."
        }
    }
}