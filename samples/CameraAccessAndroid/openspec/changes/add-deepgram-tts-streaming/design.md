## Context

Deepgram's TTS WebSocket API (`wss://api.deepgram.com/v1/speak`) accepts text chunks sent as JSON `{"type":"Speak","text":"..."}` messages and streams back binary Linear16 PCM audio frames at 24kHz mono (matching `OUTPUT_AUDIO_SAMPLE_RATE` already configured for Gemini). A `{"type":"Flush"}` message signals end of input and triggers the final audio drain.

The `AudioManager.playAudio(ByteArray)` method already handles raw PCM16 output — Gemini's audio uses this exact path. The TTS service only needs to deliver bytes to it; no audio format changes are needed.

Deepgram TTS WebSocket flow:
1. Open socket with model + encoding params in the URL
2. Send `{"type":"Speak","text":"<response text>"}` 
3. Receive binary PCM frames → forward to `AudioManager.playAudio()`
4. Send `{"type":"Flush"}` to drain remaining audio
5. Wait for `{"type":"Flushed"}` then close

## Goals / Non-Goals

**Goals:**
- Stream Deepgram TTS audio for OpenClaw agent responses in Direct Voice mode
- Begin playback as soon as the first PCM frame arrives (low-latency streaming, not wait-for-full-response)
- Reuse existing Deepgram API key and `AudioManager.playAudio()` output path
- Clean connect/disconnect lifecycle tied to Direct Voice session

**Non-Goals:**
- TTS in AI (Gemini) mode — Gemini already produces its own audio
- Voice selection UI — use Deepgram's default `aura-2-en` model for now
- Queuing multiple concurrent TTS requests — one response at a time (current agent call is blocking anyway)
- Interruption / barge-in during TTS playback

## Decisions

**Decision 1: WebSocket over REST**

Chosen: WebSocket streaming. The REST TTS endpoint (`POST /v1/speak`) returns the full audio only after all text is processed — latency is proportional to response length. WebSocket starts returning frames within ~200ms of the first text chunk. For longer agent responses this is noticeably better.

**Decision 2: Send full response text in one `Speak` message, then `Flush`**

Chosen: send the complete `delegateTask()` result as a single Speak message followed immediately by Flush. The alternative (streaming text word-by-word) is only useful when the text source itself is streaming (e.g. an LLM token stream). Since `delegateTask()` returns a single complete string, chunking it adds complexity with no latency benefit.

**Decision 3: Sample rate**

Deepgram TTS default output is 24kHz Linear16. `AudioManager.audioTrack` is already configured at `OUTPUT_AUDIO_SAMPLE_RATE = 24000`. No resampling needed.

**Decision 4: Lifecycle — connect per-response vs persistent session**

Chosen: connect once per Direct Voice session (on `startDirectVoiceSession()`), disconnect on `stopDirectVoiceSession()`. Keeps the socket warm between commands; Deepgram's TTS WebSocket supports multiple Speak/Flush cycles on a single connection.

**Decision 5: Interrupt STT while TTS is playing**

Not handled in this change. The STT WebSocket stays open during TTS playback. In practice the mic picks up the speaker output, but since there's no Gemini voice activity detection here, it may produce spurious STT finals while TTS is playing. Mitigation deferred — can gate `deepgramService.sendAudio()` on a `isTTSPlaying` flag in a follow-up.

## Risks / Trade-offs

- [Risk] STT picks up TTS output and triggers another OpenClaw call → Mitigation: defer to follow-up; frequency is low for short agent responses
- [Risk] Network latency spike causes audio stuttering → Mitigation: OkHttp WebSocket buffers frames; `AudioManager` plays them as they arrive with no frame-size constraint
- [Risk] Deepgram TTS WebSocket session expires mid-session → Mitigation: reconnect on next response if socket is closed
