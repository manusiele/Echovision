package com.example.echovision.visualImpared.ModelLoader

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.getLifecycleScope
import androidx.lifecycle.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

@OptIn(ExperimentalStdlibApi::class)
class ModelLoader(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var tfliteInterpreter: Interpreter? = null
    val analysisResult = MutableLiveData<String>()

    init {
        lifecycleOwner.getLifecycleScope().launch {
            loadModel()

            if (context is LifecycleOwner) {
                analysisResult.observe(context) { result ->
                    Log.d("ModelLoader", "Analysis result: $result")
                }
            } else {
                Log.w("ModelLoader", "Context is not a LifecycleOwner")
            }
        }
    }

    private suspend fun loadModel(): Unit = withContext(Dispatchers.IO) {
        try {
            val modelBuffer = loadModelFile("model.tflite")
            tfliteInterpreter = Interpreter(modelBuffer)
            analysisResult.postValue("Model loaded successfully")
        } catch (e: Exception) {
            Log.e("ModelLoader", "Error loading model: ${e.message}")
            analysisResult.postValue("Error loading model: ${e.message}")
        }
    }

    private fun loadModelFile(modelFileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = fileDescriptor.fileDescriptor.let {
            java.io.FileInputStream(it)
        }
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

    fun analyzeText(input: String) {
        if (input.isBlank()) {
            analysisResult.postValue("Error: Empty input")
            return
        }

        lifecycleOwner.getLifecycleScope().launch {
            try {
                val result = runInference(input)
                analysisResult.postValue(result)
            } catch (e: Exception) {
                Log.e("ModelLoader", "Error during analysis: ${e.message}")
                analysisResult.postValue("Error during analysis: ${e.message}")
            }
        }
    }

    private suspend fun runInference(input: String): String = withContext(Dispatchers.Default) {
        // Here would be the actual inference code using the TFLite interpreter
        // This is a simplified placeholder
        val interpreter = tfliteInterpreter
        if (interpreter == null) {
            return@withContext "Model not loaded"
        }

        // Sample implementation
        return@withContext "Inference result for: $input"
    }

    fun processData(data: String) {
        analysisResult.postValue("Processed data: $data")
    }

    fun close() {
        tfliteInterpreter?.close()
        tfliteInterpreter = null
    }
}