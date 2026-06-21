## Why

When the OpenClaw agent responds to a Direct Voice query, the response is currently text-only in the overlay. For eyes-free use (e.g. wearing glasses while moving), users need the response spoken aloud. Deepgram's TTS WebSocket API enables smooth, low-latency streaming speech — audio begins playing before the full response is received — which is essential for longer agent responses.

## What Changes

- New `DeepgramTTSService` that opens a WebSocket to Deepgram's TTS streaming endpoint, sends agent response text, and streams back Linear16 PCM audio chunks
- After `delegateTask()` returns a successful response in Direct Voice mode, the response text is piped to `DeepgramTTSService`, which streams audio to `AudioManager.playAudio()`
- Reuses the existing Deepgram API key (no new credentials)
- TTS WebSocket URL added to `GeminiConfig` alongside the existing STT URLs

## Capabilities

### New Capabilities
- `deepgram-tts-streaming`: A streaming TTS service that connects to Deepgram's TTS WebSocket, sends text incrementally, and plays back PCM audio through the existing audio output path as it arrives

### Modified Capabilities

## Impact

- `GeminiConfig.kt` — add `DEEPGRAM_TTS_WEBSOCKET_URL` constant
- `DeepgramTTSService.kt` (new) — WebSocket client that sends text and streams audio back
- `GeminiSessionViewModel.kt` — instantiate `DeepgramTTSService`, pipe OpenClaw response text to it after each successful `delegateTask()` call, stop/disconnect on session end
- `AudioManager.kt` — no changes needed; `playAudio()` already accepts raw PCM bytes
