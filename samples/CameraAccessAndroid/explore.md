# Codebase Summary: VisionClaw (CameraAccessAndroid)

An Android companion app for Meta Ray-Ban smart glasses that streams camera video, sends it to Gemini Live for real-time voice AI, and can relay the feed over WebRTC to a remote viewer.

---

## Overall Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                         UI Layer (Compose)                      │
│   HomeScreen / StreamScreen / NonStreamScreen / SettingsScreen  │
└────────────┬───────────────────────────────────────────────────┘
             │ StateFlow (UiState)
┌────────────▼───────────────────────────────────────────────────┐
│                      ViewModel Layer                            │
│   WearablesViewModel  StreamViewModel  GeminiSessionViewModel   │
│   WebRTCSessionViewModel                                        │
└────────────┬────────────────┬──────────────────────────────────┘
             │                │
    ┌────────▼──────┐  ┌──────▼───────────────────────────────┐
    │ DAT SDK       │  │  Service Layer                        │
    │ (Wearables)   │  │  GeminiLiveService  WebRTCClient      │
    │ StreamSession │  │  AudioManager       SignalingClient    │
    └───────────────┘  └──────────────────────────────────────┘
```

---

## Image Input

### Source 1 — Glasses Camera (primary)
```
Meta Ray-Ban glasses
    └─▶ DAT SDK: Wearables.startStreamSession()
            └─▶ StreamSession.videoStream  (Flow<VideoFrame>)
                    └─▶ VideoFrame: raw I420 bytes (ByteBuffer)
                            └─▶ convertI420toNV21()
                                    └─▶ YuvImage → compressToJpeg(q=50)
                                            └─▶ BitmapFactory.decodeByteArray()
                                                    └─▶ Bitmap  ← canonical image object
```
File: `stream/StreamViewModel.kt:handleVideoFrame()`

### Source 2 — Phone Camera (fallback)
```
Android rear camera (CameraX)
    └─▶ ImageAnalysis.Analyzer  (YUV_420_888 ImageProxy)
            └─▶ YuvImage NV21 → compressToJpeg(q=80)
                    └─▶ Bitmap + rotation correction (EXIF/rotationDegrees)
                            └─▶ Bitmap  ← canonical image object
```
File: `phone/PhoneCameraManager.kt`

Both paths converge on a `Bitmap` that is fanned out to three consumers (UI, Gemini, WebRTC).

---

## Image Output

| Destination | How |
|---|---|
| **UI preview** | `StreamUiState.videoFrame` → `StreamScreen` renders via `Image()` composable |
| **Gemini Live** | `GeminiSessionViewModel.sendVideoFrameIfThrottled()` → throttled to 1 fps → JPEG base64 over WebSocket |
| **WebRTC peer** | `WebRTCSessionViewModel.pushVideoFrame()` → `CustomVideoCapturer.pushFrame()` → WebRTC `VideoSource` every frame |
| **Photo share** | `StreamSession.capturePhoto()` → `PhotoData.Bitmap` or `PhotoData.HEIC` → decoded with EXIF transform → `SharePhotoDialog` |

Photo capture in HEIC mode: EXIF orientation is read via `ExifInterface` and a `Matrix` rotation/flip is applied before display.

---

## Audio Input

```
Android microphone
    └─▶ AudioRecord (16 kHz, mono, PCM16, VOICE_COMMUNICATION source)
            └─▶ read() loop on dedicated thread
                    └─▶ accumulate until ≥ 3200 bytes (~100 ms)
                            └─▶ onAudioCaptured callback
                                    └─▶ GeminiLiveService.sendAudio()
                                            └─▶ base64-encode
                                                    └─▶ WebSocket JSON:
                                                        { realtimeInput: { audio: {
                                                            mimeType: "audio/pcm;rate=16000",
                                                            data: "<base64>"
                                                        }}}
```
File: `gemini/AudioManager.kt`

**Echo prevention (phone mode only):** mic is muted while `GeminiLiveService.isModelSpeaking == true`.

WebRTC also creates its own `AudioSource`/`AudioTrack` via `PeerConnectionFactory` for the WebRTC stream (separate path, device mic).

---

## Audio Output

```
Gemini WebSocket response
    └─▶ serverContent.modelTurn.parts[].inlineData
            └─▶ mimeType: "audio/pcm"  (24 kHz, mono, PCM16)
                    └─▶ Base64.decode()
                            └─▶ AudioManager.playAudio()
                                    └─▶ AudioTrack (24 kHz, CHANNEL_OUT_MONO, PCM16,
                                                    USAGE_VOICE_COMMUNICATION, MODE_STREAM)
