## 1. Config

- [x] 1.1 Add `DEEPGRAM_TTS_WEBSOCKET_URL` constant to `GeminiConfig.kt`: `wss://api.deepgram.com/v1/speak?model=aura-2-en&encoding=linear16&sample_rate=24000`

## 2. DeepgramTTSService

- [x] 2.1 Create `DeepgramTTSService.kt` with OkHttp WebSocket client (same config as `DeepgramSTTService`: 0ms read timeout, 10s ping interval)
- [x] 2.2 Add `connect(apiKey: String, onAudio: (ByteArray) -> Unit)` — opens WebSocket to `DEEPGRAM_TTS_WEBSOCKET_URL` with `Authorization: Token <key>` header; stores `onAudio` callback
- [x] 2.3 In `onMessage(bytes: ByteString)`, forward raw bytes to `onAudio` callback (these are PCM frames)
- [x] 2.4 Add `speak(text: String)` — sends `{"type":"Speak","text":"<text>"}` then `{"type":"Flush"}` over the open WebSocket; no-ops if not connected
- [x] 2.5 Add `disconnect()` — closes WebSocket with code 1000, clears callback

## 3. GeminiSessionViewModel Integration

- [x] 3.1 Instantiate `DeepgramTTSService` as a private field in `GeminiSessionViewModel`
- [x] 3.2 In `startDirectVoiceSession()`, call `ttsService.connect(GeminiConfig.deepgramAPIKey) { data -> audioManager.playAudio(data) }` after audio capture starts (guard with `isDeepgramConfigured`)
- [x] 3.3 After a successful `delegateTask()` result in the Deepgram `is_final` collector, call `ttsService.speak(result.result)` if the result text is non-blank
- [x] 3.4 In `stopDirectVoiceSession()`, call `ttsService.disconnect()`

## 4. Verification

- [ ] 4.1 Start Direct Voice mode, speak a command, confirm in logcat that `DeepgramTTSService` connects and sends Speak+Flush messages when an agent response arrives
- [ ] 4.2 Confirm audio plays back through the glasses/phone speaker after the agent responds
- [ ] 4.3 Confirm stopping Direct Voice mode disconnects the TTS WebSocket cleanly (logcat shows close 1000)
<!-- NOTE: TTS model fixed from aura-2-en (403) → aura-asteria-en. Full e2e verification blocked on OpenClaw server reachability (port 18789 unreachable on current network). -->
