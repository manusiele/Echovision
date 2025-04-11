package com.example.echovision.visualImpared

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.echovision.CameraManager
import com.example.echovision.R
import com.example.echovision.visualImpared.features.BatchScan
import com.example.echovision.visualImpaired.features.ColorDetection
import com.example.echovision.visualImpaired.features.DescribeScene
import com.example.echovision.visualImpaired.features.DetectLight
import com.example.echovision.visualImpaired.features.EmailReader
import com.example.echovision.visualImpaired.features.ExploreArea
import com.example.echovision.visualImpaired.features.FindObject
import com.example.echovision.visualImpaired.features.FindPeople
import com.example.echovision.visualImpaired.features.NotificationListener
import com.example.echovision.visualImpaired.features.ReadCallLogs
import com.example.echovision.visualImpaired.features.RecognizeCash
import com.example.echovision.visualImpaired.features.scandocument.ScanDocument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

class VisualImpairmentActivity : ComponentActivity(), TextToSpeech.OnInitListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private val REQUIRED_PERMISSIONS = arrayOf(
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_CALL_LOG",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.CAMERA",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.CALL_PHONE"
    )

    private var onCameraStartRequested = false
    private var recognizeCashInstance: RecognizeCash? = null
    private val _shouldStartDocumentScanning = MutableLiveData(false)
    private lateinit var textRecognizer: TextRecognizer
    private var currentImageUri: Uri? = null
    private lateinit var cameraLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var emailReader: EmailReader
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var previewView: PreviewView
    private var isSpeechRecognizerReady = true
    private var pendingCommand: String? = null
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private lateinit var cameraExecutor: java.util.concurrent.ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraManager: CameraManager
    private var capturedBitmap: Bitmap? = null
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private lateinit var documentScannerLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        handlePermissionResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        emailReader = EmailReader(this, this)
        Log.d("VisualImpairment", "onCreate() called")
        textToSpeech = TextToSpeech(this, this)
        setupTextToSpeech()
        requestPermissions()
        cameraManager = CameraManager(this)
        previewView = PreviewView(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        initializeSpeechRecognition()
        checkNotificationAccessAndPrompt()
        onSpeechRecognizerReady = { startListening() }
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && currentImageUri != null) {
                processImage(currentImageUri!!)
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            VisualImpairmentActivityUI(_shouldStartDocumentScanning, this)
        }
    }

    @Composable
    fun VisualImpairmentActivityUI(shouldStartDocumentScanning: MutableLiveData<Boolean>, context: Context) {
        val shouldStart by shouldStartDocumentScanning.observeAsState(initial = false)
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewView = remember { PreviewView(context) }
        val documentScanner = remember { DocumentScannerScreen(previewView, lifecycleOwner, context) }

        documentScanner.DocumentScannerScreenUI(
            isVisible = shouldStart,
            onClose = { shouldStartDocumentScanning.value = false }
        )
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun CameraScreen(
        command: String,
        lifecycleOwner: LifecycleOwner,
        onCloseCamera: () -> Unit,
        isCameraVisible: Boolean,
        cameraManager: CameraManager,
        context: Context,
        shouldStartDocumentScanning: MutableState<Boolean>
    ) {
        val cameraPermissionState = rememberPermissionState("android.permission.CAMERA")
        LaunchedEffect(Unit) {
            if (!cameraPermissionState.status.isGranted) {
                cameraPermissionState.launchPermissionRequest()
            }
        }
        LaunchedEffect(shouldStartDocumentScanning.value) {
            if (shouldStartDocumentScanning.value) startDocumentScanner()
        }

        if (cameraPermissionState.status.isGranted) {
            if (isCameraVisible) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    AndroidView(
                        factory = { PreviewView(context).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) } },
                        update = { cameraManager.startCamera(it, lifecycleOwner) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission is required.", color = Color.Red, fontSize = 18.sp)
            }
        }
    }

    @Composable
    fun MainScreenContent(
        textToSpeech: TextToSpeech,
        isCameraVisible: MutableState<Boolean>,
        isLottieVisible: MutableState<Boolean>,
        shouldStartDocumentScanning: MutableState<Boolean>,
        isProcessing: MutableState<Boolean>,
        command: MutableState<String>,
        cameraManager: CameraManager,
        requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
        lifecycleOwner: LifecycleOwner,
        statusText: MutableState<String>
    ) {
        val context = LocalContext.current
        val commandAliases = mapOf("scan document" to "scan document", "read text" to "scan document")
        val normalizedCommand = commandAliases[command.value.toLowerCase(Locale.ROOT)] ?: command.value.toLowerCase(Locale.ROOT)
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.voice))

        Scaffold { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (isLottieVisible.value) {
                    LottieAnimation(
                        composition = composition,
                        modifier = Modifier.size(600.dp),
                        iterations = Int.MAX_VALUE
                    )
                }
                // Additional UI logic can be added here if needed
            }
        }
    }

    @Composable
    fun CallDocumentScannerScreen(shouldStartDocumentScanning: MutableLiveData<Boolean>) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val shouldStart by shouldStartDocumentScanning.observeAsState(initial = false)
        val previewView = PreviewView(context)
        val documentScanner = remember { DocumentScannerScreen(previewView, lifecycleOwner, context) }

        documentScanner.DocumentScannerScreenUI(
            isVisible = shouldStart,
            onClose = { shouldStartDocumentScanning.value = false }
        )
    }

    private fun requestPermissions() {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter { ContextCompat.checkSelfPermission(this, it) != 0 }.toTypedArray()
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys.toList()
        if (deniedPermissions.isEmpty()) {
            initializeSpeechRecognition()
            pendingCommand?.let { processCommand(this, it) }
            pendingCommand = null
        } else {
            Toast.makeText(this, "Permissions denied: ${deniedPermissions.joinToString()}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processDocumentScannerResult(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            textToSpeech.speak("No image available", TextToSpeech.QUEUE_FLUSH, null, null)
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText -> handleRecognitionResult(visionText.text) }
            .addOnFailureListener { handleRecognitionFailure(it) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun handleRecognitionResult(text: String) {
        if (text.isNotEmpty()) {
            textToSpeech.speak("Document scanned successfully", TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            textToSpeech.speak("No document found", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun handleRecognitionFailure(e: Exception) {
        textToSpeech.speak("Failed to recognize text", TextToSpeech.QUEUE_FLUSH, null, null)
        Log.e("VisualImpairment", "Text recognition failed", e)
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(arrayOf("android.permission.CAMERA"))
    }

    private fun startDocumentScanner() {
        if (ContextCompat.checkSelfPermission(this, "android.permission.CAMERA") != 0) {
            requestCameraPermission()
            return
        }
        val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { processDocumentScannerResult(it) }
        ProcessCameraProvider.getInstance(this).addListener({
            cameraProvider = it.get() as ProcessCameraProvider
            cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
    }

    private fun stopCamera() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }

    private fun checkNotificationAccessAndPrompt() {
        if (!NotificationListener.isNotificationListenerEnabled(this)) {
            showNotificationAccessDialog()
        }
    }

    private fun showNotificationAccessDialog() {
        Toast.makeText(this, "Please enable notification access for this app.", Toast.LENGTH_LONG).show()
        openNotificationAccessSettings()
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    private fun startListening() {
        if (isSpeechRecognizerReady && !isListening) {
            statusText.value = "Activating..."
            isLottieVisible.value = true
            try {
                speechRecognizer.startListening(speechRecognizerIntent)
                isListening = true
            } catch (e: Exception) {
                Log.e("VisualImpairment", "Error starting speech recognition", e)
                Toast.makeText(this, "Error starting speech", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processImage(imageUri: Uri) {
        try {
            val image = InputImage.fromBitmap(MediaStore.Images.Media.getBitmap(contentResolver, imageUri), 0)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    ScanDocument(visionText.text, imageUri, 0, true)
                    textToSpeech.speak("Document scanned successfully", TextToSpeech.QUEUE_FLUSH, null, null)
                }
                .addOnFailureListener { e ->
                    ScanDocument(null, imageUri, 0, false, e.message)
                    textToSpeech.speak("Error scanning document", TextToSpeech.QUEUE_FLUSH, null, null)
                }
        } catch (e: IOException) {
            textToSpeech.speak("Error scanning document", TextToSpeech.QUEUE_FLUSH, null, null)
            Log.e("VisualImpairment", "Error scanning document: ${e.message}")
        }
    }

    private fun initializeSpeechRecognition() {
        if (speechRecognizer != null) return
        if (ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO") != 0) {
            ActivityCompat.requestPermissions(this, arrayOf("android.permission.RECORD_AUDIO"), 105)
            return
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { isSpeechRecognizerReady = true }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) { isListening = false }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { processCommand(this@VisualImpairmentActivity, it) }
                        isListening = false
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            speechRecognizerIntent = Intent("android.speech.action.RECOGNIZE_SPEECH").apply {
                putExtra("android.speech.extra.LANGUAGE_MODEL", "free_form")
                putExtra("android.speech.extra.LANGUAGE", Locale.getDefault())
            }
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun processCommand(context: Context, command: String) {
        this.command.value = command
        val commandAliases = mapOf("scan document" to "scan document", "read text" to "scan document", "read document" to "scan document")
        val normalizedCommand = commandAliases[command.toLowerCase(Locale.ROOT)] ?: command.toLowerCase(Locale.ROOT)

        when {
            "open camera" in normalizedCommand -> {
                if (ContextCompat.checkSelfPermission(context, "android.permission.CAMERA") != 0) {
                    ActivityCompat.requestPermissions(this, arrayOf("android.permission.CAMERA"), 104)
                } else {
                    isCameraVisible.value = true
                }
            }
            "email" in normalizedCommand -> emailReader.readEmails()
            "close camera" in normalizedCommand -> {
                textToSpeech.speak("Closing camera", TextToSpeech.QUEUE_FLUSH, null, null)
                isCameraVisible.value = false
                isLottieVisible.value = true
            }
            "document" in normalizedCommand || "read text" in normalizedCommand -> {
                textToSpeech.speak("Scanning document", TextToSpeech.QUEUE_FLUSH, null, null)
                _shouldStartDocumentScanning.value = true
                isCameraVisible.value = true
            }
            "call" in normalizedCommand || "check recent call logs" in normalizedCommand -> {
                val permissions = listOf("android.permission.READ_CALL_LOG", "android.permission.READ_CONTACTS", "android.permission.CALL_PHONE")
                    .filter { ContextCompat.checkSelfPermission(context, it) != 0 }
                if (permissions.isNotEmpty()) {
                    requestPermissionLauncher.launch(permissions.toTypedArray())
                } else {
                    textToSpeech.speak("Checking call logs", TextToSpeech.QUEUE_FLUSH, null, null)
                    ReadCallLogs(context, textToSpeech).apply {
                        val contactName = extractContactName(normalizedCommand)
                        if (contactName.isNotEmpty()) callContact(contactName) else readCallLogs()
                    }
                }
            }
            "describe" in normalizedCommand -> {
                textToSpeech.speak("describing of your current environment", TextToSpeech.QUEUE_FLUSH, null, null)
                isCameraVisible.value = true
                isLottieVisible.value = false
                cameraManager.focusAndCapture(previewView)
                cameraManager.setOnImageCapturedListener { uri ->
                    val bitmap = uriToBitmap(uri)
                    if (bitmap != null) {
                        DescribeScene(this).describeScene(bitmap) { description ->
                            textToSpeech.speak(description, TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                    }
                    cameraManager.setOnImageCapturedListener(null)
                }
            }
            "cash" in normalizedCommand || "money" in normalizedCommand || "currency" in normalizedCommand -> {
                textToSpeech.speak("Recognizing cash", TextToSpeech.QUEUE_FLUSH, null, null)
                if (recognizeCashInstance == null) {
                    recognizeCashInstance = RecognizeCash(this, this, previewView) { currency ->
                        textToSpeech.speak("Currency: $currency. Cash recognition complete.", TextToSpeech.QUEUE_FLUSH, null, null)
                        isCameraVisible.value = false
                        recognizeCashInstance?.release()
                        recognizeCashInstance = null
                    }
                    recognizeCashInstance?.recognizeCash()
                } else {
                    textToSpeech.speak("Cash recognition is already in progress.", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
            "find object" in normalizedCommand -> {
                textToSpeech.speak("Choose the object to search", TextToSpeech.QUEUE_FLUSH, null, null)
                isCameraVisible.value = true
                isLottieVisible.value = false
                FindObject(context)
            }
            "light" in normalizedCommand -> {
                textToSpeech.speak("Detecting light", TextToSpeech.QUEUE_FLUSH, null, null)
                isCameraVisible.value = true
                isLottieVisible.value = false
                DetectLight(context)
            }
            "color" in normalizedCommand -> {
                textToSpeech.speak("Performing color detection", TextToSpeech.QUEUE_FLUSH, null, null)
                isCameraVisible.value = true
                isLottieVisible.value = false
                ColorDetection(context)
            }
            "batch scan" in normalizedCommand -> {
                textToSpeech.speak("Scanning multiple items", TextToSpeech.QUEUE_FLUSH, null, null)
                isCameraVisible.value = true
                isLottieVisible.value = false
                BatchScan(context)
            }
            "find people" in normalizedCommand -> {
                textToSpeech.speak("Finding people", TextToSpeech.QUEUE_FLUSH, null, null)
                isCameraVisible.value = true
                isLottieVisible.value = false
                FindPeople(context)
            }
            "explore" in normalizedCommand -> {
                textToSpeech.speak("Exploring the area", TextToSpeech.QUEUE_FLUSH, null, null)
                isCameraVisible.value = true
                isLottieVisible.value = false
                ExploreArea(context)
            }
            "help" in normalizedCommand -> {
                val helpText = """
                    Here are the available commands:
                    1. Describe Your Surroundings: Get a clear description of your current environment.
                    2. Find People or Objects: Detect people or specific objects in your surroundings.
                    3. Recognize Cash: Identify and differentiate between different bills.
                    4. Color Detection: Identify the colors around you.
                    5. Detect Light Levels: Check how bright or dim your environment is.
                    6. Read and Extract Text from Documents: Scan a document and extract the text.
                    7. Check Recent Call Logs: Review your recent call history.
                    8. Scan Cash: To know the amount of cash you have.
                    9. Batch Scan: Scan multiple items at once in one command.
                    10. Read Email: The app can read aloud your inbox, draft, and outbox emails.
                    11. Explore the area: Get a brief description of your surroundings.
                """.trimIndent()
                textToSpeech.speak(helpText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
            else -> textToSpeech.speak("Sorry, I don't understand that command. Try saying help for available commands.", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            when (textToSpeech.setLanguage(Locale.US)) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> Log.e("TextToSpeech", "Language data missing or not supported")
                else -> {
                    textToSpeech.setSpeechRate(1.0f)
                    getAvailableVoices()
                }
            }
        } else {
            Log.e("TextToSpeech", "Initialization failed")
        }
    }

    private fun getAvailableVoices() {
        val voices = textToSpeech.voices ?: run {
            Log.e("TTS", "No voices available")
            return
        }
        voices.forEach { voice ->
            Log.d("TTS", "Voice: ${voice.name}, Locale: ${voice.locale}, Quality: ${voice.quality}, Latency: ${voice.latency}")
        }
        val desiredVoice = findDesiredVoice(voices)
        if (desiredVoice != null) {
            textToSpeech.setVoice(desiredVoice)
            speak("Hello, this is a test with the new voice.")
        } else {
            Log.w("TTS", "Desired voice not found.")
            speak("Welcome to Echovision! Tap anywhere on the screen and speak your command. Say help for available commands.")
        }
    }

    private fun findDesiredVoice(voices: Set<Voice>): Voice? {
        return voices.find { voice ->
            voice.locale == Locale.US && "female" in voice.name.lowercase() && voice.quality == 400
        }
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
    }

    // State variables (moved here for clarity)
    private val command = mutableStateOf("")
    private val statusText = mutableStateOf("Tutorial Mode")
    private val isCameraVisible = mutableStateOf(false)
    private val isLottieVisible = mutableStateOf(true)
    private val isProcessing = mutableStateOf(false)
    private var isListening = false
    private var onSpeechRecognizerReady: (() -> Unit)? = null
}

// Placeholder for DocumentScannerScreen (assumed to be a separate class)
class DocumentScannerScreen(
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context
) {
    @Composable
    fun DocumentScannerScreenUI(isVisible: Boolean, onClose: () -> Unit) {
        if (isVisible) {
            Box(Modifier.fillMaxSize()) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                // Add UI elements like close button if needed
            }
        }
    }
}