```

If the model is interrupted (user speaks), `stopPlayback()` pauses and flushes the AudioTrack.

---

## LLM Usage (Gemini Live)

**Model:** `gemini-2.5-flash-native-audio-preview-12-2025`  
**Transport:** WebSocket to `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent`

### Session Setup (sent on WebSocket open)
```json
{
  "setup": {
    "model": "models/gemini-2.5-flash-native-audio-preview-12-2025",
    "generationConfig": {
      "responseModalities": ["AUDIO"],
      "thinkingConfig": { "thinkingBudget": 0 }
    },
    "systemInstruction": "<configurable via SettingsManager>",
    "tools": [{ "functionDeclarations": [...] }],
    "realtimeInputConfig": {
      "automaticActivityDetection": {
        "disabled": false,
        "startOfSpeechSensitivity": "START_SENSITIVITY_HIGH",
        "endOfSpeechSensitivity": "END_SENSITIVITY_LOW",
        "silenceDurationMs": 500
      },
      "activityHandling": "START_OF_ACTIVITY_INTERRUPTS",
      "turnCoverage": "TURN_INCLUDES_ALL_INPUT"
    },
    "contextWindowCompression": {
      "slidingWindow": { "targetTokens": 80000 }
    },
    "inputAudioTranscription": {},
    "outputAudioTranscription": {}
  }
}
```

### Inputs sent during session
| Type | Rate | Format |
|---|---|---|
| Audio | Continuous, ~100ms chunks | PCM16, 16 kHz, base64 in `realtimeInput.audio` |
| Video | 1 fps (throttled) | JPEG q=50, base64 in `realtimeInput.video` |
| Text | On-demand | `clientContent.turns[role=user].parts[].text` |
| Tool responses | After tool call | Raw JSON via `sendToolResponse()` |

### Outputs received
| Type | Where |
|---|---|
| Audio | `serverContent.modelTurn.parts[].inlineData` (PCM, 24 kHz) |
| Input transcription | `serverContent.inputTranscription.text` |
| Output transcription | `serverContent.outputTranscription.text` |
| Tool calls | Top-level `toolCall.functionCalls[]` |
| Turn complete | `serverContent.turnComplete = true` |
| Interrupted | `serverContent.interrupted = true` |

### System prompt (default)
The default prompt positions the AI as a voice interface for someone wearing the glasses, with access to exactly one tool (`execute`) that delegates all persistent actions to OpenClaw. The AI is instructed to never pretend to do things itself and to verbally acknowledge before calling the tool.

---

## Data Layer

### Persistence
- **`SettingsManager`** (`settings/SettingsManager.kt`): `SharedPreferences` named `visionclaw_settings`
  - Gemini API key, system prompt
  - OpenClaw host/port/tokens (ignored per scope)
  - WebRTC signaling URL
  - Feature flags: `videoStreamingEnabled`, `proactiveNotificationsEnabled`
- **`Secrets.kt`**: compile-time default values (gitignored; `Secrets.kt.example` is the template)

### In-Memory State (MVVM with StateFlow)
| ViewModel | State |
|---|---|
| `WearablesViewModel` | DAT registration, device list, permissions |
| `StreamViewModel` | streaming mode (GLASSES/PHONE), current video frame Bitmap, captured photo |
| `GeminiSessionViewModel` | connection state, isModelSpeaking, user/AI transcripts, tool call status |
| `WebRTCSessionViewModel` | ICE connection state, room code, signaling status |

No database. All state is ephemeral (in-memory StateFlow) or stored in SharedPreferences.

---

## WebRTC Layer (video relay to remote viewer)

```
Glasses/Phone Bitmap frames
    └─▶ CustomVideoCapturer.pushFrame(bitmap)
            └─▶ NV21Buffer → VideoFrame → VideoSource → VideoTrack
                    └─▶ WebRTC PeerConnection (UNIFIED_PLAN)
                            └─▶ SignalingClient (WebSocket)
                                    └─▶ Configurable signaling server (wss://...)
                                            └─▶ SDP offer/answer + ICE candidates
                                                    └─▶ STUN: stun.l.google.com:19302
                                                    └─▶ TURN: visionclaw-turn-creds.fly.dev/credentials
```

Purpose: allows a second viewer (e.g., a desktop browser) to receive a live video stream from the glasses. Not connected to Gemini at all — parallel output path.

---

## Data Flow Summary

```
                    ┌──────────────┐
                    │  Glasses DAT │
                    │  (I420 raw)  │
                    └──────┬───────┘
                           │ VideoFrame
              ┌────────────▼──────────────────┐
              │     StreamViewModel            │
              │  convertI420→NV21→JPEG→Bitmap  │
              └─┬──────────┬──────────┬────────┘
                │          │          │
         ┌──────▼──┐  ┌────▼───┐  ┌──▼──────────────┐
         │  UI     │  │Gemini  │  │  WebRTC          │
         │ preview │  │1fps    │  │  CustomCapturer  │
         └─────────┘  │JPEG b64│  └──────────────────┘
                      └────┬───┘
                           │ WebSocket
                  ┌────────▼────────────┐
                  │  Gemini Live API    │
                  │  (audio+video in)   │
                  │  (audio out)        │
                  └────────┬────────────┘
                           │ PCM 24kHz
              ┌────────────▼──────────┐
              │  AudioManager         │
              │  AudioTrack playback  │
              └───────────────────────┘

     ┌──────────────────────────────────┐
     │  AudioManager (mic)              │
     │  AudioRecord 16kHz PCM16 →      │
     │  GeminiLiveService.sendAudio()  │
     └──────────────────────────────────┘
```