package com.example.echovision.visualImpared.features

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.echovision.visualImpared.VisualImpairmentActivity
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePartBody
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.binary.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import kotlin.text.Charsets

class EmailReader(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : TextToSpeech.OnInitListener, DefaultLifecycleObserver {

    companion object {
        const val TEXT_PLAIN_MIME_TYPE = "text/plain"
    }

    private val TAG = "EmailReader"
    private val intent = Intent(context, VisualImpairmentActivity::class.java)
    private var textToSpeech: TextToSpeech? = null
    private var credential: GoogleAccountCredential? = null
    private var gmail: Gmail? = null
    private var isInitialized = false

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        textToSpeech = TextToSpeech(context, this)

        if (context is AppCompatActivity) {
            context.lifecycle.addObserver(this)
        } else {
            Log.e(TAG, "Context is not an AppCompatActivity.")
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Initialization work in IO thread
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "The Language not supported!")
                return
            }
            isInitialized = true
            login()
        } else {
            Log.e(TAG, "Initialization Failed!")
        }
    }

    private fun login() {
        speak("Logging in...")
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Login implementation
        }
    }

    private fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun fetchGoogleAccount(): Account? {
        val accounts = AccountManager.get(context).getAccountsByType("com.google")
        return accounts.firstOrNull()
    }

    fun readAndSpeakEmails() {
        speak("Reading emails")
        CoroutineScope(Dispatchers.IO).launch {
            // Implementation to read and speak emails
        }
    }

    fun readEmails() {
        CoroutineScope(Dispatchers.IO).launch {
            // Implementation to read emails
        }
    }

    private suspend fun readEmailsFromGmail(): List<EmailMessage> = withContext(Dispatchers.IO) {
        // Implementation to fetch emails from Gmail
        emptyList() // Placeholder return
    }

    private fun extractEmailMessage(message: Message): EmailMessage {
        val payload = message.getPayload()

        val fromHeader = payload.getHeaders().find { it.getName() == "From" }
        val from = fromHeader?.getValue() ?: "Unknown"

        val subjectHeader = payload.getHeaders().find { it.getName() == "Subject" }
        val subject = subjectHeader?.getValue() ?: "No Subject"

        val sentDate = Date(message.getInternalDate())

        val content = getMessageContent(message)

        return EmailMessage(from, subject, sentDate, content)
    }

    private fun getMessageContent(message: Message): String {
        val body = message.getPayload().getBody()
        if (body == null || body.getData() == null) {
            return "No content found"
        }
        return String(Base64.decodeBase64(body.getData()), Charsets.UTF_8)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        textToSpeech?.shutdown()
        super.onDestroy(owner)
    }

    data class EmailMessage(
        val from: String,
        val subject: String,
        val sentDate: Date,
        val content: String
    )
}