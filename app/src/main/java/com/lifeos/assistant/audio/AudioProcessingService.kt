package com.lifeos.assistant.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lifeos.assistant.MainActivity
import com.lifeos.assistant.R

/**
 * AudioProcessingService - Foreground service for audio processing
 * 
 * Keeps the app running when processing long audio files
 * or when the app is in the background.
 */
class AudioProcessingService : Service() {

    companion object {
        private const val TAG = "AudioProcessingService"
        private const val CHANNEL_ID = "lifeos_audio_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.lifeos.assistant.STOP_PROCESSING"
    }

    private val binder = LocalBinder()
    private var isProcessing = false

    inner class LocalBinder : Binder() {
        fun getService(): AudioProcessingService = this@AudioProcessingService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProcessing()
                return START_NOT_STICKY
            }
        }

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProcessing()
    }

    /**
     * Start audio processing
     */
    fun startProcessing() {
        isProcessing = true
        updateNotification("Processing audio...")
    }

    /**
     * Stop audio processing
     */
    fun stopProcessing() {
        isProcessing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Check if currently processing
     */
    fun isProcessing(): Boolean = isProcessing

    /**
     * Create notification channel (Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio processing for LifeOs"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create foreground notification
     */
    private fun createNotification(content: String = "LifeOs is listening..."): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AudioProcessingService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeOs Voice Assistant")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_mic, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Update notification text
     */
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}