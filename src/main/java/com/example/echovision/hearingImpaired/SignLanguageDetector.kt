package com.example.echovision.hearingImpaired

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.*
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.view.InputDeviceCompat
import androidx.core.view.ViewCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class SignLanguageDetector(
    private val context: Context,
    private val executor: Executor,
    val useKiswahili: Boolean = true,
    private val useDeepSeekApi: Boolean = false,
    private val deepSeekApiKey: String? = null
) {
    companion object {
        private const val TAG = "SignLanguageDetector"
    }

    // State flows for UI updates
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

    // Overlay dimensions
    private var overlayWidth = 0
    private var overlayHeight = 0

    // Processing control
    private val processingLock = AtomicBoolean(false)
    private var handLandmarker: HandLandmarker? = null
    private var tfliteInterpreter: Interpreter? = null
    private var latestHandLandmarkResult: HandLandmarkerResult? = null

    // Gesture tracking
    private val gestureBuffer = mutableListOf<GestureFrame>()
    private val maxGestureBufferSize = 30
    private var lastDetectionTime = System.currentTimeMillis()
    private val detectionCooldownMs = 1000L

    // Drawing resources
    private val handPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val jointPaint = Paint().apply {
        color = InputDeviceCompat.SOURCE_ANY
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    // Network client
    private val client = OkHttpClient()

    data class GestureFrame(
        val fingerLandmarks: List<List<NormalizedLandmark>>,
        val timestamp: Long
    )

    init {
        // Initialize in background
        CoroutineScope(Dispatchers.IO).launch {
            initializeMediaPipe()
            if (!useDeepSeekApi) {
                loadTfliteModel()
            }
            _detectedSignText.value = if (useKiswahili) "Inatafuta ishara..." else "Looking for signs..."
        }
    }

    private fun initializeMediaPipe() {
        try {
            handLandmarker = HandLandmarker.createFromOptions(
                context,
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath("hand_landmarker.task")
                            .build()
                    )
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result, inputImage ->
                        processLandmarks(result, inputImage)
                    }
                    .setErrorListener { error ->
                        _debugInfo.value = "${if (useKiswahili) "Hitilafu" else "Error"}: ${error.message}"
                        Log.e(TAG, "MediaPipe error: ${error.message}")
                    }
                    .build()
            )
            Log.d(TAG, "MediaPipe HandLandmarker initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe HandLandmarker", e)
            _debugInfo.value = "${if (useKiswahili) "Hitilafu" else "Error"}: ${e.message}"
        }
    }

    private suspend fun loadTfliteModel() = withContext(Dispatchers.IO) {
        try {
            val modelFileName = if (useKiswahili) "sign_language_model_kiswahili.tflite" else "sign_language_model_english.tflite"
            val modelBuffer = loadModelFile(modelFileName)
            tfliteInterpreter = Interpreter(modelBuffer)
            Log.d(TAG, "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model", e)
            _debugInfo.value = "${if (useKiswahili) "Hitilafu ya kupakia modeli" else "Model loading error"}: ${e.message}"
        }
    }

    private fun loadModelFile(modelFileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val mappedByteBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
        fileChannel.close()
        inputStream.close()
        return mappedByteBuffer!!
    }

    fun getSignLanguageAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { imageProxy ->
        processImageProxy(imageProxy)
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        if (!processingLock.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        _isProcessing.value = true

        // Check if overlay dimensions need updating
        if (overlayWidth != imageProxy.width || overlayHeight != imageProxy.height) {
            overlayWidth = imageProxy.width
            overlayHeight = imageProxy.height
            createNewOverlayBitmap()
        }

        val image = imageProxy.image
        if (image == null) {
            _debugInfo.value = "No image available"
            imageProxy.close()
            return
        }

        val bitmap = imageToBitmap(image, imageProxy.imageInfo.rotationDegrees)
        if (bitmap != null) {
            try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                handLandmarker?.detectAsync(mpImage, imageProxy.imageInfo.timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Image processing error", e)
                _debugInfo.value = "${if (useKiswahili) "Hitilafu ya usindikaji" else "Processing error"}: ${e.message}"
            } finally {
                _isProcessing.value = false
                processingLock.set(false)
                imageProxy.close()
            }
        } else {
            _debugInfo.value = if (useKiswahili) "Ubadilishaji wa bitmap umeshindwa" else "Bitmap conversion failed"
            _isProcessing.value = false
            processingLock.set(false)
            imageProxy.close()
        }
    }

    private fun imageToBitmap(image: Image, rotation: Int): Bitmap? {
        try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // Copy Y channel
            yBuffer.get(nv21, 0, ySize)
            // Copy V and U channels
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            // Convert YUV to JPEG
            val out = ByteArrayOutputStream()
            val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
            val imageBytes = out.toByteArray()

            // Convert JPEG to Bitmap
            var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length)

            // Rotate if needed
            if (rotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }

            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to bitmap", e)
            return null
        }
    }

    private fun createNewOverlayBitmap() {
        if (overlayWidth > 0 && overlayHeight > 0) {
            val newBitmap = Bitmap.createBitmap(overlayWidth, overlayHeight, Bitmap.Config.ARGB_8888)
            Canvas(newBitmap).drawColor(Color.TRANSPARENT)
            _edgeOverlayBitmap.value = newBitmap
        }
    }

    private fun processLandmarks(result: HandLandmarkerResult?, inputImage: Any? = null) {
        latestHandLandmarkResult = result

        if (result == null || result.landmarks().isEmpty()) {
            _debugInfo.value = if (useKiswahili) "Hakuna ishara iliyopatikana" else "No sign detected"
            return
        }

        // Draw landmarks on overlay bitmap
        drawEdgeOverlay(result)

        // Process sign language data
        processSignLanguageData(result)
    }

    private fun drawEdgeOverlay(handResult: HandLandmarkerResult?) {
        val overlay = _edgeOverlayBitmap.value ?: run {
            createNewOverlayBitmap()
            _edgeOverlayBitmap.value ?: return
        }

        val canvas = Canvas(overlay)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        handResult?.landmarks()?.let { landmarks ->
            landmarks.forEach { hand ->
                drawHandConnections(canvas, hand)

                // Draw joints
                hand?.forEach { landmark ->
                    canvas.drawCircle(
                        landmark.x() * overlayWidth,
                        landmark.y() * overlayHeight,
                        8f,
                        jointPaint
                    )
                }
            }
        }

        // Draw sign text if available
        val currentSign = _detectedSignText.value
        if (currentSign != "Looking for signs..." && currentSign != "Inatafuta ishara...") {
            canvas.drawText(currentSign, 50f, 100f, textPaint)
            canvas.drawText(
                "${if (useKiswahili) "Ujasiri" else "Confidence"}: ${(_confidence.value * 100).toInt()}%",
                50f, 150f, textPaint
            )
        }

        // Update the overlay bitmap
        _edgeOverlayBitmap.value = overlay
    }

    private fun drawHandConnections(canvas: Canvas, handLandmarks: List<NormalizedLandmark>) {
        // Define connections between landmarks
        val connections = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 4,  // thumb
            0 to 5, 5 to 6, 6 to 7, 7 to 8,  // index finger
            0 to 9, 9 to 10, 10 to 11, 11 to 12,  // middle finger
            0 to 13, 13 to 14, 14 to 15, 15 to 16,  // ring finger
            0 to 17, 17 to 18, 18 to 19, 19 to 20,  // pinky
            5 to 9, 9 to 13, 13 to 17  // palm connections
        )

        for ((start, end) in connections) {
            if (start < handLandmarks.size && end < handLandmarks.size) {
                canvas.drawLine(
                    handLandmarks[start].x() * overlayWidth,
                    handLandmarks[start].y() * overlayHeight,
                    handLandmarks[end].x() * overlayWidth,
                    handLandmarks[end].y() * overlayHeight,
                    handPaint
                )
            }
        }
    }

    private fun processSignLanguageData(handResult: HandLandmarkerResult?) {
        val fingerLandmarks = handResult?.landmarks() ?: emptyList()

        if (fingerLandmarks.isEmpty()) {
            _debugInfo.value = if (useKiswahili) "Hakuna mikono iliyopatikana" else "No hands detected"
            return
        }

        // Add gesture frame to buffer
        gestureBuffer.add(GestureFrame(fingerLandmarks, System.currentTimeMillis()))

        // Maintain buffer size
        if (gestureBuffer.size > maxGestureBufferSize) {
            gestureBuffer.removeAt(0)
        }

        // Check if we should perform classification
        val currentTime = System.currentTimeMillis()
        if (gestureBuffer.size >= maxGestureBufferSize && currentTime - lastDetectionTime > detectionCooldownMs) {
            // Process on background thread
            CoroutineScope(Dispatchers.IO).launch {
                lastDetectionTime = currentTime

                val (signText, confidenceValue) = if (useDeepSeekApi) {
                    classifySignWithDeepSeek(gestureBuffer)
                } else {
                    classifySignWithTflite(gestureBuffer)
                }

                // Update UI with results if confidence is high enough
                if (confidenceValue > 0.4f && signText.isNotEmpty()) {
                    updateDetectedSign(signText, confidenceValue)
                }
            }
        }
    }

    private fun classifySignWithTflite(gestureFrames: List<GestureFrame>): Pair<String, Float> {
        val interpreter = tfliteInterpreter
        var word = ""
        var confidence = 0.0f

        if (interpreter == null) {
            _debugInfo.value = if (useKiswahili) "Modeli haijapakiwa" else "Model not loaded"
            return Pair(word, confidence)
        }

        // Use latest frame for classification
        val latestFrame = gestureFrames.lastOrNull() ?: return Pair(word, confidence)
        if (latestFrame.fingerLandmarks.isEmpty()) return Pair(word, confidence)

        // Prepare input buffer (21 landmarks * 3 coordinates = 63 values * 4 bytes per float = 252 bytes)
        val inputBuffer = ByteBuffer.allocateDirect(252).order(ByteOrder.nativeOrder())

        // Add all hand landmarks to input buffer
        latestFrame.fingerLandmarks[0].forEach { landmark ->
            inputBuffer.putFloat(landmark.x())
            inputBuffer.putFloat(landmark.y())
            inputBuffer.putFloat(landmark.z())
        }
        inputBuffer.rewind()

        // Prepare output array (assuming 20 classes)
        val outputBuffer = Array(1) { FloatArray(20) }

        // Run inference
        interpreter.run(inputBuffer, outputBuffer)

        // Find class with highest confidence
        val maxIndex = outputBuffer[0].indices.maxByOrNull { outputBuffer[0][it] } ?: -1
        confidence = outputBuffer[0].getOrNull(maxIndex) ?: 0f

        // Map to sign text if confidence is high enough
        if (confidence > 0.5f) {
            // Sign language vocabulary
            val signVocabulary = if (useKiswahili) {
                arrayOf(
                    "Habari", "Asante", "Ndio", "Hapana", "Tafadhali",
                    "Msaada", "Nakupenda", "Samahani", "Nzuri", "Mbaya",
                    "Habari yako?", "Jina langu ni", "Nafurahi kukutana nawe", "Kwa heri",
                    "Nini", "Wapi", "Lini", "Nani", "Kwa nini", "Maji"
                )
            } else {
                arrayOf(
                    "Hello", "Thank you", "Yes", "No", "Please",
                    "Help", "I love you", "Sorry", "Good", "Bad",
                    "How are you?", "My name is", "Nice to meet you", "Goodbye",
                    "What", "Where", "When", "Who", "Why", "Water"
                )
            }

            word = signVocabulary.getOrNull(maxIndex) ?: ""
        }

        return Pair(word, confidence)
    }

    private suspend fun classifySignWithDeepSeek(gestureFrames: List<GestureFrame>): Pair<String, Float> = withContext(Dispatchers.IO) {
        if (deepSeekApiKey.isNullOrEmpty()) {
            _debugInfo.value = if (useKiswahili) "Hakuna ufunguo wa API" else "No API key provided"
            return@withContext Pair("", 0.0f)
        }

        try {
            // Extract features from gestures
            val landmarkData = mutableListOf<Map<String, Any>>()

            // Process last 10 frames to reduce data size
            val framesToProcess = gestureFrames.takeLast(10)

            framesToProcess.forEach { frame ->
                if (frame.fingerLandmarks.isNotEmpty()) {
                    // Convert landmarks to JSON-compatible format
                    val frameData = frame.fingerLandmarks[0].mapIndexed { index, landmark ->
                        mapOf(
                            "id" to index,
                            "x" to landmark.x(),
                            "y" to landmark.y(),
                            "z" to landmark.z()
                        )
                    }
                    landmarkData.add(mapOf("timestamp" to frame.timestamp, "landmarks" to frameData))
                }
            }

            // Create request body
            val requestJson = JSONObject().apply {
                put("language", if (useKiswahili) "kiswahili" else "english")
                put("landmarks", JSONObject.wrap(landmarkData))
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            // Build and execute request
            val request = Request.Builder()
                .url("https://api.deepseek.com/v1/sign-language-recognition")
                .addHeader("Authorization", "Bearer $deepSeekApiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _debugInfo.value = if (useKiswahili) "Hitilafu ya API: ${response.code}" else "API Error: ${response.code}"
                    return@withContext Pair("", 0.0f)
                }

                val responseBody = response.body?.string() ?: return@withContext Pair("", 0.0f)
                val jsonResponse = JSONObject(responseBody)

                val text = jsonResponse.optString("text", "")
                val confidence = jsonResponse.optDouble("confidence", 0.0).toFloat()

                return@withContext Pair(text, confidence)
            }

        } catch (e: Exception) {
            Log.e(TAG, "DeepSeek API error", e)
            _debugInfo.value = if (useKiswahili) "Hitilafu ya mtandao: ${e.message}" else "Network error: ${e.message}"
            return@withContext Pair("", 0.0f)
        }
    }

    private fun updateDetectedSign(signText: String, confidence: Float) {
        _detectedSignText.value = signText
        _confidence.value = confidence
    }

    fun shutdown() {
        Log.d(TAG, "Shutting down SignLanguageDetector")
        handLandmarker?.close()
        tfliteInterpreter?.close()
        gestureBuffer.clear()
        _detectedSignText.value = if (useKiswahili) "Kitambua kimezimwa" else "Detector stopped"
    }
}