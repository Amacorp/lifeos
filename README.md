# LifeOs - Offline Voice Assistant

A privacy-focused, completely offline voice assistant for Android. Optimized for low-memory devices (8GB RAM) and requiring no internet connection after initial setup.

## Features

- **100% Offline** - No internet connection required after model download
- **Privacy First** - All processing happens on your device
- **Multi-language** - Supports English and Farsi (Persian)
- **Lightweight** - Optimized for 8GB RAM systems
- **Minimal UI** - Single-button interface with intuitive controls
- **Extensible** - Easy to add new models and capabilities

## Screenshots

- Main screen with animated wake button
- Settings for model management
- Language selection
- Storage usage monitoring

## Requirements

- Android 7.0+ (API 24)
- 8GB RAM recommended
- 500MB free storage for models
- Microphone permission

## Quick Start

### 1. Clone and Open Project

```bash
git clone <repository-url>
```

Open the `LifeOs` folder in Android Studio.

### 2. Configure SDK Path

Copy `local.properties.template` to `local.properties` and update:

```properties
sdk.dir=C\:\\Android SDK
ndk.dir=C\:\\Android SDK\\ndk\\25.1.8937393
```

### 3. Download Models

Download a Whisper model and transfer to your device:

| Model | Size | Language | Download |
|-------|------|----------|----------|
| Whisper Tiny | ~39MB | Multilingual | [Download](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin) |
| Whisper Base | ~74MB | Multilingual | [Download](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin) |
| Whisper Tiny (en) | ~39MB | English | [Download](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin) |
| Vosk Farsi | ~50MB | Farsi | [Download](https://alphacephei.com/vosk/models/vosk-model-small-fa-0.5.zip) |

**Mirror Links (for Iran/China):**
- Use [hf-mirror.com](https://hf-mirror.com) for HuggingFace models
- Use VPN for direct GitHub access

### 4. Build and Install

```bash
# Build debug APK
./gradlew assembleDebug

# Or build release (requires signing)
./gradlew assembleRelease
```

Install the APK on your device.

### 5. First Run

1. Open LifeOs app
2. Grant microphone permission
3. Go to Settings → Model Management
4. Tap "Add Model" and select downloaded `.bin` file
5. Return to main screen and tap the wake button!

## Project Structure

```
LifeOs/
├── app/
│   ├── src/main/
│   │   ├── java/com/lifeos/assistant/
│   │   │   ├── MainActivity.kt          # Main entry point
│   │   │   ├── LifeOsApp.kt             # Application class
│   │   │   ├── ui/
│   │   │   │   ├── screens/
│   │   │   │   │   ├── MainScreen.kt    # Main voice UI
│   │   │   │   │   └── SettingsScreen.kt # Settings UI
│   │   │   │   ├── components/
│   │   │   │   │   ├── WakeButton.kt    # Animated wake button
│   │   │   │   │   └── ModelManager.kt  # Model management UI
│   │   │   │   └── theme/               # Material You theme
│   │   │   ├── audio/
│   │   │   │   ├── AudioRecorder.kt     # PCM audio recording
│   │   │   │   └── WhisperEngine.kt     # Whisper JNI wrapper
│   │   │   ├── nlu/
│   │   │   │   ├── IntentClassifier.kt  # Intent detection
│   │   │   │   └── ResponseGenerator.kt # Response generation
│   │   │   ├── tts/
│   │   │   │   └── OfflineTTS.kt        # Text-to-speech
│   │   │   └── data/
│   │   │       ├── ModelRepository.kt   # Model management
│   │   │       └── LocalDatabase.kt     # Room database
│   │   ├── cpp/                         # Native code
│   │   │   ├── CMakeLists.txt
│   │   │   ├── whisper.h
│   │   │   └── whisper_jni.cpp
│   │   └── res/                         # Android resources
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Memory Optimization

This project is optimized for 8GB RAM systems:

### Gradle Settings (`gradle.properties`)
```properties
org.gradle.jvmargs=-Xmx1536m
org.gradle.parallel=false
org.gradle.daemon=false
```

### Android Manifest
```xml
android:largeHeap="true"
```

### Build Configuration
- Minification enabled for release builds
- Resource shrinking enabled
- Native library stripped of debug symbols
- ABI filters for arm64-v8a and armeabi-v7a only

## Supported Commands

### English
- "Hello" / "Hi" - Greeting
- "What time is it?" - Current time
- "What's the date?" - Current date
- "Remind me to..." - Set reminder
- "Take a note..." - Save note
- "What are my reminders?" - List reminders
- "Calculate 5 plus 3" - Math operations
- "Goodbye" - Farewell

### Farsi (Persian)
- "سلام" - Greeting
- "ساعت چنده؟" - Current time
- "امروز چندمه؟" - Current date
- "یادم بنداز..." - Set reminder
- "یادداشت کن..." - Save note
- "یادآوری‌های من" - List reminders
- "محاسبه کن..." - Math operations
- "خداحافظ" - Farewell

## Model Management

### Adding Models
1. Go to Settings → Model Management
2. Tap "Add Model"
3. Select `.bin` or `.tflite` file from storage
4. Model will be copied to app storage

### Supported Formats
- `.bin` - Whisper models (ggml format)
- `.tflite` - TensorFlow Lite models
- `.pt` - PyTorch models (limited support)
- `.onnx` - ONNX models

### Model Recommendations

**For 8GB RAM devices:**
- Whisper Tiny (~39MB) - Fast, good accuracy
- Vosk Small (~50MB) - Good for command recognition

**For 4GB RAM devices:**
- Whisper Tiny only
- Disable TTS for better performance

## Language Setup

### Farsi (Persian) Support

1. Download Farsi model (Vosk or Whisper multilingual)
2. Load model in Settings
3. Select "Farsi" or "Auto-detect" in language settings
4. Speak in Farsi!

**Note:** TTS for Farsi requires:
- Android with Farsi TTS voice installed
- Or use visual text output (disable TTS in settings)

## Troubleshooting

### Build Issues

**Out of Memory Error:**
```bash
# Increase Gradle heap size in gradle.properties
org.gradle.jvmargs=-Xmx2g

# Or disable parallel builds
org.gradle.parallel=false
```

**NDK Not Found:**
```bash
# Install NDK in Android Studio
# Tools → SDK Manager → SDK Tools → NDK (Side by side)
```

**CMake Not Found:**
```bash
# Install CMake in Android Studio
# Tools → SDK Manager → SDK Tools → CMake
```

### Runtime Issues

**"No model loaded" error:**
- Download and load a model from Settings
- Ensure model file is not corrupted

**"Recognition failed" error:**
- Check microphone permission
- Speak clearly and closer to microphone
- Try a different model

**Low memory warnings:**
- Close other apps
- Use smaller model (Whisper Tiny)
- Clear cache in Settings

**TTS not working:**
- Check if TTS is enabled in Settings
- Install offline TTS voices in Android settings
- Disable TTS and use text output only

## Development

### Adding New Intents

1. Edit `IntentClassifier.kt`:
```kotlin
const val INTENT_MY_COMMAND = "my_command"

private val englishPatterns = mapOf(
    INTENT_MY_COMMAND to listOf(
        Pattern.compile("\\b(my trigger phrase)\\b", Pattern.CASE_INSENSITIVE)
    )
)
```

2. Edit `ResponseGenerator.kt`:
```kotlin
private val englishResponses = mapOf(
    INTENT_MY_COMMAND to listOf(
        "My response!",
        "Another response!"
    )
)
```

3. Handle in `generateResponse()`:
```kotlin
IntentClassifier.INTENT_MY_COMMAND -> handleMyCommand(intent)
```

### Custom Models

To add support for custom STT models:

1. Implement model interface in `audio/` package
2. Add model type in `ModelType` enum
3. Update `ModelRepository` for detection
4. Add UI components if needed

## Building for Production

### Release Build

1. Create signing keystore:
```bash
keytool -genkey -v -keystore lifeos.keystore -alias lifeos -keyalg RSA -keysize 2048 -validity 10000
```

2. Create `keystore.properties`:
```properties
storeFile=lifeos.keystore
storePassword=your_password
keyAlias=lifeos
keyPassword=your_password
```

3. Build release:
```bash
./gradlew assembleRelease
```

### APK Distribution

For easy sideloading (no Google Play):

```bash
# Build universal APK
./gradlew assembleRelease

# Or build per-architecture APKs for smaller size
./gradlew assembleRelease -Pandroid.injected.build.abi=arm64-v8a
```

## Privacy

LifeOs is designed with privacy as a core principle:

- ✅ No internet connection required
- ✅ No data collection
- ✅ No cloud services
- ✅ All processing on-device
- ✅ No ads or tracking
- ✅ Open source (coming soon)

## License

MIT License - See LICENSE file for details

## Credits

- [Whisper.cpp](https://github.com/ggerganov/whisper.cpp) - OpenAI Whisper inference
- [Vosk](https://alphacephei.com/vosk/) - Offline speech recognition
- [TensorFlow Lite](https://www.tensorflow.org/lite) - On-device ML
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI

## Support

For issues and feature requests, please use the GitHub issue tracker.

---

**LifeOs** - Your voice, your data, your assistant.