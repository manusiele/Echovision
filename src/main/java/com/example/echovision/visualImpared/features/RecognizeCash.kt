package com.example.echovision.visualImpared.features

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RecognizeCash(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewFinder: PreviewView,
    private val onCurrencyDetected: (String) -> Unit,
    private var onCameraStartRequested: Boolean
) {
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private var speechResult: String? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    init {
        initializeTTS()
        initializeSpeechRecognizer()
    }

    fun requestCameraStart() {
        onCameraStartRequested = true
    }

    fun recognizeCash() {
        Log.d("RecognizeCash", "recognizeCash called")
        if (camera == null) {
            Log.d("RecognizeCash", "Camera is not initialized, opening camera")
            startCamera()
        } else {
            Log.d("RecognizeCash", "Camera is initialized, capturing image")
            captureImage()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun startCamera() {
        Log.d("RecognizeCash", "Starting camera...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder().build()

                cameraProvider?.unbindAll()

                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture!!
                )

                Log.d("RecognizeCash", "Camera bound successfully.")
            } catch (e: Exception) {
                Log.e("RecognizeCash", "Camera binding failed: ${e.message}", e)
                speak("Camera binding failed.")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun captureImage() {
        Log.d("RecognizeCash", "Capturing image...")
        val imageCaptureExecutor = Executors.newSingleThreadExecutor().also {
            imageCapture?.takePicture(
                it,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        super.onCaptureSuccess(image)
                        Log.d("RecognizeCash", "Image captured successfully")

                        val bitmap = image.toBitmap()
                        image.close()

                        if (bitmap != null) {
                            val imageUri = saveImageToGallery(bitmap)
                            launchGoogleLens(imageUri) { result ->
                                onCurrencyDetected(result)
                            }
                        } else {
                            Log.e("RecognizeCash", "Failed to convert image to bitmap")
                            speak("Failed to process the captured image.")
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        super.onError(exception)
                        Log.e("RecognizeCash", "Image capture failed: ${exception.message}", exception)
                        speak("Image capture failed.")
                    }
                }
            )
        }

        imageCaptureExecutor.shutdown()
        try {
            if (!imageCaptureExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                Log.w("RecognizeCash", "Image capture executor did not terminate in 5 seconds.")
            }
        } catch (e: InterruptedException) {
            Log.e("RecognizeCash", "Image capture executor termination interrupted: ${e.message}", e)
        }
    }

    private fun initializeTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                when (textToSpeech.setLanguage(Locale.US)) {
                    TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                        Log.e("RecognizeCash", "TTS Language is not supported")
                    }
                }
            } else {
                Log.e("RecognizeCash", "TTS Initialization failed")
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    speechResult = matches[0]
                    speechResult?.let { result ->
                        Log.d("RecognizeCash", "Speech recognized: $result")
                        onCurrencyDetected(result)
                    }
                }
            }

            override fun onError(error: Int) {
                Log.e("RecognizeCash", "Speech recognition error: $error")
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        Log.d("RecognizeCash", "toBitmap called")
        val planes = this.planes
        if (planes.isEmpty()) {
            Log.e("RecognizeCash", "Image planes are empty")
            return null
        }

        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun saveImageToGallery(bitmap: Bitmap?): Uri? {
        if (bitmap == null) return null

        val timestamp = System.currentTimeMillis()
        val fileName = "currency_image_$timestamp.jpg"
        val outputStream = kotlin.jvm.internal.Ref.ObjectRef<java.io.OutputStream>()
        var uri: Uri? = null

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentResolver = context.contentResolver ?: return null

            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }

            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            outputStream.element = uri?.let { contentResolver.openOutputStream(it) }
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val imageFile = File(picturesDir, fileName)
            uri = Uri.fromFile(imageFile)
            outputStream.element = FileOutputStream(imageFile)
        }

        outputStream.element?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }

        return uri
    }

    private fun launchGoogleLens(imageUri: Uri?, onResult: (String) -> Unit) {
        if (imageUri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = imageUri
                setPackage("com.google.android.apps.photos.lens")
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                onResult("Currency Result Simulated")
            } else {
                speak("Google Lens is not installed.")
            }
        }
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun release() {
        try {
            textToSpeech.shutdown()
            speechRecognizer.destroy()
            cameraProvider?.unbindAll()
            Log.d("RecognizeCash", "Resources Released")
        } catch (e: Exception) {
            Log.e("RecognizeCash", "Error releasing resources: ${e.message}")
        }
    }
}