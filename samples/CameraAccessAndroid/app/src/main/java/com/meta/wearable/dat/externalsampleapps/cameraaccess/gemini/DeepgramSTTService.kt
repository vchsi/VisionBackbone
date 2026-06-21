package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class DeepgramConnectionState {
    data object NotConfigured : DeepgramConnectionState()
    data object Connecting : DeepgramConnectionState()
    data object Connected : DeepgramConnectionState()
    data class Error(val message: String) : DeepgramConnectionState()
}

data class DeepgramTranscript(
    val text: String,
    val isFinal: Boolean,
    val confidence: Double,
    val speaker: Int? = null,
)

class DeepgramSTTService {
    companion object {
        private const val TAG = "DeepgramSTTService"
    }

    private val _connectionState = MutableStateFlow<DeepgramConnectionState>(DeepgramConnectionState.NotConfigured)
    val connectionState: StateFlow<DeepgramConnectionState> = _connectionState.asStateFlow()

    private val _transcript = MutableStateFlow<DeepgramTranscript?>(null)
    val transcript: StateFlow<DeepgramTranscript?> = _transcript.asStateFlow()

    private var webSocket: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    fun connect(apiKey: String) {
        if (apiKey.isEmpty() || apiKey == "YOUR_DEEPGRAM_API_KEY") {
            _connectionState.value = DeepgramConnectionState.NotConfigured
            return
        }

        _connectionState.value = DeepgramConnectionState.Connecting

        val request = Request.Builder()
            .url(GeminiConfig.DEEPGRAM_WEBSOCKET_URL)
            .header("Authorization", "Token $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Deepgram STT")
                _connectionState.value = DeepgramConnectionState.Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = t.message ?: "Unknown error"
                Log.e(TAG, "Deepgram WebSocket failure: $msg")
                _connectionState.value = DeepgramConnectionState.Error(msg)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Deepgram closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Deepgram closed: $code $reason")
                _connectionState.value = DeepgramConnectionState.NotConfigured
            }
        })
    }

    // 2.7: silently no-ops when not connected so Gemini is never blocked
    fun sendAudio(data: ByteArray) {
        if (_connectionState.value != DeepgramConnectionState.Connected) return
        webSocket?.send(data.toByteString())
    }

    fun disconnect() {
        webSocket?.close(1000, "Session ended")
        webSocket = null
        _connectionState.value = DeepgramConnectionState.NotConfigured
        _transcript.value = null
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Ignore metadata-only messages (no channel key)
            val channel = json.optJSONObject("channel") ?: return
            val alternatives = channel.optJSONArray("alternatives") ?: return
            if (alternatives.length() == 0) return

            val first = alternatives.getJSONObject(0)
            val transcript = first.optString("transcript", "")
            if (transcript.isBlank()) return

            val isFinal = json.optBoolean("is_final", false)
            val confidence = first.optDouble("confidence", 0.0)

            // Extract speaker from first word (streaming diarization returns per-word speaker)
            val words = first.optJSONArray("words")
            val speaker: Int? = if (words != null && words.length() > 0) {
                val w = words.getJSONObject(0)
                if (w.has("speaker")) w.getInt("speaker") else null
            } else null

            Log.d(TAG, "[${if (isFinal) "FINAL" else "interim"}] $transcript (conf=$confidence, spk=$speaker)")
            _transcript.value = DeepgramTranscript(
                text = transcript,
                isFinal = isFinal,
                confidence = confidence,
                speaker = speaker,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Deepgram message: ${e.message}")
        }
    }
}
