package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream

class AudioManager(application: Application) {
    companion object {
        private const val TAG = "AudioManager"
        private const val MIN_SEND_BYTES = 3200 // 100ms at 16kHz mono Int16 = 1600 frames * 2 bytes
        private const val BLUETOOTH_SCO_TIMEOUT_MS = 3000L
        private const val BLUETOOTH_SCO_POLL_MS = 100L
    }

    private val sysAudioManager =
        application.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    var onAudioCaptured: ((ByteArray) -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureThread: Thread? = null
    @Volatile
    private var isCapturing = false
    private val accumulatedData = ByteArrayOutputStream()
    private val accumulateLock = Any()

    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isCapturing) return

        // Route audio through the glasses' Bluetooth microphone if available.
        // startBluetoothSco() is a no-op when no BT device is connected, so this is safe
        // to call unconditionally — we proceed with the device mic on timeout.
        // Note: API 31+ deprecated startBluetoothSco in favor of AudioRecord.setPreferredDevice,
        // but that requires the SCO link to already be active anyway; this still works on API 31+.
        @Suppress("DEPRECATION")
        sysAudioManager.startBluetoothSco()
        val deadline = System.currentTimeMillis() + BLUETOOTH_SCO_TIMEOUT_MS
        @Suppress("DEPRECATION")
        while (!sysAudioManager.isBluetoothScoOn && System.currentTimeMillis() < deadline) {
            Thread.sleep(BLUETOOTH_SCO_POLL_MS)
        }
        @Suppress("DEPRECATION")
        if (sysAudioManager.isBluetoothScoOn) {
            Log.d(TAG, "Bluetooth SCO connected — using glasses microphone")
        } else {
            Log.w(TAG, "Bluetooth SCO not available — falling back to device microphone")
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            GeminiConfig.INPUT_AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            GeminiConfig.INPUT_AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(GeminiConfig.OUTPUT_AUDIO_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(
                AudioTrack.getMinBufferSize(
                    GeminiConfig.OUTPUT_AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2
            )
            .build()

        audioRecord?.startRecording()
        audioTrack?.play()
        isCapturing = true

        synchronized(accumulateLock) {
            accumulatedData.reset()
        }

        captureThread = Thread({
            val buffer = ByteArray(bufferSize)
            var tapCount = 0
            while (isCapturing) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    tapCount++
                    synchronized(accumulateLock) {
                        accumulatedData.write(buffer, 0, read)
                        if (accumulatedData.size() >= MIN_SEND_BYTES) {
                            val chunk = accumulatedData.toByteArray()
                            accumulatedData.reset()
                            if (tapCount <= 3) {
                                Log.d(TAG, "Sending chunk: ${chunk.size} bytes (~${chunk.size / 32}ms)")
                            }
                            onAudioCaptured?.invoke(chunk)
                        }
                    }
                }
            }
        }, "audio-capture").also { it.start() }

        Log.d(TAG, "Audio capture started (16kHz mono PCM16)")
    }

    fun playAudio(data: ByteArray) {
        if (!isCapturing || data.isEmpty()) return
        audioTrack?.write(data, 0, data.size)
    }

    fun stopPlayback() {
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
    }

    fun stopCapture() {
        if (!isCapturing) return
        isCapturing = false

        captureThread?.join(1000)
        captureThread = null

        // Flush remaining accumulated audio
        synchronized(accumulateLock) {
            if (accumulatedData.size() > 0) {
                val chunk = accumulatedData.toByteArray()
                accumulatedData.reset()
                onAudioCaptured?.invoke(chunk)
            }
        }

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        @Suppress("DEPRECATION")
        sysAudioManager.stopBluetoothSco()

        Log.d(TAG, "Audio capture stopped")
    }
}


