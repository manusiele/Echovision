package com.example.echovision.visualImpared.features

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

class FindObject(
    private val context: Context,
    private val modelFileName: String = "mobilenet_v1_1.0_224.tflite",
    private val labelFileName: String = "labelmap.txt"
) {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private val inputSize: Int = 300
    private val numDetections: Int = 10
    private val confidenceThreshold: Float = 0.5f
    private val nmsThreshold: Float = 0.5f

    init {
        try {
            interpreter = Interpreter(loadModelFile(context, modelFileName), Interpreter.Options())
            labels = FileUtil.loadLabels(context, labelFileName)
        } catch (e: IOException) {
            Log.e("ObjectDetector", "Error initializing ObjectDetector", e)
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelName)
        val fileChannel = FileInputStream(fileDescriptor.fileDescriptor).channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun preprocessImage(bitmap: Bitmap): TensorImage {
        return ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0.0f, 1.0f))
            .build()
            .process(TensorImage.fromBitmap(bitmap))
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        if (interpreter == null) {
            Log.e("ObjectDetector", "Interpreter is null. Model might not have loaded correctly.")
            return emptyList()
        }

        val inputBuffer = preprocessImage(bitmap).buffer

        // Prepare output arrays
        val outputLocations = Array(1) {
            Array(numDetections) {
                FloatArray(4)
            }
        }
        val outputClasses = Array(1) {
            FloatArray(numDetections)
        }
        val outputScores = Array(1) {
            FloatArray(numDetections)
        }
        val outputDetectionCount = FloatArray(1)

        val outputs = mapOf(
            0 to outputLocations,
            1 to outputClasses,
            2 to outputScores,
            3 to outputDetectionCount
        )

        try {
            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            return postprocessResults(
                outputLocations,
                outputClasses,
                outputScores,
                outputDetectionCount,
                bitmap.width,
                bitmap.height
            )
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Error running inference", e)
            return emptyList()
        }
    }

    private fun postprocessResults(
        outputLocations: Array<Array<FloatArray>>,
        outputClasses: Array<FloatArray>,
        outputScores: Array<FloatArray>,
        outputDetectionCount: FloatArray,
        imageWidth: Int,
        imageHeight: Int
    ): List<DetectionResult> {
        val detectionResults = ArrayList<DetectionResult>()

        // Create a priority queue to sort detection results by score
        val pq = PriorityQueue<Int>(numDetections) { lhs, rhs ->
            Float.compare(outputScores[0][lhs], outputScores[0][rhs])
        }

        // Add all detections with score above threshold to the priority queue
        for (i in 0 until numDetections) {
            if (outputScores[0][i] >= confidenceThreshold) {
                pq.add(i)
            }
        }

        // Apply non-maximum suppression
        val detections = ArrayList<Int>()
        while (!pq.isEmpty()) {
            val idx = pq.remove()
            var good = true

            for (detection in detections) {
                if (calculateIOU(outputLocations[0][idx], outputLocations[0][detection]) > nmsThreshold) {
                    good = false
                    break
                }
            }

            if (good) {
                detections.add(idx)
            }
        }

        // Process valid detections
        for (idx in detections) {
            val boundingBox = outputLocations[0][idx]
            val box = RectF(
                imageWidth * Math.max(0.0f, boundingBox[1]),
                imageHeight * Math.max(0.0f, boundingBox[0]),
                imageWidth * Math.min(1.0f, boundingBox[3]),
                imageHeight * Math.min(1.0f, boundingBox[2])
            )

            val labelIndex = outputClasses[0][idx].toInt()
            val label = if (labelIndex < labels.size) labels[labelIndex] else "Unknown"

            detectionResults.add(
                DetectionResult(
                    box,
                    label,
                    outputScores[0][idx]
                )
            )
        }

        return detectionResults
    }

    private fun calculateIOU(box1: FloatArray, box2: FloatArray): Float {
        val yMin1 = box1[0]
        val xMin1 = box1[1]
        val yMax1 = box1[2]
        val xMax1 = box1[3]

        val yMin2 = box2[0]
        val xMin2 = box2[1]
        val yMax2 = box2[2]
        val xMax2 = box2[3]

        // Calculate intersection area
        val intersectionArea = Math.max(0.0f, Math.min(xMax1, xMax2) - Math.max(xMin1, xMin2)) *
                Math.max(0.0f, Math.min(yMax1, yMax2) - Math.max(yMin1, yMin2))

        // Calculate union area
        val unionArea = ((xMax1 - xMin1) * (yMax1 - yMin1)) +
                ((xMax2 - xMin2) * (yMax2 - yMin2)) - intersectionArea

        return if (unionArea <= 0.0f) 0.0f else intersectionArea / unionArea
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    data class DetectionResult(
        val boundingBox: RectF,
        val label: String,
        val confidence: Float
    )
}