package com.example.echovision.visualImpaired.features

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.min

/**
 * An optimized notification listener service that reads notifications aloud for visually impaired users.
 *
 * Key optimizations:
 * - Uses a fixed-size LinkedHashSet to prevent memory leaks from cached notification keys.
 * - Offloads package name resolution and TTS calls to a background coroutine to prevent ANRs.
 * - Implements a more robust notification content parser.
 * - Uses the TextToSpeech.OnInitListener for cleaner lifecycle management.
 * - Includes a placeholder for user-configurable app filtering.
 */
class NotificationListener : NotificationListenerService(), TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // --- Memory Optimization ---
    // A fixed-size cache to prevent memory leaks. It automatically evicts the oldest entry.
    private val maxCachedNotifications = 100
    private val processedNotifications = object : LinkedHashSet<String>(maxCachedNotifications) {
        override fun add(element: String): Boolean {
            if (size >= maxCachedNotifications) {
                val iterator = iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            return super.add(element)
        }
    }

    // --- Asynchronous Operation Optimization ---
    // A coroutine scope for background operations tied to the service's lifecycle.
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "NotificationListener"

        /**
         * Checks if the notification listener service is enabled.
         */
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            val componentName = ComponentName(context, NotificationListener::class.java)
            return flat?.contains(componentName.flattenToString()) ?: false
        }
    }

    // --- Resource Management Optimization ---
    override fun onCreate() {
        super.onCreate()
        // Initialize TTS using the OnInitListener callback pattern
        textToSpeech = TextToSpeech(this, this)
    }

    // This is the callback from TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            isTtsInitialized = when (result) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.e(TAG, "TTS Language not supported.")
                    false
                }
                else -> {
                    Log.d(TAG, "TTS Initialized successfully.")
                    true
                }
            }
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
            isTtsInitialized = false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Early exit if TTS is not ready or the notification is not allowed
        if (!isTtsInitialized || !isNotificationAllowed(sbn.packageName)) {
            return
        }

        // .add() returns false if the element was already present, true if it was added.
        if (processedNotifications.add(sbn.key)) {
            // Offload the work to a background coroutine
            serviceScope.launch {
                val appName = getAppName(sbn.packageName)
                val message = extractNotificationDetails(sbn.notification)

                val textToSpeak = if (message != null) {
                    "Notification from $appName: $message"
                } else {
                    "You have a new notification from $appName"
                }
                speakOut(textToSpeak)
            }
        }
    }

    // --- Robust Parsing Optimization ---
    private fun extractNotificationDetails(notification: Notification): String? {
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        
        // Prioritize more detailed content
        val messageContent = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()

        return if (title != null && !messageContent.isNullOrBlank()) {
            // Truncate only if the content is excessively long
            val truncatedMessage = if (messageContent.length > 200) {
                "${messageContent.take(200)}..."
            } else {
                messageContent
            }
            "$title: $truncatedMessage"
        } else {
            null
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app name for $packageName", e)
            packageName
        }
    }

    private fun speakOut(text: String) {
        // The TTS speak method is thread-safe, but we ensure it's called only when initialized.
        if (isTtsInitialized) {
            // Use a unique utterance ID for better control
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Clean up the cache when a notification is removed
        processedNotifications.remove(sbn.key)
    }

    // --- User Control & Battery Life Optimization (Placeholder) ---
    /**
     * Checks if notifications from a specific package are allowed to be read.
     * This should be connected to a user settings screen (e.g., using SharedPreferences).
     * Currently, it allows all notifications.
     */
    private fun isNotificationAllowed(packageName: String): Boolean {
        // Example: Check a user preference
        // val prefs = getSharedPreferences("NotificationPrefs", MODE_PRIVATE)
        // return prefs.getBoolean("enabled_$packageName", true)
        return true // Default to allowing all notifications for now
    }

    override fun onDestroy() {
        // Cancel all background coroutines
        serviceScope.cancel()
        
        // Clean up TTS resources
        if (isTtsInitialized) {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        super.onDestroy()
    }
}
