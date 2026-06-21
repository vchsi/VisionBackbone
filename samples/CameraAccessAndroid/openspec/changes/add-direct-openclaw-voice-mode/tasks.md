## 1. ViewModel State

- [x] 1.1 Add `isDirectVoiceActive: Boolean = false` and `directVoiceResponse: String? = null` fields to `GeminiUiState`
- [x] 1.2 Add `startDirectVoiceSession()` function to `GeminiSessionViewModel` — guards against starting if OpenClaw is not configured, stops AI mode if active, opens mic and Deepgram connection
- [x] 1.3 Add `stopDirectVoiceSession()` function to `GeminiSessionViewModel` — stops audio capture, disconnects Deepgram, clears `directVoiceResponse` in UI state
- [x] 1.4 In the Deepgram `is_final` collector inside `startDirectVoiceSession()`, skip blank transcripts, then call `openClawBridge.delegateTask(text)` and update `directVoiceResponse` with the result on success

## 2. Mutual Exclusion

- [x] 2.1 In `startSession()` (AI mode), call `stopDirectVoiceSession()` first if `isDirectVoiceActive` is true
- [x] 2.2 In `startDirectVoiceSession()`, call `stopSession()` first if `isGeminiActive` is true
- [x] 2.3 Ensure `stopDirectVoiceSession()` is called in `onCleared()` alongside `stopSession()`

## 3. UI — Button

- [x] 3.1 Add a "Direct Voice" toggle button to `StreamScreen.kt` control bar, visible only when OpenClaw is configured (`openClawConnectionState != NotConfigured`)
- [x] 3.2 Wire button `onClick` to `geminiViewModel.startDirectVoiceSession()` / `geminiViewModel.stopDirectVoiceSession()` based on `isDirectVoiceActive`
- [x] 3.3 Disable the Direct Voice button when AI mode is starting/connecting (guard against race during mode switch)

## 4. UI — Overlay Response

- [x] 4.1 In `GeminiOverlayView.kt`, render `directVoiceResponse` as a distinct text block in the overlay when non-null (use a slightly different color or label prefix like "Agent:" to distinguish from STT transcript lines)
- [x] 4.2 Clear `directVoiceResponse` from the overlay when Direct Voice mode stops

## 5. Guard: OpenClaw Not Configured

- [x] 5.1 In `startDirectVoiceSession()`, return early without changing UI state if `!GeminiConfig.isOpenClawConfigured`

## 6. Verification

- [x] 6.1 Start Direct Voice mode, speak a command, confirm Deepgram final appears in logcat and OpenClaw agent is called
- [x] 6.2 Confirm agent response text appears in the overlay
- [x] 6.3 Confirm toggling AI mode while Direct Voice is active stops Direct Voice first (and vice versa)
- [x] 6.4 Confirm stopping Direct Voice clears the response text from the overlay
