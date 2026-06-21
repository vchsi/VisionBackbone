package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class BatchWord(
    val word: String,
    val start: Float,
    val end: Float,
    val speaker: Int,
    val speakerConfidence: Float?,
)

data class SpeakerTurn(
    val speaker: Int,
    val start: Float,
    val end: Float,
    val text: String,
    val words: List<BatchWord>,
)

fun groupIntoSpeakerTurns(words: List<BatchWord>): List<SpeakerTurn> {
    if (words.isEmpty()) return emptyList()
    val turns = mutableListOf<SpeakerTurn>()
    var currentSpeaker = words[0].speaker
    var currentWords = mutableListOf(words[0])
    for (word in words.drop(1)) {
        if (word.speaker == currentSpeaker) {
            currentWords.add(word)
        } else {
            turns.add(makeSpeakerTurn(currentSpeaker, currentWords))
            currentSpeaker = word.speaker
            currentWords = mutableListOf(word)
        }
    }
    turns.add(makeSpeakerTurn(currentSpeaker, currentWords))
    return turns
}

private fun makeSpeakerTurn(speaker: Int, words: List<BatchWord>) = SpeakerTurn(
    speaker = speaker,
    start = words.first().start,
    end = words.last().end,
    text = words.joinToString(" ") { it.word },
    words = words,
)

class DeepgramBatchService {
    companion object {
        private const val TAG = "DeepgramBatchSvc"
        // ~200ms minimum before bothering to upload
        private const val MIN_AUDIO_BYTES = 6400
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val audioBuffer = ByteArrayOutputStream()
    private val lock = Any()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun appendAudio(data: ByteArray) {
        synchronized(lock) { audioBuffer.write(data) }
    }

    fun reset() {
        synchronized(lock) { audioBuffer.reset() }
    }

    fun flushAndUpload(apiKey: String, onResult: (List<SpeakerTurn>) -> Unit) {
        val audioData: ByteArray
        synchronized(lock) {
            audioData = audioBuffer.toByteArray()
            audioBuffer.reset()
        }
        if (audioData.size < MIN_AUDIO_BYTES) {
            Log.d(TAG, "Audio clip too short (${audioData.size} bytes), skipping batch upload")
            return
        }
        executor.execute {
            try {
                val turns = uploadToDeepgram(apiKey, audioData)
                onResult(turns)
            } catch (e: Exception) {
                Log.e(TAG, "Batch upload failed", e)
                onResult(emptyList())
            }
        }
    }

    private fun uploadToDeepgram(apiKey: String, data: ByteArray): List<SpeakerTurn> {
        val body = data.toRequestBody("audio/raw".toMediaType())
        val request = Request.Builder()
            .url(GeminiConfig.DEEPGRAM_BATCH_URL)
            .header("Authorization", "Token $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Batch HTTP ${response.code}: ${response.body?.string()?.take(200)}")
                return emptyList()
            }
            val bodyStr = response.body?.string() ?: run {
                Log.e(TAG, "Batch response body is null")
                return emptyList()
            }
            Log.d(TAG, "Batch response received (${bodyStr.length} chars)")
            return parseSpeakerTurns(JSONObject(bodyStr))
        }
    }

    private fun parseSpeakerTurns(json: JSONObject): List<SpeakerTurn> {
        val words = mutableListOf<BatchWord>()
        try {
            val results = json.getJSONObject("results")
            val channels = results.getJSONArray("channels")
            val alternatives = channels.getJSONObject(0).getJSONArray("alternatives")
            val wordsJson = alternatives.getJSONObject(0).optJSONArray("words")
                ?: return emptyList()
            for (i in 0 until wordsJson.length()) {
                val w = wordsJson.getJSONObject(i)
                words.add(
                    BatchWord(
                        word = w.optString("word", ""),
                        start = w.optDouble("start", 0.0).toFloat(),
                        end = w.optDouble("end", 0.0).toFloat(),
                        speaker = w.optInt("speaker", 0),
                        speakerConfidence = if (w.has("speaker_confidence"))
                            w.optDouble("speaker_confidence").toFloat() else null,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing batch response", e)
        }
        return groupIntoSpeakerTurns(words)
    }
}
