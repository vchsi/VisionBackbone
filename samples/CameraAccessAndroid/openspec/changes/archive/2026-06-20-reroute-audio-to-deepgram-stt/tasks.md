## 1. Configuration & Secrets

- [x] 1.1 Add `deepgramAPIKey` property to `Secrets.kt.example` with placeholder value `YOUR_DEEPGRAM_API_KEY`
- [x] 1.2 Add `deepgramAPIKey` property to `Secrets.kt` (default to empty string if not present)
- [x] 1.3 Add `deepgramAPIKey: String` get/set to `SettingsManager` backed by SharedPreferences, falling back to `Secrets.deepgramAPIKey`
- [x] 1.4 Add Deepgram endpoint constant to `GeminiConfig` (or a new `DeepgramConfig` object): `wss://api.deepgram.com/v1/listen` with query params `encoding`, `sample_rate`, `channels`, `interim_results`, `endpointing`

## 2. DeepgramSTTService

- [x] 2.1 Create `gemini/DeepgramSTTService.kt` with a sealed `DeepgramConnectionState` (NotConfigured, Connecting, Connected, Error)
- [x] 2.2 Add `data class DeepgramTranscript(text: String, isFinal: Boolean, confidence: Double)` (can be in the same file or a types file)
- [x] 2.3 Implement WebSocket connection in `DeepgramSTTService.connect()` using OkHttp with `Authorization: Token <key>` header and the configured URL
- [x] 2.4 Implement `sendAudio(data: ByteArray)` that sends raw binary frames via `webSocket.send(ByteString)` when `connectionState == Connected`
- [x] 2.5 Implement `handleMessage(text: String)` to parse Deepgram JSON responses: extract `channel.alternatives[0].transcript` and `is_final`, skip empty transcripts, emit to `_transcript` StateFlow
- [x] 2.6 Implement `disconnect()` that closes the WebSocket cleanly (code 1000)
- [x] 2.7 Guard `sendAudio()` to silently no-op when `connectionState != Connected` (Deepgram down should not affect Gemini)

## 3. Wire Fan-out in GeminiSessionViewModel

- [x] 3.1 Instantiate `DeepgramSTTService` as a field in `GeminiSessionViewModel`
- [x] 3.2 In `startSession()`, after `audioManager.startCapture()` succeeds, call `deepgramService.connect()` if `SettingsManager.deepgramAPIKey` is non-empty
- [x] 3.3 Update `audioManager.onAudioCaptured` lambda to call `deepgramService.sendAudio(data)` in addition to `geminiService.sendAudio(data)` (parallel, no ordering requirement)
- [x] 3.4 In `stopSession()`, call `deepgramService.disconnect()`
- [x] 3.5 In `geminiService.onDisconnected` handler, also call `deepgramService.disconnect()`

## 4. UI State & Overlay

- [x] 4.1 Add `deepgramTranscript: String = ""` field to `GeminiUiState`
- [x] 4.2 In `GeminiSessionViewModel.startSession()`, observe `deepgramService.transcript` and update `_uiState.deepgramTranscript` on each emission (append interim, replace on final — or always replace for simplicity)
- [x] 4.3 Reset `deepgramTranscript` to `""` in the `geminiService.onTurnComplete` handler
- [x] 4.4 Update `GeminiOverlayView` (or `GeminiOverlayView.kt`) to display `deepgramTranscript` — show it as the live "you said:" line, styled to distinguish interim (italic/dimmed) vs. final text

## 5. Settings UI

- [x] 5.1 Add a Deepgram API key input field to `SettingsScreen.kt`, positioned after the Gemini API key field, reading/writing `SettingsManager.deepgramAPIKey`

## 6. Verification

- [ ] 6.1 Build and install the app; open Settings and enter a valid Deepgram API key
- [ ] 6.2 Start a Gemini session and confirm `DeepgramSTTService` connects (check Logcat for WebSocket open)
- [ ] 6.3 Speak and confirm interim transcripts appear in the overlay in real time
- [ ] 6.4 Confirm final transcripts are marked `isFinal=true` in logs
- [ ] 6.5 Stop the session and confirm both Gemini and Deepgram WebSockets close cleanly
- [ ] 6.6 Test with empty Deepgram key: confirm Gemini session starts normally and no Deepgram connection is attempted
