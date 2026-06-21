## Why

Gemini Live handles both STT and LLM response in one opaque pipeline, which makes it impossible to access raw transcription independently or to swap out the STT engine. Routing audio through Deepgram STT over a persistent WebSocket gives us a separate, low-latency transcription stream that can be used to drive other UI or logic independently of Gemini's turn lifecycle.

## What Changes

- `AudioManager` continues to capture mic audio at 16 kHz PCM16 as today
- A new `DeepgramSTTService` opens a persistent WebSocket to the Deepgram streaming STT API and forwards captured audio chunks in real time
- `GeminiLiveService` continues to receive the same audio chunks for LLM processing (no change to existing behavior)
- Deepgram transcription results (interim and final) are surfaced as a `StateFlow` that the UI and `GeminiSessionViewModel` can consume
- `SettingsManager` gains a `deepgramAPIKey` preference
- `GeminiConfig` / `Secrets.kt.example` updated with Deepgram config constants
- Gemini's own `inputAudioTranscription` remains enabled but can optionally be disabled once Deepgram is validated

## Capabilities

### New Capabilities
- `deepgram-stt`: Real-time speech-to-text via Deepgram's WebSocket streaming API, running in parallel with Gemini audio input. Produces interim and final transcript events.

### Modified Capabilities
- (none — existing Gemini audio flow is unchanged)

## Impact

- **New dependency**: Deepgram Streaming Speech API (WebSocket, `wss://api.deepgram.com/v1/listen`)
- **New files**: `gemini/DeepgramSTTService.kt`
- **Modified files**: `gemini/GeminiSessionViewModel.kt` (wire Deepgram alongside Gemini audio), `gemini/GeminiConfig.kt` (Deepgram constants), `settings/SettingsManager.kt` (Deepgram API key), `Secrets.kt.example`
- **UI**: `GeminiOverlayView` / `GeminiUiState` gain a `deepgramTranscript` field for display
- **No breaking changes** to existing Gemini or WebRTC paths
