package com.example.echovision.visualImpaired.features

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette

class ColorDetection(private val context: Context) {

    fun colorDetection() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageCapture = ImageCapture.Builder().build()
            // Example: Capture an image and analyze colors
            imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    Palette.from(bitmap).generate { palette ->
                        val dominantColor = palette?.getDominantColor(0x000000)
                        val colorName = getColorName(dominantColor ?: 0) // Custom method to map color to name
                        Toast.makeText(context, "Detected color: $colorName", Toast.LENGTH_SHORT).show()
                    }
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(context, "Failed to capture image", Toast.LENGTH_SHORT).show()
                }
            })
        }, ContextCompat.getMainExecutor(context))
    }

    private fun getColorName(color: Int): String {
        // Simplified color mapping (could be expanded)
        return when {
            color shr 16 and 0xFF > 200 -> "Red"
            color shr 8 and 0xFF > 200 -> "Green"
            color and 0xFF > 200 -> "Blue"
            else -> "Unknown"
        }
    }
}