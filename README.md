# LifeOS - Private Offline AI Assistant

**LifeOS** is a privacy-first, fully offline Voice AI Assistant for Android. It combines local Speech-to-Text (STT) and Large Language Models (LLM) to create a smart assistant that lives entirely on your device—no internet connection required, no data collection, no servers.

It is designed to be lightweight, fast, and supports **English** and **Farsi (Persian)** natively.

---

## 🚀 Key Features

*   **100% Offline & Private:** Your voice and data never leave your phone.
*   **Smart AI Brain:** Supports **GGUF** models (like Qwen, TinyLlama, Phi) to provide intelligent, ChatGPT-like responses locally.
*   **Speech Recognition:** Powered by **Vosk**, allowing for accurate offline voice detection.
*   **Dual-Language Support:**
    *   **English:** Full conversational support.
    *   **Farsi (Persian):** Unique support for Farsi voice interaction.
*   **Smart TTS Engine:**
    *   Uses Android's native Text-to-Speech.
    *   **Farsi Transliteration:** Automatically converts Farsi text to phonetic English pronunciation, allowing the app to speak Farsi even on devices that don't support the language natively.
*   **Memory System:** Remembers your name, job, and preferences during the conversation.
*   **Modern UI:** Beautiful, animated Material You interface built with Jetpack Compose.

---

## 🛠️ Tech Stack

*   **Language:** Kotlin
*   **UI:** Jetpack Compose (Material3)
*   **Architecture:** MVVM (Model-View-ViewModel)
*   **STT Engine:** Vosk Android
*   **LLM Engine:** Custom implementation for GGUF model inference
*   **State Management:** Kotlin Coroutines & Flows
*   **Persistence:** DataStore Preferences

---

## ⚙️ Setup Guide (Important)

Because LifeOS is 100% offline, it does not come with pre-bundled AI models (to keep the app size small). You must download the "Brain" and "Ears" files separately.

### Step 1: Download & Install
1.  Download the latest APK release.
2.  Install it on your Android device.
3.  Grant **Microphone** permissions when prompted.

### Step 2: Setup Speech Recognition (The Ears)
1.  Go to the [Vosk Models Page](https://alphacephei.com/vosk/models).
2.  Download a model (keep it as a `.zip` file):
    *   **For English:** `vosk-model-small-en-us-0.15` (~40MB)
    *   **For Farsi:** `vosk-model-small-fa-0.5` (~40MB)
3.  Open LifeOS App -> **Settings** -> **Speech Model** -> **Browse**.
4.  Select the downloaded `.zip` file.

### Step 3: Setup AI Brain (The Intelligence)
To enable "Smart Mode" (ChatGPT-like intelligence), you need a GGUF model.
1.  Go to [HuggingFace](https://huggingface.co/).
2.  Search for and download a **Q4_K_M.gguf** file.
    *   **Recommended (Smartest for English & Farsi):** `Qwen2.5-1.5B-Instruct-Q4_K_M.gguf` (~1.1GB).
    *   **Recommended (Fastest):** `Qwen2.5-0.5B-Instruct-Q4_K_M.gguf` (~400MB).
3.  Open LifeOS App -> **Settings** -> **AI Brain** -> **Browse**.
4.  Select the `.gguf` file.

*Note: Without a GGUF model, the app runs in "Basic Mode" using pattern matching for simple commands.*

---

## 🎤 How to Use

1.  **Tap the Mic:** Tap the large microphone button on the home screen.
2.  **Speak:** Say your command or question (e.g., "What time is it?", "Tell me a joke", "Why is the sky blue?").
3.  **Listen:** The app will process your request locally and speak the answer back to you.
4.  **Stop:** Tap the Stop button or the Volume icon to interrupt the AI.

### Voice Settings
In the **Settings** menu, you can:
*   Change the **Voice Pitch** (Deep vs High).
*   Change the **Voice Speed** (Slow vs Fast).
*   Select specific system voices.

---

## 🔒 Privacy Policy

LifeOS is an open-source project dedicated to privacy.
*   **No Internet Access:** The app does not require internet permissions.
*   **No Analytics:** We do not track your usage.
*   **Local Storage:** Conversation history is stored temporarily in RAM and cleared when the app closes.

---

## 🤝 Contributing

Contributions are welcome! If you want to improve the Farsi translation logic, optimize the inference engine, or improve the UI:

1.  Fork the repository.
2.  Create a feature branch (`git checkout -b feature/NewFeature`).
3.  Commit your changes.
4.  Push to the branch.
5.  Open a Pull Request.

---

## 📄 License

This project is distributed under the MIT License. You are free to use, modify, and distribute this software.

---

**Disclaimer:** Performance depends on your device's hardware. Running Large Language Models (LLMs) locally requires a decent amount of RAM (4GB+ recommended) and may consume battery life faster than standard apps.