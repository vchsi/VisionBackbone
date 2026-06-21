package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.IOException

object TranscriptSaver {
    private const val TAG = "TranscriptSaver"
    private const val FOLDER = "VisionClaw"

    fun save(context: Context, turns: List<SpeakerTurn>) {
        if (turns.isEmpty()) return
        val filename = "transcript_${System.currentTimeMillis()}.txt"
        val content = turns.joinToString("\n") { "[${formatSeconds(it.start)}] Speaker ${it.speaker}: ${it.text}" }
            .toByteArray(Charsets.UTF_8)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: run {
            Log.e(TAG, "MediaStore insert returned null URI for $filename")
            return
        }

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(content)
            } ?: throw IOException("openOutputStream returned null for $uri")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            Log.d(TAG, "Saved transcript: $filename (${turns.size} speaker turns)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save transcript: $filename", e)
            resolver.delete(uri, null, null)
        }
    }

    private fun formatSeconds(seconds: Float): String {
        val total = seconds.toInt()
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
