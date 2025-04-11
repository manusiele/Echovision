package com.example.echovision.hearingImpaired

import android.content.Context
import android.graphics.Bitmap
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.ExecutorService

private const val TAG = "SignLanguageScreen"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SignLanguageDetectionScreen(
    cameraExecutor: ExecutorService,
    useKiswahili: Boolean = false,
    useDeepSeekApi: Boolean = false,
    deepSeekApiKey: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State variables
    var hasCameraPermission by remember { mutableStateOf(checkCameraPermission(context)) }
    var signDetectionActive by remember { mutableStateOf(false) }
    var cameraFacing by remember { mutableIntOf(CameraSelector.LENS_FACING_FRONT) }

    // Detection state variables
    val detectedSignText = remember { mutableStateOf("") }
    val isProcessing = remember { mutableStateOf(false) }
    val confidence = remember { mutableStateOf(0f) }
    val debugInfo = remember { mutableStateOf("") }
    val edgeOverlayBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val cameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Initialize detector
    val signLanguageDetector = remember {
        SignLanguageDetector(
            context = context,
            useKiswahili = useKiswahili,
            useDeepSeekApi = useDeepSeekApi,
            deepSeekApiKey = deepSeekApiKey,
            onSignDetected = { text, conf ->
                detectedSignText.value = text
                confidence.value = conf
            },
            onProcessingStateChanged = { processing ->
                isProcessing.value = processing
            },
            onDebugInfoUpdated = { info ->
                debugInfo.value = info
            },
            onEdgeOverlayUpdated = { bitmap ->
                edgeOverlayBitmap.value = bitmap
            }
        )
    }

    // Clean up resources when the composable is removed
    DisposableEffect(signLanguageDetector) {
        onDispose {
            signLanguageDetector.shutdown()
            cameraProvider.value?.unbindAll()
        }
    }

    // Request camera permission
    val requestPermissionLauncher = rememberPermissionState(
        android.Manifest.permission.CAMERA
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            signDetectionActive = true
            vibratePhone(context)
            Toast.makeText(
                context,
                if (useKiswahili) "Utambuzi wa Lugha ya Ishara Umeanza" else "Sign Language Detection Started",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                if (useKiswahili) "Ruhusa ya kamera inahitajika" else "Camera permission required",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Observe lifecycle to handle pause and destroy events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    signDetectionActive = false
                    cameraProvider.value?.unbindAll()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    signLanguageDetector.shutdown()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Camera preview area with overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
        ) {
            // Camera preview
            if (hasCameraPermission && signDetectionActive) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        startCameraWithSignDetection(
                            context = context,
                            previewView = previewView,
                            lifecycleOwner = lifecycleOwner,
                            cameraFacing = cameraFacing,
                            signLanguageDetector = signLanguageDetector,
                            cameraExecutor = cameraExecutor,
                            onCameraProviderAvailable = { provider ->
                                cameraProvider.value = provider
                            }
                        )
                    }
                )
            }

            // Edge overlay for visualization
            edgeOverlayBitmap.value?.let { bitmap ->
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { imageView ->
                        imageView.setImageBitmap(bitmap)
                    }
                )
            }

            // Processing indicator
            if (isProcessing.value) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp)
                        .background(Color.Red, CircleShape)
                )
            }

            // Camera switch button
            IconButton(
                onClick = {
                    cameraProvider.value?.unbindAll()
                    cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT)
                        CameraSelector.LENS_FACING_BACK else
                        CameraSelector.LENS_FACING_FRONT
                    vibratePhone(context)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = if (useKiswahili) "Badilisha kamera" else "Switch camera",
                    tint = Color.White
                )
            }
        }

        // Detection result area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (useKiswahili) "Ishara Iliyotambuliwa:" else "Detected Sign:",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detectedSignText.value.ifEmpty { if (useKiswahili) "Hakuna ishara" else "No sign detected" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (confidence.value > 0) {
                    Text(
                        text = if (useKiswahili)
                            "Uhakika: ${(confidence.value * 100).toInt()}%"
                        else
                            "Confidence: ${(confidence.value * 100).toInt()}%",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (debugInfo.value.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = debugInfo.value,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Control buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (hasCameraPermission) {
                        signDetectionActive = true
                        vibratePhone(context)
                        Toast.makeText(
                            context,
                            if (useKiswahili) "Utambuzi wa Lugha ya Ishara Umeanza" else "Sign Language Detection Started",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        requestPermissionLauncher.launchPermissionRequest()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                enabled = !signDetectionActive
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (useKiswahili) "Anza" else "Start",
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    signDetectionActive = false
                    cameraProvider.value?.unbindAll()
                    vibratePhone(context)
                    Toast.makeText(
                        context,
                        if (useKiswahili) "Utambuzi wa Ishara Umesimama" else "Sign Detection Stopped",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                enabled = signDetectionActive
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (useKiswahili) "Simamisha" else "Stop",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun checkCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun vibratePhone(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    if (vibrator?.hasVibrator() == true) {
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        Log.w(TAG, "Vibrator unavailable or not supported")
    }
}

private fun startCameraWithSignDetection(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    cameraFacing: Int,
    signLanguageDetector: SignLanguageDetector,
    cameraExecutor: ExecutorService,
    onCameraProviderAvailable: (ProcessCameraProvider) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        try {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            onCameraProviderAvailable(cameraProvider)

            // Set up the preview use case
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            try {
                // Choose the camera by facing direction
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraFacing)
                    .build()

                // Set up the image analysis use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, signLanguageDetector.signLanguageAnalyzer)
                    }

                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d(TAG, "Camera bound with MediaPipe Hands detection")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
                Toast.makeText(
                    context,
                    "${if (signLanguageDetector.useKiswahili) "Hitilafu ya kamera" else "Camera error"}: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera", e)
            Toast.makeText(
                context,
                "${if (signLanguageDetector.useKiswahili) "Hitilafu ya kamera" else "Camera error"}: ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }, ContextCompat.getMainExecutor(context))
}