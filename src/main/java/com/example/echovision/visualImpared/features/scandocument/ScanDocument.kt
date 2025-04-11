package com.example.echovision.visualImpared.features.scandocument

import android.net.Uri

data class ScanDocument(
    val text: String = "",
    val imageUri: Uri? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false,
    val errorMessage: String? = null
)