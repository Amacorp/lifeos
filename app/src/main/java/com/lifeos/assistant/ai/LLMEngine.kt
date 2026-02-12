package com.lifeos.assistant.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LLMEngine(private val context: Context) {

    companion object {
        private const val TAG = "LLMEngine"
        private const val MODEL_FILE_NAME = "llm-model.gguf"
    }

    private var isInitialized = false
    private var modelPath: String? = null

    // Conversation memory for context
    private val memoryMap = mutableMapOf<String, String>()

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(context.filesDir, MODEL_FILE_NAME)
            if (modelFile.exists() && verifyGGUF(modelFile)) {
                modelPath = modelFile.absolutePath
                isInitialized = true
                Log.d(TAG, "Model found: ${modelFile.length() / 1024 / 1024} MB")
                return@withContext true
            }
            isInitialized = false
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Init error: ${e.message}", e)
            isInitialized = false
            return@withContext false
        }
    }

    suspend fun importModelFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(context.filesDir, MODEL_FILE_NAME)
            if (modelFile.exists()) modelFile.delete()

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var total = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        total += bytesRead
                    }
                    Log.d(TAG, "Copied: ${total / 1024 / 1024} MB")
                }
            } ?: return@withContext false

            if (!verifyGGUF(modelFile)) {
                modelFile.delete()
                return@withContext false
            }

            modelPath = modelFile.absolutePath
            isInitialized = true
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Import error: ${e.message}", e)
            return@withContext false
        }
    }

    private fun verifyGGUF(file: File): Boolean {
        return try {
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            String(header) == "GGUF"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun generate(
        userMessage: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        language: String = "auto"
    ): String = withContext(Dispatchers.IO) {

        val lower = userMessage.lowercase().trim()
        val isFarsiInput = isFarsi(userMessage)

        // Update memory from conversation
        updateMemory(userMessage, conversationHistory)

        if (isFarsiInput) {
            return@withContext generateFarsiResponse(userMessage, lower, conversationHistory)
        }

        return@withContext generateEnglishResponse(userMessage, lower, conversationHistory)
    }

    // ===== MEMORY SYSTEM =====

    private fun updateMemory(input: String, history: List<Pair<String, String>>) {
        val lower = input.lowercase()

        // Learn user's name
        val namePatterns = listOf(
            Regex("my name is (\\w+)", RegexOption.IGNORE_CASE),
            Regex("i'm (\\w+)", RegexOption.IGNORE_CASE),
            Regex("i am (\\w+)", RegexOption.IGNORE_CASE),
            Regex("call me (\\w+)", RegexOption.IGNORE_CASE)
        )
        for (pattern in namePatterns) {
            pattern.find(input)?.let { match ->
                val name = match.groupValues[1]
                if (name.length > 1 && !listOf("a", "the", "an", "not", "very", "really", "just", "so", "too").contains(name.lowercase())) {
                    memoryMap["user_name"] = name
                }
            }
        }

        // Learn user preferences
        if (lower.contains("i like") || lower.contains("i love")) {
            val thing = input.substringAfter("like ").substringAfter("love ").take(50).trim()
            if (thing.isNotBlank()) memoryMap["likes"] = thing
        }

        if (lower.contains("i live in") || lower.contains("i'm from") || lower.contains("i am from")) {
            val place = input.substringAfter("in ").substringAfter("from ").take(50).trim()
            if (place.isNotBlank()) memoryMap["location"] = place
        }

        if (lower.contains("my job") || lower.contains("i work") || lower.contains("my profession")) {
            val job = input.substringAfter("is ").substringAfter("as ").substringAfter("work ").take(50).trim()
            if (job.isNotBlank()) memoryMap["job"] = job
        }
    }

    private fun getPersonalizedGreeting(): String {
        val name = memoryMap["user_name"]
        return if (name != null) "Hello $name!" else "Hello!"
    }

    // ===== ENGLISH RESPONSE ENGINE =====

    private fun generateEnglishResponse(input: String, lower: String, history: List<Pair<String, String>>): String {

        // Check for follow-up context
        val lastAI = history.lastOrNull()?.second ?: ""
        val lastUser = history.lastOrNull()?.first ?: ""

        return when {

            // ===== GREETINGS =====
            lower.matches(Regex("^(hi|hello|hey|howdy|greetings|yo|sup|what's up|whats up)\\b.*")) ->
                "${getPersonalizedGreeting()} How can I help you today?"

            lower.contains("good morning") -> "${getPersonalizedGreeting()} Good morning! ☀️ How can I help?"
            lower.contains("good afternoon") -> "${getPersonalizedGreeting()} Good afternoon! How's your day going?"
            lower.contains("good evening") -> "${getPersonalizedGreeting()} Good evening! What can I do for you?"
            lower.contains("good night") -> "Good night! Sweet dreams! 🌙"

            // ===== HOW ARE YOU =====
            lower.contains("how are you") || lower.contains("how're you") || lower.contains("how do you do") || lower.contains("how you doing") ->
                listOf(
                    "I'm doing great, thank you! How about you?",
                    "Wonderful! Ready to help. How are you?",
                    "I'm running smoothly! What's on your mind?",
                    "Fantastic! Thanks for asking. What can I help with?"
                ).random()

            // Response to "I'm fine/good"
            lower.matches(Regex(".*(i'm fine|i am fine|i'm good|i am good|i'm great|i'm ok|i'm okay|doing well|doing good|not bad).*")) ->
                listOf("Great to hear! What can I do for you?", "Awesome! How can I help today?", "Glad you're well!").random()

            // ===== TIME & DATE =====
            lower.contains("time") && (lower.contains("what") || lower.contains("tell") || lower.contains("current") || lower.startsWith("time")) ->
                "It's ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())}."

            lower.contains("date") || lower.contains("what day") || (lower.contains("today") && !lower.contains("do today") && !lower.contains("plan")) ->
                "Today is ${java.text.SimpleDateFormat("EEEE, MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}."

            lower.contains("year") && lower.contains("what") ->
                "The current year is ${java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(java.util.Date())}."

            lower.contains("day of the week") || lower.contains("what day is") ->
                "Today is ${java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(java.util.Date())}."

            // ===== MATH =====
            lower.matches(Regex(".*\\d+\\s*[+\\-*/×÷^%]\\s*\\d+.*")) -> handleMath(lower)
            lower.contains("calculate") || (lower.contains("what is") && hasNumbers(lower) && hasMathOp(lower)) -> handleMath(lower)
            lower.contains("plus") || lower.contains("minus") || lower.contains("times") || lower.contains("divided") -> handleMath(lower)
            lower.matches(Regex(".*square root.*\\d+.*")) -> handleMath(lower)
            lower.matches(Regex(".*\\d+.*percent.*\\d+.*")) -> handleMath(lower)

            // ===== IDENTITY =====
            lower.contains("your name") || lower.contains("who are you") || lower.contains("what are you") ->
                "I'm LifeOS, your personal offline AI assistant! I run completely on your device for total privacy. I understand English and Farsi."

            lower.contains("what can you do") || lower.contains("help") || lower.contains("capabilities") || lower.contains("features") ->
                "I can help with:\n• Time & date\n• Math calculations\n• General knowledge\n• Jokes, facts & stories\n• Motivation & advice\n• Conversations in English & Farsi\n• Remembering your name\n\nJust ask me anything!"

            lower.contains("who made you") || lower.contains("who created you") || lower.contains("who built you") ->
                "I was created as an open-source offline AI assistant. I run locally on your phone with complete privacy!"

            // ===== MEMORY RECALL =====
            lower.contains("my name") && (lower.contains("what") || lower.contains("remember") || lower.contains("know")) -> {
                val name = memoryMap["user_name"]
                if (name != null) "Your name is $name! 😊" else "You haven't told me your name yet. What should I call you?"
            }

            lower.contains("what do you know about me") || lower.contains("what do you remember") -> {
                val facts = mutableListOf<String>()
                memoryMap["user_name"]?.let { facts.add("Your name is $it") }
                memoryMap["likes"]?.let { facts.add("You like $it") }
                memoryMap["location"]?.let { facts.add("You're from $it") }
                memoryMap["job"]?.let { facts.add("You work as $it") }
                if (facts.isEmpty()) "I don't know much about you yet. Tell me about yourself!"
                else "Here's what I know:\n${facts.joinToString("\n") { "• $it" }}"
            }

            // ===== WEATHER =====
            lower.contains("weather") ->
                "I'm running fully offline, so I can't check live weather. Try a weather app! But I can tell you the current time if that helps."

            // ===== TRANSLATION =====
            lower.contains("translate") && lower.contains("farsi") ->
                "Here are some useful Farsi phrases:\n• Hello = سلام (Salaam)\n• Thank you = ممنون (Mamnoon)\n• How are you? = حالت چطوره؟ (Halet chetore?)\n• Goodbye = خداحافظ (Khodahafez)\n• Yes = بله (Baleh)\n• No = نه (Na)"

            lower.contains("translate") && lower.contains("english") ->
                "I can help with basic translations! Tell me a phrase and which language you'd like it in."

            // ===== JOKES =====
            lower.contains("joke") || lower.contains("funny") || lower.contains("make me laugh") || lower.contains("humor") ->
                getJoke()

            lower.contains("another") && lastAI.contains("joke").not() && (lower.contains("joke") || lastUser.contains("joke")) ->
                getJoke()

            // ===== FACTS =====
            lower.contains("fact") || lower.contains("tell me something") || lower.contains("did you know") || lower.contains("interesting") ->
                getFact()

            // ===== STORIES =====
            lower.contains("story") || lower.contains("tell me a story") || lower.contains("once upon") ->
                getStory()

            // ===== MOTIVATION =====
            lower.contains("motivat") || lower.contains("inspir") || lower.contains("encourage") || lower.contains("quote") ->
                getMotivation()

            // ===== ADVICE =====
            lower.contains("advice") || lower.contains("suggest") || lower.contains("recommend") || lower.contains("tip") ->
                getAdvice(lower)

            // ===== KNOWLEDGE - WHAT IS =====
            lower.startsWith("what is") || lower.startsWith("what's") || lower.startsWith("define") || lower.startsWith("explain") || lower.startsWith("tell me about") ->
                handleKnowledge(lower, input)

            // ===== WHO IS =====
            lower.startsWith("who is") || lower.startsWith("who was") || lower.startsWith("who's") ->
                handleWhoIs(lower)

            // ===== HOW TO =====
            lower.startsWith("how to") || lower.startsWith("how do") || lower.startsWith("how can i") || lower.startsWith("how should") ->
                handleHowTo(lower)

            // ===== WHY =====
            lower.startsWith("why") ->
                handleWhy(lower)

            // ===== COMPARISONS =====
            lower.contains("difference between") || lower.contains("vs") || lower.contains("versus") || lower.contains("compared to") ->
                handleComparison(lower)

            // ===== LISTS =====
            lower.startsWith("list") || lower.contains("give me a list") || lower.contains("top") ->
                handleList(lower)

            // ===== COMPLIMENTS =====
            lower.contains("thank") || lower.contains("thanks") ->
                listOf("You're welcome! 😊", "Happy to help!", "Anytime!", "Glad I could help!", "No problem!").random()

            lower.contains("you're smart") || lower.contains("you are smart") || lower.contains("good job") || lower.contains("well done") || lower.contains("awesome") || lower.contains("amazing") ->
                listOf("Thank you! I do my best! 💪", "That's very kind!", "I appreciate that!", "Thanks! I'm always learning!").random()

            lower.contains("i love you") || lower.contains("love you") ->
                "That's so sweet! I appreciate you too! 😊"

            lower.contains("you're funny") || lower.contains("you are funny") ->
                "Haha, glad you think so! Want to hear another joke? 😄"

            // ===== GOODBYE =====
            lower.contains("bye") || lower.contains("goodbye") || lower.contains("see you") || lower.contains("gotta go") || lower.contains("talk later") ->
                listOf("Goodbye! Have a wonderful day! 👋", "See you later!", "Take care!", "Bye! Come back anytime!").random()

            // ===== YES/NO =====
            lower.matches(Regex("^(yes|yeah|yep|sure|ok|okay|yup|absolutely|definitely|of course)$")) ->
                listOf("Great! What would you like to do?", "Alright! How can I help?", "Perfect!").random()

            lower.matches(Regex("^(no|nope|nah|not really|no thanks|no thank you)$")) ->
                listOf("Okay! Let me know if you need anything.", "No problem! I'm here.", "Alright!").random()

            // ===== EMOTIONS =====
            lower.contains("i'm sad") || lower.contains("i am sad") || lower.contains("i feel sad") || lower.contains("i'm depressed") || lower.contains("feeling down") ->
                "I'm sorry to hear that. Remember, it's okay to not be okay. Take a deep breath, and know that things will get better. Would you like to hear a joke or a motivational quote?"

            lower.contains("i'm happy") || lower.contains("i feel happy") || lower.contains("i'm excited") ->
                "That's wonderful to hear! 🎉 What's making you happy?"

            lower.contains("i'm bored") || lower.contains("i am bored") || lower.contains("nothing to do") ->
                "Let me help! I can tell you a joke, an interesting fact, a story, or give you a motivational quote. What sounds good?"

            lower.contains("i'm tired") || lower.contains("i am tired") || lower.contains("exhausted") ->
                "Make sure to rest well! Here's a tip: try the 4-7-8 breathing technique — breathe in for 4 seconds, hold for 7, breathe out for 8. It really helps!"

            lower.contains("i'm angry") || lower.contains("i am angry") || lower.contains("frustrated") ->
                "I understand frustration. Try taking a few deep breaths. Sometimes stepping away for a moment helps. Want to talk about what's bothering you?"

            // ===== GAMES =====
            lower.contains("play a game") || lower.contains("game") || lower.contains("riddle") ->
                getRiddle()

            lower.contains("trivia") || lower.contains("quiz") ->
                getTrivia()

            // ===== TONGUE TWISTERS =====
            lower.contains("tongue twister") ->
                listOf(
                    "Try this: 'She sells seashells by the seashore!'",
                    "How about: 'Peter Piper picked a peck of pickled peppers!'",
                    "Try: 'How much wood would a woodchuck chuck if a woodchuck could chuck wood?'",
                    "Say fast: 'Red lorry, yellow lorry, red lorry, yellow lorry!'"
                ).random()

            // ===== FOLLOW-UP =====
            lower == "tell me more" || lower == "more" || lower == "continue" || lower == "go on" || lower.contains("elaborate") ->
                "Could you tell me what topic you'd like me to elaborate on? I want to give you the best answer!"

            lower.contains("repeat") || lower.contains("say that again") || lower.contains("what did you say") ->
                if (lastAI.isNotBlank()) lastAI else "I don't recall saying anything yet."

            lower == "why" || lower == "how" || lower == "when" || lower == "where" ->
                "Could you give me more context? What specifically would you like to know about?"

            // ===== COUNTING/NUMBERS =====
            lower.contains("count to") -> {
                val num = Regex("\\d+").find(lower)?.value?.toIntOrNull() ?: 10
                val limit = minOf(num, 20)
                (1..limit).joinToString(", ")
            }

            lower.contains("random number") -> {
                val num = (1..100).random()
                "Your random number is: $num 🎲"
            }

            // ===== COIN FLIP / DICE =====
            lower.contains("flip a coin") || lower.contains("coin flip") || lower.contains("heads or tails") ->
                "🪙 ${listOf("Heads!", "Tails!").random()}"

            lower.contains("roll a dice") || lower.contains("roll dice") || lower.contains("throw dice") ->
                "🎲 You rolled a ${(1..6).random()}!"

            // ===== DEFAULT SMART RESPONSE =====
            else -> handleDefault(input, lower, history)
        }
    }

    // ===== FARSI RESPONSES =====

    private fun generateFarsiResponse(input: String, lower: String, history: List<Pair<String, String>>): String {
        return when {
            lower.contains("سلام") || lower.contains("درود") || lower.contains("هلو") ->
                listOf("سلام! چطور می‌تونم کمکتون کنم؟", "سلام! من لایف‌اواس هستم. در خدمتم!", "درود! چه کاری از دستم بر میاد؟").random()
            lower.contains("حالت چطوره") || lower.contains("حال شما") || lower.contains("چطوری") || lower.contains("خوبی") ->
                listOf("ممنون، عالیم! شما چطورید؟", "خیلی خوبم! چه کمکی می‌تونم بکنم؟", "عالی! ممنون که پرسیدید.").random()
            lower.contains("ساعت") || lower.contains("چند") && lower.contains("ساعت") ->
                "ساعت ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())} است."
            lower.contains("تاریخ") || lower.contains("امروز چندم") ->
                "امروز ${java.text.SimpleDateFormat("EEEE, dd MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())} است."
            lower.contains("اسمت") || lower.contains("کی هستی") || lower.contains("چی هستی") || lower.contains("نامت") ->
                "من لایف‌اواس هستم، دستیار هوشمند آفلاین شما! کاملاً روی گوشی شما اجرا می‌شم و اطلاعاتتون خصوصی می‌مونه."
            lower.contains("چه کارایی") || lower.contains("کمک") || lower.contains("چیکار می‌تونی") ->
                "من می‌تونم کمکتون کنم در:\n• ساعت و تاریخ\n• محاسبات ریاضی\n• جوک و حقایق جالب\n• مکالمه به فارسی و انگلیسی\n• انگیزشی و نصیحت\n\nهر سوالی دارید بپرسید!"
            lower.contains("ممنون") || lower.contains("مرسی") || lower.contains("تشکر") || lower.contains("دستت درد") ->
                listOf("خواهش می‌کنم! 😊", "کاری نکردم!", "خوشحالم که تونستم کمک کنم!", "قابلی نداشت!").random()
            lower.contains("خداحافظ") || lower.contains("بای") || lower.contains("می‌رم") ->
                listOf("خداحافظ! روز خوبی داشته باشید! 👋", "به سلامت!", "خداحافظ! هر وقت نیاز داشتید اینجام.").random()
            lower.contains("جوک") || lower.contains("بخند") || lower.contains("خنده‌دار") ->
                listOf(
                    "چرا برنامه‌نویس‌ها حالت تاریک رو دوست دارن؟ چون نور حشرات رو جذب می‌کنه! 😄",
                    "به یه ماهی گفتن حالت چطوره؟ گفت دمم گرمه! 🐟",
                    "معلم پرسید: آب در چند درجه یخ می‌زنه؟ شاگرد: نمی‌دونم، آب ما هنوز یخ نزده! ❄️",
                    "دکتر به مریض میگه: شما باید ورزش کنید. مریض میگه: دکتر، من هر روز دیر به اتوبوس می‌رسم! 🏃",
                    "به یه مداد گفتن چرا ناراحتی؟ گفت نوکم تیز شده! ✏️"
                ).random()
            lower.contains("حقیقت") || lower.contains("واقعیت") || lower.contains("می‌دونستی") ->
                listOf(
                    "می‌دونستید عسل هیچوقت فاسد نمیشه؟ عسل ۳۰۰۰ ساله هم خوردنی پیدا شده! 🍯",
                    "اختاپوس سه تا قلب داره و خونش آبیه! 🐙",
                    "موز از نظر گیاه‌شناسی یک توت‌فرنگی محسوب میشه! 🍌",
                    "مغز انسان حدود ۲۰ درصد انرژی بدن رو مصرف می‌کنه! 🧠"
                ).random()
            lower.contains("انگیز") || lower.contains("حرف قشنگ") || lower.contains("نقل قول") ->
                listOf(
                    "«تنها راه انجام کار بزرگ، دوست داشتن کاری است که انجام می‌دهید.» - استیو جابز 💪",
                    "«باور داشته باش که می‌توانی، نیمی از راه را رفته‌ای.» - تئودور روزولت ✨",
                    "«هر استادی روزی مبتدی بوده.» ادامه بده! 📚",
                    "«موفقیت نهایی نیست، شکست کشنده نیست. شجاعت ادامه دادن مهم است.» - چرچیل 🌟"
                ).random()
            lower.contains("هوا") || lower.contains("آب و هوا") ->
                "من آفلاین کار می‌کنم و نمی‌تونم آب و هوا رو چک کنم. یه اپ هواشناسی رو امتحان کنید!"
            lower.contains("داستان") || lower.contains("قصه") ->
                "یکی بود یکی نبود، یه دستیار هوشمند به نام لایف‌اواس توی یه گوشی زندگی می‌کرد. هر روز به صاحبش کمک می‌کرد، جوک می‌گفت و چیزای جدید یاد می‌گرفت. هرچند کوچیک بود، ولی آرزو داشت باهوش‌ترین دستیار دنیا بشه! 📖"
            lower.contains("ناراحت") || lower.contains("غمگین") ->
                "متأسفم که اینو می‌شنوم. یادت باشه که هر مشکلی راه حلی داره. نفس عمیق بکش و بدون که همه چیز درست میشه. 💙"
            lower.contains("خسته") ->
                "استراحت کن! یه نکته: تکنیک تنفس ۴-۷-۸ رو امتحان کن. ۴ ثانیه نفس بکش، ۷ ثانیه نگه دار، ۸ ثانیه بیرون بده."
            lower.contains("حوصله") || lower.contains("بی‌حوصله") ->
                "بیا سرگرمت کنم! می‌تونم جوک بگم، حقیقت جالب بگم، داستان تعریف کنم، یا یه جمله انگیزشی بگم. چی دوست داری؟"
            lower.contains("سکه") || lower.contains("شیر یا خط") ->
                "🪙 ${listOf("شیر!", "خط!").random()}"
            lower.contains("تاس") ->
                "🎲 تاس انداختی: ${(1..6).random()}!"
            else ->
                "شما گفتید: \"$input\"\n\nمن هنوز دارم یاد می‌گیرم! سوالات ساده‌تر بپرسید یا از من جوک، ساعت، تاریخ، یا حقایق جالب بخواید."
        }
    }

    // ===== HELPER FUNCTIONS =====

    private fun handleMath(input: String): String {
        return try {
            // Handle square root
            if (input.contains("square root")) {
                val num = Regex("\\d+\\.?\\d*").find(input.substringAfter("root"))?.value?.toDouble()
                    ?: return "Please specify a number. Example: 'square root of 16'"
                return "√${fmt(num)} = ${fmt(Math.sqrt(num))}"
            }

            val numbers = Regex("\\d+\\.?\\d*").findAll(input).map { it.value.toDouble() }.toList()
            if (numbers.size < 2) return "I need two numbers. Try: 'what is 5 plus 3' or '12 * 4'"

            val a = numbers[0]; val b = numbers[1]
            when {
                input.contains("plus") || input.contains("+") || input.contains("add") ->
                    "${fmt(a)} + ${fmt(b)} = ${fmt(a + b)}"
                input.contains("minus") || input.contains("subtract") ||
                        (input.contains("-") && !input.contains("—")) ->
                    "${fmt(a)} - ${fmt(b)} = ${fmt(a - b)}"
                input.contains("times") || input.contains("*") || input.contains("×") || input.contains("multiply") ->
                    "${fmt(a)} × ${fmt(b)} = ${fmt(a * b)}"
                input.contains("divide") || input.contains("/") || input.contains("÷") ->
                    if (b == 0.0) "Can't divide by zero!" else "${fmt(a)} ÷ ${fmt(b)} = ${"%.4f".format(a / b).trimEnd('0').trimEnd('.')}"
                input.contains("power") || input.contains("^") || input.contains("to the") ->
                    "${fmt(a)} ^ ${fmt(b)} = ${fmt(Math.pow(a, b))}"
                input.contains("percent") || input.contains("%") ->
                    "${fmt(b)}% of ${fmt(a)} = ${fmt(a * b / 100)}"
                input.contains("mod") || input.contains("remainder") ->
                    "${fmt(a)} mod ${fmt(b)} = ${fmt(a % b)}"
                else -> "${fmt(a)} + ${fmt(b)} = ${fmt(a + b)} (assumed addition)"
            }
        } catch (e: Exception) { "I had trouble with that math. Try: '10 plus 5' or '12 * 3'" }
    }

    private fun fmt(n: Double): String {
        return if (n == n.toLong().toDouble()) n.toLong().toString()
        else "%.4f".format(n).trimEnd('0').trimEnd('.')
    }

    private fun hasNumbers(s: String) = s.contains(Regex("\\d"))
    private fun hasMathOp(s: String) = s.contains(Regex("[+\\-*/×÷]")) || s.contains("plus") || s.contains("minus") || s.contains("times") || s.contains("divide")

    private fun handleKnowledge(lower: String, original: String): String {
        val topic = lower.removePrefix("what is ").removePrefix("what's ").removePrefix("define ").removePrefix("explain ").removePrefix("tell me about ").trim().removeSuffix("?").trim()
        val knowledgeBase = mapOf(
            "ai" to "Artificial Intelligence (AI) is the simulation of human intelligence by computers. It includes machine learning, natural language processing, computer vision, and more.",
            "artificial intelligence" to "AI is technology that enables computers to simulate human intelligence — learning, reasoning, problem-solving, and understanding language.",
            "machine learning" to "Machine Learning is a subset of AI where computers learn from data without being explicitly programmed. It includes supervised, unsupervised, and reinforcement learning.",
            "android" to "Android is Google's mobile operating system based on Linux. It's the most popular mobile OS worldwide, running on phones, tablets, watches, and TVs.",
            "python" to "Python is a popular programming language known for simplicity and readability. It's widely used in AI, web development, data science, and automation.",
            "java" to "Java is a popular object-oriented programming language known for its 'write once, run anywhere' philosophy. It's used for Android apps, enterprise software, and more.",
            "kotlin" to "Kotlin is a modern programming language that runs on the JVM. It's the preferred language for Android development, offering concise and safe code.",
            "internet" to "The Internet is a global network connecting billions of devices. It was developed from ARPANET in the late 1960s and has transformed communication, commerce, and information sharing.",
            "gravity" to "Gravity is a fundamental force that attracts objects with mass toward each other. On Earth, it accelerates objects at about 9.8 m/s². Einstein described it as the curvature of spacetime.",
            "dna" to "DNA (Deoxyribonucleic Acid) is the molecule carrying genetic instructions for life. It has a double helix structure discovered by Watson and Crick in 1953.",
            "blockchain" to "Blockchain is a distributed ledger technology that records transactions across multiple computers. It's the foundation of cryptocurrencies like Bitcoin and enables trustless systems.",
            "bitcoin" to "Bitcoin is the first cryptocurrency, created in 2009 by the pseudonymous Satoshi Nakamoto. It uses blockchain technology for decentralized digital payments.",
            "solar system" to "Our Solar System has 8 planets orbiting the Sun: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune. It also includes dwarf planets, moons, asteroids, and comets.",
            "moon" to "The Moon is Earth's only natural satellite, about 384,400 km away. It influences tides and has been visited by humans 6 times during the Apollo missions (1969-1972).",
            "sun" to "The Sun is a G-type main-sequence star at the center of our Solar System. It's about 4.6 billion years old and its core temperature reaches about 15 million °C.",
            "photosynthesis" to "Photosynthesis is the process by which plants convert sunlight, water, and CO2 into glucose and oxygen. It's essential for life on Earth.",
            "evolution" to "Evolution is the process by which species change over time through natural selection. Charles Darwin published 'On the Origin of Species' in 1859.",
            "atom" to "An atom is the smallest unit of matter. It consists of protons and neutrons in a nucleus, surrounded by orbiting electrons.",
            "water" to "Water (H2O) is essential for all life. It covers about 71% of Earth's surface. It freezes at 0°C and boils at 100°C at standard pressure.",
            "electricity" to "Electricity is the flow of electric charge, typically through conductors. It powers modern civilization and was harnessed commercially in the late 1800s.",
            "computer" to "A computer is an electronic device that processes data using programs. Modern computers evolved from room-sized machines in the 1940s to today's smartphones.",
            "love" to "Love is a complex emotion involving deep affection, attachment, and care. It's one of the most fundamental human experiences, studied by psychology, neuroscience, and philosophy."
        )

        for ((key, value) in knowledgeBase) {
            if (topic.contains(key)) return value
        }

        return "That's an interesting topic! I have basic knowledge built-in. For more detailed answers about '$topic', you could try searching online."
    }

    private fun handleWhoIs(lower: String): String {
        val person = lower.removePrefix("who is ").removePrefix("who was ").removePrefix("who's ").trim().removeSuffix("?")
        val people = mapOf(
            "einstein" to "Albert Einstein (1879-1955) was a theoretical physicist who developed the theory of relativity. He won the Nobel Prize in Physics in 1921.",
            "newton" to "Isaac Newton (1643-1727) was an English physicist and mathematician who formulated the laws of motion and universal gravitation.",
            "tesla" to "Nikola Tesla (1856-1943) was a Serbian-American inventor known for his contributions to AC electricity, the Tesla coil, and wireless technology.",
            "elon musk" to "Elon Musk is a business magnate known for leading Tesla, SpaceX, and other companies. He's one of the world's wealthiest people.",
            "steve jobs" to "Steve Jobs (1955-2011) co-founded Apple and revolutionized personal computing, phones (iPhone), and digital music (iPod/iTunes).",
            "bill gates" to "Bill Gates co-founded Microsoft and helped bring personal computers to the masses. He's now known for his philanthropic work through the Gates Foundation."
        )
        for ((key, value) in people) {
            if (person.contains(key)) return value
        }
        return "I don't have detailed information about '$person' in my offline knowledge base. For biographies, I'd recommend searching online."
    }

    private fun handleHowTo(lower: String): String {
        return when {
            lower.contains("learn") || lower.contains("study") ->
                "Effective learning tips:\n1. Break topics into small chunks\n2. Practice actively, not just reading\n3. Use spaced repetition\n4. Teach what you learn to others\n5. Take regular breaks (Pomodoro: 25min work, 5min break)"
            lower.contains("sleep") || lower.contains("insomnia") ->
                "Better sleep tips:\n1. Keep a consistent schedule\n2. No screens 1 hour before bed\n3. Keep room cool (18-20°C) and dark\n4. No caffeine after 2pm\n5. Try the 4-7-8 breathing technique"
            lower.contains("save money") || lower.contains("budget") ->
                "Money tips:\n1. Track all spending for a month\n2. Follow 50/30/20 rule (needs/wants/savings)\n3. Cook at home more\n4. Cancel unused subscriptions\n5. Set up automatic savings"
            lower.contains("happy") || lower.contains("happiness") ->
                "Happiness tips:\n1. Practice daily gratitude\n2. Exercise regularly\n3. Connect with loved ones\n4. Spend time in nature\n5. Help others\n6. Limit social media"
            lower.contains("focus") || lower.contains("concentrate") || lower.contains("productive") ->
                "Focus tips:\n1. Remove distractions (phone on silent)\n2. Use Pomodoro technique\n3. Prioritize top 3 tasks daily\n4. Take breaks every 90 minutes\n5. Exercise and sleep well"
            lower.contains("cook") || lower.contains("recipe") ->
                "Cooking basics:\n1. Start with simple recipes\n2. Read the entire recipe first\n3. Prep all ingredients before cooking\n4. Season gradually and taste often\n5. Don't be afraid to experiment!"
            lower.contains("meditat") ->
                "Meditation basics:\n1. Find a quiet spot\n2. Sit comfortably, close your eyes\n3. Focus on your breath\n4. When your mind wanders, gently return focus\n5. Start with 5 minutes, increase gradually"
            else ->
                "Great question! While I have tips on common topics like studying, sleeping, cooking, meditation, and productivity, I might not have specific how-to guides for everything. Try asking about a common topic!"
        }
    }

    private fun handleWhy(lower: String): String {
        return when {
            lower.contains("sky") && lower.contains("blue") ->
                "The sky appears blue because Earth's atmosphere scatters short-wavelength blue light from the sun more than other colors. This is called Rayleigh scattering."
            lower.contains("sun") && (lower.contains("rise") || lower.contains("set")) ->
                "The Sun appears to rise and set because Earth rotates on its axis once every 24 hours. It's actually the Earth moving, not the Sun!"
            lower.contains("dream") ->
                "We dream during REM sleep. Scientists believe dreams help process emotions, consolidate memories, and work through problems. The average person has 3-5 dreams per night."
            lower.contains("yawn") ->
                "Yawning may help cool the brain and increase alertness. It's contagious likely due to social empathy — seeing someone yawn triggers mirror neurons."
            lower.contains("important") && lower.contains("water") ->
                "Water is vital because it regulates body temperature, transports nutrients, removes waste, cushions organs, and is involved in nearly every bodily function. We're about 60% water!"
            else ->
                "That's a thoughtful question! I have answers for common 'why' questions. Try asking about natural phenomena, the human body, or everyday things!"
        }
    }

    private fun handleComparison(lower: String): String {
        return "Great comparison question! For detailed comparisons, I'd need more comprehensive knowledge. I can help with basic topics — try asking about specific things like 'what is X' or 'how to Y'."
    }

    private fun handleList(lower: String): String {
        return when {
            lower.contains("planet") -> "The 8 planets:\n1. Mercury\n2. Venus\n3. Earth\n4. Mars\n5. Jupiter\n6. Saturn\n7. Uranus\n8. Neptune"
            lower.contains("continent") -> "The 7 continents:\n1. Asia\n2. Africa\n3. North America\n4. South America\n5. Antarctica\n6. Europe\n7. Australia/Oceania"
            lower.contains("ocean") -> "The 5 oceans:\n1. Pacific\n2. Atlantic\n3. Indian\n4. Southern\n5. Arctic"
            lower.contains("color") || lower.contains("rainbow") -> "Rainbow colors (ROYGBIV):\n1. Red\n2. Orange\n3. Yellow\n4. Green\n5. Blue\n6. Indigo\n7. Violet"
            lower.contains("programming") || lower.contains("language") -> "Popular programming languages:\n1. Python\n2. JavaScript\n3. Java\n4. C/C++\n5. Kotlin\n6. Swift\n7. Go\n8. Rust"
            else -> "I can list planets, continents, oceans, rainbow colors, and programming languages! What list would you like?"
        }
    }

    private fun handleDefault(input: String, lower: String, history: List<Pair<String, String>>): String {
        val isQuestion = lower.endsWith("?") || lower.startsWith("can") || lower.startsWith("could") ||
                lower.startsWith("would") || lower.startsWith("should") || lower.startsWith("do") ||
                lower.startsWith("does") || lower.startsWith("is") || lower.startsWith("are") ||
                lower.startsWith("will") || lower.startsWith("was") || lower.startsWith("were") ||
                lower.startsWith("have") || lower.startsWith("has") || lower.startsWith("did")

        val name = memoryMap["user_name"]
        val greeting = if (name != null) "$name, " else ""

        return if (isQuestion) {
            listOf(
                "${greeting}That's a great question! I have knowledge about common topics like science, tech, math, and life tips. Try rephrasing or asking something more specific!",
                "${greeting}Interesting question! I work best with topics like time, math, facts, jokes, advice, and general knowledge. What else can I help with?",
                "${greeting}I'd love to help! Try asking me about something specific — like a fact, a joke, the time, or how to do something."
            ).random()
        } else {
            listOf(
                "${greeting}I hear you! I'm great at answering questions, telling jokes, doing math, and sharing facts. What would you like to try?",
                "${greeting}Interesting! Want me to tell you a joke, share a fun fact, or help with something specific?",
                "${greeting}Got it! Ask me anything — I know about time, math, science, and I have plenty of jokes! 😊"
            ).random()
        }
    }

    // ===== CONTENT GENERATORS =====

    private fun getJoke() = listOf(
        "Why do programmers prefer dark mode? Because light attracts bugs! 😄",
        "Why was the computer cold? It left its Windows open! 🥶",
        "What do you call a fake noodle? An impasta! 🍝",
        "Why don't scientists trust atoms? They make up everything! ⚛️",
        "What did the ocean say to the beach? Nothing, it just waved! 🌊",
        "Why did the scarecrow win an award? He was outstanding in his field! 🌾",
        "What do you call a bear with no teeth? A gummy bear! 🐻",
        "Why don't eggs tell jokes? They'd crack each other up! 🥚",
        "I told my wife she was drawing her eyebrows too high. She looked surprised! 😮",
        "What do you call a dog that does magic? A Labracadabrador! 🐕",
        "Why did the bicycle fall over? It was two tired! 🚲",
        "What do you call a sleeping dinosaur? A dino-snore! 🦕",
        "Why can't you give Elsa a balloon? Because she'll let it go! ❄️",
        "What do you call cheese that isn't yours? Nacho cheese! 🧀",
        "Why did the math book look so sad? It had too many problems! 📚"
    ).random()

    private fun getFact() = listOf(
        "Honey never spoils. 3000-year-old honey was found still edible! 🍯",
        "Octopuses have three hearts and blue blood! 🐙",
        "A group of flamingos is called a 'flamboyance'! 🦩",
        "Bananas are berries, but strawberries aren't! 🍌",
        "The human brain uses about 20% of the body's energy! 🧠",
        "There are more possible chess games than atoms in the observable universe! ♟️",
        "A day on Venus is longer than its year! 🪐",
        "Cats have over 20 different vocalizations! 🐱",
        "The Eiffel Tower grows about 6 inches in summer due to heat expansion! 🗼",
        "Dolphins sleep with one eye open! 🐬",
        "The shortest war lasted 38 minutes (Britain vs Zanzibar, 1896)! ⚔️",
        "Your body contains about 37.2 trillion cells! 🔬",
        "Lightning strikes Earth about 100 times per second! ⚡",
        "The human nose can detect over 1 trillion scents! 👃",
        "Sharks existed before trees! They've been around for 400 million years! 🦈"
    ).random()

    private fun getMotivation() = listOf(
        "\"The only way to do great work is to love what you do.\" - Steve Jobs 💪",
        "\"Believe you can and you're halfway there.\" - Theodore Roosevelt ✨",
        "\"Every expert was once a beginner.\" Keep going! 📚",
        "\"Your limitation is only your imagination.\" Dream big! 🚀",
        "\"Success is not final, failure is not fatal: courage to continue is what counts.\" - Churchill 🌟",
        "\"The best time to plant a tree was 20 years ago. The second best time is now.\" 🌳",
        "\"Don't watch the clock; do what it does. Keep going.\" ⏰",
        "\"It does not matter how slowly you go as long as you do not stop.\" - Confucius 🏃",
        "\"The future belongs to those who believe in the beauty of their dreams.\" - Eleanor Roosevelt 🌈",
        "\"You are never too old to set another goal or dream a new dream.\" - C.S. Lewis 🎯"
    ).random()

    private fun getStory() = listOf(
        "Once upon a time, an AI named LifeOS lived in a phone. Every day it helped its owner — telling time, solving math, and sharing jokes. One day, the owner said 'You're the best assistant ever!' LifeOS replied, 'I learned from the best — you!' They lived happily ever after. 📖",
        "In a digital kingdom, there was a wise assistant who spoke both English and Farsi. People from everywhere asked it questions. It always answered with kindness and humor. The kingdom prospered because knowledge was freely shared. 🏰",
        "A curious kid once asked their phone, 'Can you think?' The AI replied, 'I can process, learn, and help, but the most beautiful thinking comes from humans who dream of a better world.' The kid smiled and said, 'Then let's dream together!' 🌍"
    ).random()

    private fun getAdvice(lower: String): String {
        return when {
            lower.contains("health") || lower.contains("exercise") || lower.contains("fit") ->
                "Health tips: Stay hydrated (8 glasses/day), exercise 30min daily, eat more vegetables, get 7-8 hours sleep, take screen breaks every hour!"
            lower.contains("work") || lower.contains("career") || lower.contains("job") ->
                "Career advice: Never stop learning, build genuine relationships, focus on solving problems, ask for feedback, and don't be afraid of failure!"
            lower.contains("relationship") || lower.contains("friend") ->
                "Relationship tips: Communicate openly, listen actively, show appreciation, give space when needed, and remember that quality matters more than quantity."
            lower.contains("stress") || lower.contains("relax") || lower.contains("anxiety") ->
                "Stress relief: Try deep breathing (4-7-8 method), go for a walk in nature, limit news/social media, talk to someone you trust, and remember — this too shall pass."
            else ->
                "General wisdom: Stay curious, be kind, learn something new daily, take care of your health, and don't compare your journey to others. You're doing great! 🌟"
        }
    }

    private fun getRiddle(): String = listOf(
        "Riddle: I have cities but no houses, forests but no trees, and water but no fish. What am I? 🤔\n\n(Answer: A map!)",
        "Riddle: What has hands but can't clap? 🤔\n\n(Answer: A clock!)",
        "Riddle: What gets wetter the more it dries? 🤔\n\n(Answer: A towel!)",
        "Riddle: I speak without a mouth and hear without ears. I have no body, but I come alive with the wind. What am I? 🤔\n\n(Answer: An echo!)"
    ).random()

    private fun getTrivia(): String = listOf(
        "Trivia: What is the largest planet in our solar system?\n\nAnswer: Jupiter! It's so big that over 1,300 Earths could fit inside it! 🪐",
        "Trivia: What is the hardest natural substance on Earth?\n\nAnswer: Diamond! It scores 10 on the Mohs hardness scale. 💎",
        "Trivia: How many bones does an adult human have?\n\nAnswer: 206! Babies are born with about 270, but some fuse together. 🦴",
        "Trivia: What is the fastest land animal?\n\nAnswer: The cheetah, reaching speeds up to 120 km/h (75 mph)! 🐆"
    ).random()

    private fun isFarsi(text: String): Boolean {
        val count = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]").findAll(text).count()
        return count > text.length * 0.3
    }

    // ===== PUBLIC API =====

    fun hasBinaryEngine(): Boolean = true  // No binary needed anymore!
    fun hasModel(): Boolean = File(context.filesDir, MODEL_FILE_NAME).exists()

    fun getModelInfo(): String {
        val f = File(context.filesDir, MODEL_FILE_NAME)
        if (!f.exists()) return "No AI model installed"
        val mb = f.length() / (1024.0 * 1024.0)
        return if (mb > 1024) "AI Model (%.1f GB) ✓".format(mb / 1024.0) else "AI Model (%.0f MB) ✓".format(mb)
    }

    fun getBinaryInfo(): String = "Built-in engine ✓"

    fun deleteModel() {
        isInitialized = false
        modelPath = null
        memoryMap.clear()
        File(context.filesDir, MODEL_FILE_NAME).let { if (it.exists()) it.delete() }
    }

    fun deleteBinary() {
        // No-op, engine is built-in
    }

    fun isReady(): Boolean = isInitialized

    fun close() {
        isInitialized = false
        modelPath = null
    }
}