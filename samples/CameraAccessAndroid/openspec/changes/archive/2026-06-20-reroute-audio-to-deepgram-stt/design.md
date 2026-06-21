## Context

Currently, `AudioManager` captures mic audio at 16 kHz PCM16 and feeds it exclusively to `GeminiLiveService`, which handles both real-time transcription and LLM response generation in a single opaque pipeline. Transcription results are only available as Gemini's `inputTranscription` field, tied to Gemini's turn lifecycle.

The goal is to add a parallel STT path via Deepgram's streaming WebSocket API so that raw transcription is available independently of Gemini's state, enabling richer UI feedback and potential future use cases (e.g., wakeword detection, logging, non-Gemini pipelines).

## Goals / Non-Goals

**Goals:**
- Tap the existing audio byte stream and forward it simultaneously to both Gemini and a Deepgram WebSocket
- Surface Deepgram interim and final transcript events as a `StateFlow` consumed by `GeminiSessionViewModel` and UI
- Keep the Deepgram connection lifecycle coupled to the Gemini session (start/stop together)
- Store the Deepgram API key in `SettingsManager` (SharedPreferences), defaulting to `Secrets.kt`

**Non-Goals:**
- Replacing or disabling Gemini's own `inputAudioTranscription` (may be turned off later as a follow-up)
- Using Deepgram for anything beyond real-time STT (no diarization, no summarization)
- Routing Gemini's *output* audio through Deepgram
- Any server-side component — all WebSocket connections stay on-device

## Decisions

### 1. Parallel fan-out, not a router

**Decision:** `GeminiSessionViewModel` fans the same `ByteArray` from `AudioManager.onAudioCaptured` to both `GeminiLiveService.sendAudio()` and `DeepgramSTTService.sendAudio()`.

**Rationale:** The simplest structural change. No changes to `AudioManager` itself, no abstract router layer needed. Audio chunks are small (~3.2 kB each) so copying or passing the same reference to two consumers adds negligible overhead.

**Alternative considered:** A `SpeechInputRouter` abstraction that dispatches to multiple sinks. Rejected as over-engineering for two consumers.

### 2. `DeepgramSTTService` is a plain class, not a ViewModel

**Decision:** `DeepgramSTTService` mirrors the pattern of `GeminiLiveService` — a plain Kotlin class with a WebSocket, `StateFlow` outputs, and callback hooks. Owned and lifecycle-managed by `GeminiSessionViewModel`.

**Rationale:** Consistent with existing architecture. ViewModels already own service lifetimes; no need for a new ViewModel just for Deepgram.

### 3. Deepgram WebSocket connection parameters

**Decision:** Connect to `wss://api.deepgram.com/v1/listen` with query params:
- `encoding=linear16`
- `sample_rate=16000`
- `channels=1`
- `interim_results=true`
- `endpointing=300` (300ms silence = utterance end)

Authorization via `Authorization: Token <key>` header on the WebSocket handshake (OkHttp `Request.Builder.header()`).

**Rationale:** Matches the audio format `AudioManager` already produces. `interim_results=true` enables live typing-indicator style transcript updates. `endpointing=300` is a reasonable middle ground between responsiveness and fragmentation.

### 4. Transcript exposed as `StateFlow<DeepgramTranscript>`

**Decision:** `DeepgramSTTService` exposes:
```kotlin
data class DeepgramTranscript(
    val text: String,
    val isFinal: Boolean,
    val confidence: Double,
)
val transcript: StateFlow<DeepgramTranscript?>
```

`GeminiSessionViewModel` merges this into `GeminiUiState.deepgramTranscript: String`.

**Rationale:** Decouples the service from UI concerns. `GeminiUiState` is already the single source of truth for the overlay view.

### 5. Connection resilience

**Decision:** On WebSocket failure, `DeepgramSTTService` logs the error and sets `connectionState` to `Error` but does **not** auto-reconnect. The Gemini session continues unaffected; only the Deepgram transcript goes dark.

**Rationale:** Keeps the implementation simple. A Deepgram outage should not bring down the Gemini voice session. Auto-reconnect can be added later if needed.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Deepgram adds latency visible to the user | Deepgram runs in parallel; it has no blocking effect on Gemini audio delivery |
| Double WebSocket consuming extra battery | Both connections use OkHttp with 10-second pings; negligible vs. camera streaming |
| Audio duplication — same `ByteArray` passed to two consumers | Audio chunks are consumed read-only; no mutation, so reference sharing is safe |
| Deepgram transcript lags Gemini's own transcription | Expected — Deepgram is additive. If they drift, we can suppress Gemini's transcript in UI |
| API key stored in SharedPreferences in plaintext | Same treatment as Gemini API key today — acceptable for a developer tool |

## Migration Plan

1. Add `deepgramAPIKey` to `SettingsManager` and `Secrets.kt.example`
2. Create `DeepgramSTTService.kt`
3. Wire fan-out in `GeminiSessionViewModel.startSession()`
4. Extend `GeminiUiState` with `deepgramTranscript`
5. Update `GeminiOverlayView` to display it
6. No migration needed for existing users — Deepgram is opt-in via API key presence

## Open Questions

- Should Gemini's `inputAudioTranscription` be disabled once Deepgram is stable, to reduce Gemini token usage?
- Should `endpointing` be user-configurable in Settings?
- Do we want to forward Deepgram final transcripts to Gemini as `clientContent` text turns (as a lower-latency input alternative)?
