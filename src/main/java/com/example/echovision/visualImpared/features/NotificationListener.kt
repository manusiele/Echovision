package com.example.echovision.visualImpared.features

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
import java.util.*
import kotlin.math.min

/**
 * A notification listener service that reads notifications aloud for visually impaired users.
 * This service captures incoming system notifications and converts them to speech.
 */
class NotificationListener : NotificationListenerService() {

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private val processedNotifications = HashSet<String>()

    override fun onCreate() {
        super.onCreate()
        initializeTextToSpeech()
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                when (textToSpeech?.setLanguage(Locale.getDefault())) {
                    TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                        Log.e(TAG, "Language not supported")
                    }
                    else -> {
                        isTtsInitialized = true
                    }
                }
            } else {
                Log.e(TAG, "TTS Initialization failed")
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isTtsInitialized) {
            Log.w(TAG, "TTS not initialized")
            return
        }

        val notificationKey = sbn.key
        if (!processedNotifications.contains(notificationKey)) {
            processedNotifications.add(notificationKey)

            val packageName = sbn.packageName
            val appName = getAppName(packageName)
            val message = extractNotificationDetails(sbn.notification)

            if (message != null) {
                speakOut("Notification from $appName: $message")
            } else {
                speakOut("You have a new notification from $appName")
            }
        }
    }

    private fun extractNotificationDetails(notification: Notification): String? {
        val extras = notification.extras
        val title = extras.getString(NotificationCompat.EXTRA_TITLE)
        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)

        if (title == null || text == null) {
            return null
        }

        val truncatedText = text.toString().substring(0, min(30, text.length))
        return "$title: $truncatedText"
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app name", e)
            packageName
        }
    }

    private fun speakOut(text: String) {
        if (isTtsInitialized) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        processedNotifications.remove(sbn.key)
    }

    override fun onDestroy() {
        if (isTtsInitialized) {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotificationListener"

        /**
         * Checks if notification listener service is enabled for this application
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
}