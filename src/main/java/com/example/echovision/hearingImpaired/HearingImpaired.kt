package com.example.echovision.hearingImpaired

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HearingImpaired : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var audioExecutor: ExecutorService
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var vibrator: Vibrator
    private lateinit var cameraProvider: ProcessCameraProvider
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize executors
        cameraExecutor = Executors.newSingleThreadExecutor()
        audioExecutor = Executors.newSingleThreadExecutor()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Check permissions
        if (checkPermissions()) {
            initializeComponents()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        }

        // Add lifecycle observer
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            // Lifecycle observer implementation would be here
        })
    }

    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                initializeComponents()
                return
            }
        }
        Log.e(TAG, "Permissions not granted")
        finish()
    }

    private fun initializeComponents() {
        // Initialize speech recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        // Initialize camera provider
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Set up the UI
            setContent {
                HearingImpairedApp(
                    cameraExecutor = cameraExecutor,
                    onStartCamera = ::startCamera,
                    onStopCamera = ::stopCamera,
                    onSwitchCamera = ::switchCamera
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCamera(previewView: PreviewView, cameraFacing: Int, detector: SignLanguageDetector) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also {
                it.setAnalyzer(cameraExecutor, detector.getSignLanguageAnalyzer())
            }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()

        try {
            // Unbind any existing camera use cases
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            Log.d(TAG, "Camera started with sign language detection")
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    private fun switchCamera(previewView: PreviewView, newFacing: Int, detector: SignLanguageDetector) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also {
                it.setAnalyzer(cameraExecutor, detector.getSignLanguageAnalyzer())
            }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(newFacing)
            .build()

        try {
            // Unbind any existing camera use cases
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            Log.d(TAG, "Camera switched to ${if (newFacing == CameraSelector.LENS_FACING_FRONT) "front" else "back"}")
        } catch (e: Exception) {
            Log.e(TAG, "Camera switch failed", e)
        }
    }

    private fun stopCamera() {
        if (this::cameraProvider.isInitialized) {
            cameraProvider.unbindAll()
            camera = null
            Log.d(TAG, "Camera stopped")
        }
    }

    class SignLanguageDetector(
        private val context: Context,
        private val executor: ExecutorService,
        val useKiswahili: Boolean,
        private val useDeepSeekApi: Boolean,
        private val deepSeekApiKey: String?
    ) {
        // StateFlow for UI updates
        private val _detectedSignText = MutableStateFlow("Looking for signs...")
        val detectedSignText: StateFlow<String> = _detectedSignText

        private val _isProcessing = MutableStateFlow(false)
        val isProcessing: StateFlow<Boolean> = _isProcessing

        private val _confidence = MutableStateFlow(0.0f)
        val confidence: StateFlow<Float> = _confidence

        private val _debugInfo = MutableStateFlow("")
        val debugInfo: StateFlow<String> = _debugInfo

        private val _edgeOverlayBitmap = MutableStateFlow<Bitmap?>(null)
        val edgeOverlayBitmap: StateFlow<Bitmap?> = _edgeOverlayBitmap

        fun getSignLanguageAnalyzer(): ImageAnalysis.Analyzer {
            return ImageAnalysis.Analyzer { imageProxy ->
                _isProcessing.value = true
                try {
                    // In the decompiled code, this was a placeholder implementation
                    // Real implementation would process the image for sign language detection
                    _detectedSignText.value = if (useKiswahili) "Habari" else "Hello"
                    _confidence.value = 0.95f
                    _debugInfo.value = "Frame processed at ${System.currentTimeMillis()}"
                    _edgeOverlayBitmap.value = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing frame", e)
                    _debugInfo.value = "Error: ${e.message}"
                } finally {
                    _isProcessing.value = false
                    imageProxy.close()
                }
            }
        }

        fun shutdown() {
            Log.d(TAG, "SignLanguageDetector shutdown")
        }
    }

    @Composable
    fun HearingImpairedApp(
        cameraExecutor: ExecutorService,
        onStartCamera: (PreviewView, Int, SignLanguageDetector) -> Unit,
        onStopCamera: () -> Unit,
        onSwitchCamera: (PreviewView, Int, SignLanguageDetector) -> Unit
    ) {
        // State variables
        var isDarkMode by remember { mutableStateOf(false) }
        var currentMode by remember { mutableStateOf("TTS") }
        var isCameraActive by remember { mutableStateOf(false) }
        var isListening by remember { mutableStateOf(false) }
        var transcribedTextList by remember { mutableStateOf<List<String>>(emptyList()) }
        var languageMode by remember { mutableStateOf("English") }
        var cameraFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }

        // Get access to Context and LifecycleOwner
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val coroutineScope = rememberCoroutineScope()

        // Create the sign language detector
        val signLanguageDetector = remember {
            SignLanguageDetector(
                context,
                cameraExecutor,
                useKiswahili = languageMode == "Kiswahili" || languageMode == "English-Kiswahili",
                useDeepSeekApi = false,
                deepSeekApiKey = null
            )
        }

        // Collect state flows
        val detectedSignText by signLanguageDetector.detectedSignText.collectAsState()
        val confidence by signLanguageDetector.confidence.collectAsState()
        val isProcessing by signLanguageDetector.isProcessing.collectAsState()
        val edgeOverlayBitmap by signLanguageDetector.edgeOverlayBitmap.collectAsState()
        val debugInfo by signLanguageDetector.debugInfo.collectAsState()

        // Set up translator
        val translator = remember {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.SWAHILI)
                .build()
            Translation.getClient(options)
        }

        var translatedSignText by remember { mutableStateOf("") }

        // Effect to download translation model
        LaunchedEffect(Unit) {
            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    Log.d(TAG, "Translation model downloaded successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error downloading translation model", e)
                }
        }

        // Effect to translate detected sign text
        LaunchedEffect(detectedSignText, languageMode) {
            if (languageMode == "English-Kiswahili" && detectedSignText != "Looking for signs...") {
                translator.translate(detectedSignText)
                    .addOnSuccessListener { translatedText ->
                        translatedSignText = translatedText
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Translation failed", e)
                    }
            }
        }

        // Effect to handle speech recognition
        LaunchedEffect(isListening) {
            if (isListening) {
                startSpeechRecognition(
                    speechRecognizer,
                    transcribedTextList,
                    languageMode
                ) { newList ->
                    transcribedTextList = newList
                    isListening = false
                    vibrate(100)
                }
            }
        }

        // Material theming
        MaterialTheme(
            colorScheme = if (isDarkMode)
                darkColorScheme()
            else
                lightColorScheme()
        ) {
            // The UI components would be defined here
            // This would include PreviewView, controls, and feedback displays
        }
    }

    private fun startSpeechRecognition(
        recognizer: SpeechRecognizer,
        currentList: List<String>,
        languageMode: String,
        callback: (List<String>) -> Unit
    ) {
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,
                if (languageMode == "Kiswahili") TranslateLanguage.SWAHILI else "en-US")
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            // Recognition listener implementation would handle speech recognition results
            // and update the transcribed text list
        })

        recognizer.startListening(intent)
    }

    private fun vibrate(durationMs: Long) {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    companion object {
        private const val TAG = "HearingImpaired"
        private const val PERMISSIONS_REQUEST_CODE = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.VIBRATE
        )
    }
}