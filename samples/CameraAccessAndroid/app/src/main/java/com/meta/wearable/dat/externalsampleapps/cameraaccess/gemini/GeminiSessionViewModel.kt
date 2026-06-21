package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.OpenClawBridge
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.OpenClawEventClient
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.OpenClawConnectionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.ToolCallRouter
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.ToolCallStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TranscriptSource { STREAMING_PROVISIONAL, BATCH_FINALIZED }

data class TranscriptLine(
    val speaker: Int?,
    val text: String,
    val source: TranscriptSource,
    val turnId: Int,
)

data class GeminiUiState(
    val isGeminiActive: Boolean = false,
    val connectionState: GeminiConnectionState = GeminiConnectionState.Disconnected,
    val isModelSpeaking: Boolean = false,
    val errorMessage: String? = null,
    val aiTranscript: String = "",
    val pendingSegment: String? = null,
    val pendingSegmentSpeaker: Int? = null,
    val finalizedLines: List<TranscriptLine> = emptyList(),
    val toolCallStatus: ToolCallStatus = ToolCallStatus.Idle,
    val openClawConnectionState: OpenClawConnectionState = OpenClawConnectionState.NotConfigured,
)

class GeminiSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(GeminiUiState())
    val uiState: StateFlow<GeminiUiState> = _uiState.asStateFlow()

    private val geminiService = GeminiLiveService()
    private val deepgramService = DeepgramSTTService()
    private val batchService = DeepgramBatchService()
    private val imageAPIService = ImageAPIService(getApplication())
    private val openClawBridge = OpenClawBridge()
    private var toolCallRouter: ToolCallRouter? = null
    private val audioManager = AudioManager(getApplication())
    private val eventClient = OpenClawEventClient()
    private var lastDisplayFrameTime: Long = 0
    private var lastImageAPIFrameTime: Long = 0
    private var stateObservationJob: Job? = null
    private var silenceTimerJob: Job? = null
    private val accumulatedTurns = mutableListOf<SpeakerTurn>()
    private var currentTurnId = 0
    private var sessionStartTimeMs: Long = 0

    companion object {
        private const val TAG = "GeminiSessionVM"
        private const val SILENCE_SAVE_DELAY_MS = 25_000L
    }

    var streamingMode: StreamingMode = StreamingMode.GLASSES

    fun startSession() {
        if (_uiState.value.isGeminiActive) return
        sessionStartTimeMs = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(isGeminiActive = true)

        audioManager.onAudioCaptured = lambda@{ data ->
            if (streamingMode == StreamingMode.PHONE && geminiService.isModelSpeaking.value) return@lambda
            geminiService.sendAudio(data)
            deepgramService.sendAudio(data)
            // Only accumulate user audio in batch buffer (skip when AI is playing)
            if (!geminiService.isModelSpeaking.value) {
                batchService.appendAudio(data)
            }
        }

        geminiService.onAudioReceived = { data -> audioManager.playAudio(data) }
        geminiService.onInterrupted = { audioManager.stopPlayback() }

        geminiService.onTurnComplete = {
            val turnId = currentTurnId
            currentTurnId++
            _uiState.value = _uiState.value.copy(pendingSegment = null, pendingSegmentSpeaker = null)

            if (GeminiConfig.isDeepgramConfigured) {
                batchService.flushAndUpload(GeminiConfig.deepgramAPIKey) { turns ->
                    if (turns.isNotEmpty()) {
                        // Atomically replace STREAMING_PROVISIONAL lines for this turn with BATCH_FINALIZED
                        val batchLines = turns.map { turn ->
                            TranscriptLine(
                                speaker = turn.speaker,
                                text = turn.text,
                                source = TranscriptSource.BATCH_FINALIZED,
                                turnId = turnId,
                            )
                        }
                        val retained = _uiState.value.finalizedLines
                            .filter { it.turnId != turnId }
                        _uiState.value = _uiState.value.copy(
                            finalizedLines = retained + batchLines
                        )
                    }
                }
            }
        }

        geminiService.onOutputTranscription = { text ->
            _uiState.value = _uiState.value.copy(aiTranscript = _uiState.value.aiTranscript + text)
        }
        geminiService.onDisconnected = { reason ->
            deepgramService.disconnect()
            if (_uiState.value.isGeminiActive) {
                stopSession()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Connection lost: ${reason ?: "Unknown error"}"
                )
            }
        }

        viewModelScope.launch {
            try {
                audioManager.startCapture()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Mic capture failed: ${e.message}",
                    isGeminiActive = false,
                )
                return@launch
            }

            if (GeminiConfig.isDeepgramConfigured) {
                deepgramService.connect(GeminiConfig.deepgramAPIKey)
                launch {
                    deepgramService.transcript.collect { t ->
                        if (t != null) {
                            if (t.isFinal) {
                                // Lock in the segment as STREAMING_PROVISIONAL
                                val line = TranscriptLine(
                                    speaker = t.speaker,
                                    text = t.text,
                                    source = TranscriptSource.STREAMING_PROVISIONAL,
                                    turnId = currentTurnId,
                                )
                                _uiState.value = _uiState.value.copy(
                                    finalizedLines = _uiState.value.finalizedLines + line,
                                    pendingSegment = null,
                                    pendingSegmentSpeaker = null,
                                )
                                // Deduplicate: skip if same text as either of last 2 accumulated turns
                                if (accumulatedTurns.takeLast(2).any { it.text.trim() == t.text.trim() }) return@collect
                                // Accumulate and reset the 25s silence timer
                                val elapsedSecs = (System.currentTimeMillis() - sessionStartTimeMs) / 1000f
                                accumulatedTurns.add(SpeakerTurn(
                                    speaker = t.speaker ?: 0,
                                    start = elapsedSecs,
                                    end = elapsedSecs,
                                    text = t.text,
                                    words = emptyList(),
                                    wallClockMs = System.currentTimeMillis(),
                                ))
                                silenceTimerJob?.cancel()
                                silenceTimerJob = viewModelScope.launch {
                                    delay(SILENCE_SAVE_DELAY_MS)
                                    flushAccumulatedTranscript()
                                }
                            } else {
                                // Overwrite pending segment (not append)
                                _uiState.value = _uiState.value.copy(
                                    pendingSegment = t.text,
                                    pendingSegmentSpeaker = t.speaker,
                                )
                            }
                        }
                    }
                }
            }

            if (!GeminiConfig.isConfigured) return@launch

            openClawBridge.checkConnection()
            openClawBridge.resetSession()

            toolCallRouter = ToolCallRouter(openClawBridge, viewModelScope)
            geminiService.onToolCall = { toolCall ->
                for (call in toolCall.functionCalls) {
                    toolCallRouter?.handleToolCall(call) { response ->
                        geminiService.sendToolResponse(response)
                    }
                }
            }
            geminiService.onToolCallCancellation = { cancellation ->
                toolCallRouter?.cancelToolCalls(cancellation.ids)
            }

            stateObservationJob = viewModelScope.launch {
                while (isActive) {
                    delay(100)
                    _uiState.value = _uiState.value.copy(
                        connectionState = geminiService.connectionState.value,
                        isModelSpeaking = geminiService.isModelSpeaking.value,
                        toolCallStatus = openClawBridge.lastToolCallStatus.value,
                        openClawConnectionState = openClawBridge.connectionState.value,
                    )
                }
            }

            geminiService.connect { setupOk ->
                if (!setupOk) {
                    val msg = when (val state = geminiService.connectionState.value) {
                        is GeminiConnectionState.Error -> state.message
                        else -> "Failed to connect to Gemini"
                    }
                    _uiState.value = _uiState.value.copy(errorMessage = msg)
                    stateObservationJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        connectionState = GeminiConnectionState.Disconnected
                    )
                    return@connect
                }

                if (SettingsManager.proactiveNotificationsEnabled) {
                    eventClient.onNotification = { text ->
                        val state = _uiState.value
                        if (state.isGeminiActive && state.connectionState == GeminiConnectionState.Ready) {
                            geminiService.sendTextMessage(text)
                        }
                    }
                    eventClient.connect()
                }
            }
        }
    }

    fun stopSession() {
        silenceTimerJob?.cancel()
        silenceTimerJob = null
        flushAccumulatedTranscript()
        eventClient.disconnect()
        toolCallRouter?.cancelAll()
        toolCallRouter = null
        audioManager.stopCapture()
        deepgramService.disconnect()
        batchService.reset()
        geminiService.disconnect()
        stateObservationJob?.cancel()
        stateObservationJob = null
        currentTurnId = 0
        _uiState.value = GeminiUiState()
    }

    private fun flushAccumulatedTranscript() {
        if (accumulatedTurns.isEmpty()) return
        val turns = accumulatedTurns.toList()
        accumulatedTurns.clear()
        Log.d(TAG, "Saving transcript: ${turns.size} segments")
        TranscriptSaver.save(getApplication(), turns)
    }

    fun sendVideoFrameIfThrottled(bitmap: Bitmap) {
        if (!SettingsManager.videoStreamingEnabled) return
        if (!_uiState.value.isGeminiActive) return
        val now = System.currentTimeMillis()
        if (now - lastDisplayFrameTime >= GeminiConfig.DISPLAY_FRAME_INTERVAL_MS) {
            lastDisplayFrameTime = now
        }
        if (now - lastImageAPIFrameTime >= GeminiConfig.IMAGE_API_FRAME_INTERVAL_MS) {
            lastImageAPIFrameTime = now
            imageAPIService.sendFrame(bitmap)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}
