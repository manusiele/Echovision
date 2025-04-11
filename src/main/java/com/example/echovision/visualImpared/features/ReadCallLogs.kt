package com.example.echovision.visualImpared.features

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.cli.HelpFormatter

class ReadCallLogs(
    private val context: Context,
    private val textToSpeech: TextToSpeech
) {

    fun readCallLogs() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == 0) {
            try {
                val cursor = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    null,
                    null,
                    null,
                    "date DESC"
                )

                if (cursor != null) {
                    cursor.use { c ->
                        if (c.moveToFirst()) {
                            val numberIndex = c.getColumnIndex("number")
                            val typeIndex = c.getColumnIndex("type")
                            val nameIndex = c.getColumnIndex("name")

                            if (numberIndex != -1 && typeIndex != -1) {
                                val number = c.getString(numberIndex)
                                val callType = when (c.getInt(typeIndex)) {
                                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                                    CallLog.Calls.MISSED_TYPE -> "missed"
                                    else -> "unknown"
                                }

                                var contactName = c.getString(nameIndex)
                                if (contactName == null) {
                                    contactName = "Unknown"
                                }

                                val digitString = number?.let { numberToDigits(it) }

                                speak("Last call was a $callType call from $contactName, number $digitString")
                            } else {
                                speak("Call log data is unavailable")
                            }
                        } else {
                            speak("No call logs found")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ReadCallLogs", "Error reading call logs", e)
                speak("Error reading call logs")
            }
        } else {
            speak("I don't have permission to access call logs")
        }
    }

    fun callContact(contactName: String) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == 0) {
            try {
                val contactId = getContactId(contactName)
                if (contactId != null) {
                    val phoneNumber = getPhoneNumber(contactId)
                    if (phoneNumber != null) {
                        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    } else {
                        speak("Phone number not found for $contactName")
                    }
                } else {
                    speak("Contact $contactName not found")
                }
            } catch (e: Exception) {
                Log.e("ReadCallLogs", "Error calling contact", e)
                speak("Error calling contact")
            }
        } else {
            speak("I don't have permission to access contacts")
        }
    }

    private fun getContactId(contactName: String): String? {
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf("_id")
        val selection = "display_name LIKE ?"
        val selectionArgs = arrayOf("%$contactName%")
        var contactId: String? = null

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex("_id")
                contactId = cursor.getString(idIndex)
            }
        }
        return contactId
    }

    private fun getPhoneNumber(contactId: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf("data1")
        val selection = "contact_id = ?"
        val selectionArgs = arrayOf(contactId)
        var phoneNumber: String? = null

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex("data1")
                phoneNumber = cursor.getString(numberIndex)
            }
        }
        return phoneNumber
    }

    fun extractContactName(command: String): String {
        val words = command.split(" ")
        return if (words.size >= 2) {
            words.last()
        } else {
            ""
        }
    }

    private fun numberToDigits(number: String): String {
        val stringBuilder = StringBuilder()
        for (i in number.indices) {
            val c = number[i]
            if (c.isDigit()) {
                stringBuilder.append("$c, ")
            } else if (c == '+') {
                stringBuilder.append("plus, ")
            }
        }
        return stringBuilder.toString()
    }

    private fun speak(message: String) {
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}