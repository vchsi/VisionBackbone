package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import java.io.IOException

class ImageAPIService(
    application: Application,
    val saveEnabled: Boolean = true,
) {
    companion object {
        private const val TAG = "ImageAPIService"
        private const val PLACEHOLDER = "YOUR_IMAGE_API_ENDPOINT"
        private const val ALBUM = "Pictures/VisionClaw"
    }

    private val context: Context = application.applicationContext
    private var warnedOnce = false

    val isConfigured: Boolean
        get() {
            val url = SettingsManager.imageAPIEndpointURL
            return url.isNotEmpty() && url != PLACEHOLDER
        }

    fun sendFrame(bitmap: Bitmap) {
        if (!isConfigured && !saveEnabled) {
            if (!warnedOnce) {
                Log.w(TAG, "imageAPIEndpointURL not configured and saving disabled — frames will not be processed")
                warnedOnce = true
            }
            return
        }

        if (saveEnabled) {
            saveToPhotos(bitmap)
        }

        // TODO: implement HTTP POST of JPEG bitmap to SettingsManager.imageAPIEndpointURL
    }

    private fun saveToPhotos(bitmap: Bitmap) {
        val filename = "frame_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: run {
                Log.e(TAG, "MediaStore insert returned null URI for $filename")
                return
            }

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)) {
                    throw IOException("Bitmap.compress returned false")
                }
            } ?: throw IOException("openOutputStream returned null for $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            Log.d(TAG, "Saved frame: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save frame: $filename", e)
            resolver.delete(uri, null, null)
        }
    }
}
