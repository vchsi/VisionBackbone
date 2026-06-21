package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DeepgramTTSService {
    companion object {
        private const val TAG = "DeepgramTTSService"
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var onAudio: ((ByteArray) -> Unit)? = null

    fun connect(apiKey: String, onAudio: (ByteArray) -> Unit) {
        if (apiKey.isEmpty() || apiKey == "YOUR_DEEPGRAM_API_KEY") return
        this.onAudio = onAudio

        val request = Request.Builder()
            .url(GeminiConfig.DEEPGRAM_TTS_WEBSOCKET_URL)
            .header("Authorization", "Token $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Deepgram TTS")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Raw PCM frames — forward directly to audio output
                onAudio?.invoke(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // JSON control messages (e.g. {"type":"Flushed"}) — log only
                Log.d(TAG, "TTS control: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "TTS WebSocket failure: ${t.message}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "TTS closed: $code $reason")
            }
        })
    }

    fun speak(text: String) {
        val ws = webSocket ?: return
        if (text.isBlank()) return
        val speakMsg = JSONObject().put("type", "Speak").put("text", text).toString()
        val flushMsg = JSONObject().put("type", "Flush").toString()
        ws.send(speakMsg)
        ws.send(flushMsg)
        Log.d(TAG, "TTS speak: ${text.take(80)}")
    }

    fun disconnect() {
        webSocket?.close(1000, "Session ended")
        webSocket = null
        onAudio = null
        Log.d(TAG, "TTS disconnected")
    }
}
