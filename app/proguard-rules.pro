# LifeOs - ProGuard Rules for Release Build
# Add project specific ProGuard rules here

# Keep Application class
-keep class com.lifeos.assistant.LifeOsApp { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Whisper JNI
-keep class com.lifeos.assistant.audio.WhisperEngine { *; }
-keep class com.lifeos.assistant.audio.WhisperEngine$* { *; }

# TensorFlow Lite
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Kotlin
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# Prevent R8 from leaving data object constructors always public
-keepclassmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# General
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service