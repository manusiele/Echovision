package com.example.echovision.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var imageCapture: ImageCapture
    private var cameraBound: Boolean = false

    var onImageCapturedListener: ((Uri) -> Unit)? = null

    init {
        imageCapture = ImageCapture.Builder().build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(context))
    }

    fun startCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            try {
                // Unbind any bound use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                cameraBound = true
                Log.d("CameraManager", "Camera bound successfully.")
            } catch (e: Exception) {
                cameraBound = false
                Log.e("CameraManager", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun focusAndCapture(previewView: PreviewView) {
        Log.d("CameraManager", "focusAndCapture() called")

        val future = camera?.cameraControl?.startFocusAndMetering(
            FocusMeteringAction.Builder(
                previewView.meteringPointFactory.createPoint(
                    previewView.width / 2f,
                    previewView.height / 2f
                ),
                FocusMeteringAction.FLAG_AF
            ).build()
        )

        future?.addListener({
            Log.d("CameraManager", "Focus and metering completed")
            takePicture()
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePicture() {
        Log.d("CameraManager", "takePicture() called")

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            File(context.getExternalFilesDir(null), "image_${System.currentTimeMillis()}.jpg")
        ).build()

        takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            { outputFileResults ->
                Log.d("CameraManager", "Photo capture succeeded: ${outputFileResults.savedUri}")
                onImageCapturedListener?.invoke(outputFileResults.savedUri ?: Uri.EMPTY)
            },
            { exception ->
                Log.e("CameraManager", "Photo capture failed: ${exception.message}")
                exception.printStackTrace()
            }
        )
    }

    fun takePicture(
        outputFileOptions: ImageCapture.OutputFileOptions,
        executor: Executor,
        onImageSaved: (ImageCapture.OutputFileResults) -> Unit,
        onError: (ImageCaptureException) -> Unit,
        maxRetries: Int = 3,
        retryDelayMs: Long = 200
    ) {
        tryTakePicture(outputFileOptions, executor, onImageSaved, 0, maxRetries, retryDelayMs, onError)
    }

    private fun tryTakePicture(
        outputFileOptions: ImageCapture.OutputFileOptions,
        executor: Executor,
        onImageSaved: (ImageCapture.OutputFileResults) -> Unit,
        retries: Int,
        maxRetries: Int,
        retryDelayMs: Long,
        onError: (ImageCaptureException) -> Unit
    ) {
        imageCapture.takePicture(
            outputFileOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onImageSaved(output)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (retries < maxRetries) {
                        // Retry after delay
                        executor.execute {
                            Thread.sleep(retryDelayMs)
                            tryTakePicture(
                                outputFileOptions,
                                executor,
                                onImageSaved,
                                retries + 1,
                                maxRetries,
                                retryDelayMs,
                                onError
                            )
                        }
                    } else {
                        onError(exception)
                    }
                }
            }
        )
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        cameraBound = false
        Log.d("CameraManager", "Camera unbound")
    }
}