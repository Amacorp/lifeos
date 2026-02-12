package com.lifeos.assistant

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.lifeos.assistant.data.LocalDatabase
import com.lifeos.assistant.data.ModelRepository
import com.lifeos.assistant.tts.OfflineTTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * LifeOs Application Class
 * 
 * Initializes core components and manages application lifecycle.
 * Optimized for low memory environments.
 */
class LifeOsApp : Application() {

    companion object {
        private const val TAG = "LifeOsApp"
        
        // DataStore for preferences
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lifeos_settings")
        
        @Volatile
        private var instance: LifeOsApp? = null
        
        fun getInstance(): LifeOsApp? = instance
    }

    // Application scope for coroutines
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Lazy initialization of components
    val database by lazy { LocalDatabase.getDatabase(this) }
    val modelRepository by lazy { ModelRepository(this) }
    lateinit var tts: OfflineTTS
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.i(TAG, "LifeOs initializing...")
        
        // Initialize TTS
        initializeTTS()
        
        // Log memory info
        logMemoryInfo()
        
        // Clean up old cache on startup
        applicationScope.launch {
            cleanupCache()
        }
        
        Log.i(TAG, "LifeOs initialized successfully")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory detected! Cleaning up...")
        
        // Release non-critical resources
        applicationScope.launch {
            modelRepository.unloadCurrentModel()
            System.gc()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "onTrimMemory called with level: $level")
        
        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE -> {
                // Aggressive cleanup
                applicationScope.launch {
                    modelRepository.unloadCurrentModel()
                    System.gc()
                }
            }
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_MODERATE -> {
                // Moderate cleanup
                System.gc()
            }
        }
    }

    private fun initializeTTS() {
        tts = OfflineTTS(this)
    }

    private fun logMemoryInfo() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory
        
        Log.i(TAG, "Memory Info:")
        Log.i(TAG, "  Max: ${maxMemory}MB")
        Log.i(TAG, "  Total: ${totalMemory}MB")
        Log.i(TAG, "  Free: ${freeMemory}MB")
        Log.i(TAG, "  Used: ${usedMemory}MB")
    }

    private suspend fun cleanupCache() {
        try {
            val cacheDir = cacheDir
            val files = cacheDir.listFiles()
            var totalSize = 0L
            
            files?.forEach { file ->
                if (file.isFile && file.name.startsWith("audio_")) {
                    // Delete old audio cache files older than 24 hours
                    if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                        file.delete()
                    } else {
                        totalSize += file.length()
                    }
                }
            }
            
            Log.d(TAG, "Cache cleanup completed. Current cache size: ${totalSize / 1024}KB")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning cache", e)
        }
    }
}