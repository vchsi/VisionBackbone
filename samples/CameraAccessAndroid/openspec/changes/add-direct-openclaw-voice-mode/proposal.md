## Why

The current AI mode routes all speech through Gemini, which introduces latency and processing overhead for users who simply want to issue a direct command to their OpenClaw agent. A dedicated push-to-talk button mode lets users bypass Gemini entirely — speech is transcribed by Deepgram, the text is sent straight to OpenClaw, and the agent response is displayed on screen.

## What Changes

- Add a **Direct Voice** mode button to the stream screen UI alongside the existing AI toggle
- While held (or toggled on), audio is captured and streamed to the existing Deepgram WebSocket STT service
- On each Deepgram `is_final`, the transcribed text is sent directly to `OpenClawBridge.delegateTask()` — no Gemini involvement
- OpenClaw agent response is displayed in the overlay as a new response line
- Mode is mutually exclusive with AI mode (can't run both simultaneously)

## Capabilities

### New Capabilities
- `direct-openclaw-voice-mode`: A push-to-talk or toggle button that transcribes speech via Deepgram and routes it directly to OpenClaw, displaying the agent's response in the UI overlay without involving Gemini

### Modified Capabilities
- `deepgram-stt`: Deepgram STT service is now shared between AI mode and Direct Voice mode (no spec-level requirement change — same service, new consumer)

## Impact

- `StreamScreen.kt` — new button added to the control bar
- `GeminiSessionViewModel.kt` — add Direct Voice mode state + session management; OR new `DirectVoiceViewModel.kt`
- `GeminiOverlayView.kt` — display OpenClaw response text when in Direct Voice mode
- `OpenClawBridge.kt` — no changes needed (already has `delegateTask`)
- `DeepgramSTTService.kt` — no changes needed (reused as-is)
- Mutually exclusive with `isGeminiActive`; shares the mic and Deepgram connection