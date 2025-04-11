package com.example.echovision.visualImpared.features

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.Properties
import java.util.concurrent.Executors

/**
 * Class responsible for describing scene in an image using Hugging Face's BLIP model API
 */
class DescribeScene(private val context: Context) {

    /**
     * Interface for callback to handle API response
     */
    interface Callback {
        fun onSuccess(description: String?)
        fun onFailure(error: String?)
    }

    /**
     * Main function to start the process of describing a scene from an image
     */
    fun describeScene(image: Bitmap, callback: Callback) {
        Log.d(TAG, "describeScene: Starting scene description process")

        if (!isNetworkAvailable()) {
            Log.w(TAG, "describeScene: No internet connection")
            callback.onFailure("No internet connection")
            return
        }

        Executors.newSingleThreadExecutor().execute {
            Log.d(TAG, "describeScene: Executing API call on background thread")
            val client = OkHttpClient()
            val outputStream = ByteArrayOutputStream()

            try {
                val apiToken = loadApiToken()
                Log.d(TAG, "describeScene: Loaded API token successfully")

                image.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                val requestBody = outputStream.toByteArray().toRequestBody("image/jpeg".toMediaType())
                Log.d(TAG, "describeScene: Created request body successfully")

                val request = Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer $apiToken")
                    .post(requestBody)
                    .build()

                Log.d(TAG, "describeScene: Sending API request")

                client.newCall(request).execute().use { response ->
                    val code = response.code
                    Log.d(TAG, "describeScene: Received API response - Code: $code")

                    if (response.isSuccessful && response.body != null) {
                        val responseBody = response.body!!.string()
                        Log.d(TAG, "describeScene: API response body: $responseBody")

                        val jsonArray = JSONArray(responseBody)
                        val jsonObject = jsonArray.getJSONObject(0)
                        val description = jsonObject.getString("generated_text")

                        callback.onSuccess(description)
                        Log.d(TAG, "describeScene: Successfully parsed description: $description")
                    } else {
                        handleErrorResponse(response.code, callback)
                    }
                }
            } catch (e: Exception) {
                callback.onFailure("Exception: ${e.message}")
                Log.e(TAG, "describeScene: Error occurred during API call", e)
            } finally {
                try {
                    outputStream.close()
                    Log.d(TAG, "describeScene: ByteArrayOutputStream closed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "describeScene: Error closing ByteArrayOutputStream", e)
                }
            }
        }
    }

    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        Log.d(TAG, "isNetworkAvailable: Checking network availability")
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val isAvailable = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Log.d(TAG, "isNetworkAvailable: Network available = $isAvailable")
        return isAvailable
    }

    /**
     * Handle error responses from the API
     */
    private fun handleErrorResponse(code: Int, callback: Callback) {
        Log.w(TAG, "handleErrorResponse: HTTP error code = $code")
        when (code) {
            401 -> {
                Log.e(TAG, "handleErrorResponse: Unauthorized - Check your API token")
                callback.onFailure("Unauthorized: Check your API token")
            }
            404 -> {
                Log.e(TAG, "handleErrorResponse: Endpoint not found - Check the URL")
                callback.onFailure("Endpoint not found: Check the URL")
            }
            else -> {
                Log.e(TAG, "handleErrorResponse: Unknown error - Code: $code")
                callback.onFailure("Error: $code")
            }
        }
    }

    /**
     * Load API token from configuration file
     */
    private fun loadApiToken(): String {
        Log.d(TAG, "loadApiToken: Loading API token from config.properties")
        try {
            val properties = Properties()
            val inputStream = context.assets.open("config.properties")
            properties.load(inputStream)
            val apiToken = properties.getProperty("API_TOKEN", "default_token_if_not_set")
            Log.d(TAG, "loadApiToken: API token loaded successfully")
            return apiToken
        } catch (e: Exception) {
            Log.e(TAG, "loadApiToken: Error loading API token", e)
            return "default_token_if_not_set"
        }
    }

    companion object {
        private const val TAG = "DescribeScene"
        private const val API_URL = "https://api-inference.huggingface.co/models/Salesforce/blip-image-captioning-large"
    }
}