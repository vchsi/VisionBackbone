## Why

The current camera pipeline sends video frames to Gemini at 1 fps and has no pathway to an external image analysis API. Gemini's vision adds latency and cost without being the intended long-term vision backend. The live display refresh is also coupled to the Gemini send rate, making the on-screen preview sluggish. Separating the display rate from the API rate lets us show a fluid 5 fps preview while keeping external API traffic at a conservative 1 fps.

## What Changes

- Stop sending video frames to Gemini entirely (Gemini remains active for audio only)
- Update the live Android display stream throttle to 5 fps (`DISPLAY_FRAME_INTERVAL_MS = 200L`)
- Add a stub `ImageAPIService` class (no-op implementation) throttled at 1 fps (`IMAGE_API_FRAME_INTERVAL_MS = 1000L`), with the interface ready to POST JPEG frames to a configurable external endpoint
- Add `imageAPIEndpointURL` to `Secrets.kt.example` and `SettingsManager` as an optional config key
- Wire the stub into `GeminiSessionViewModel`'s video dispatch path

## Capabilities

### New Capabilities

- `image-api-endpoint`: Stub service and configuration for routing captured video frames to an external HTTP image analysis API at 1 fps (POST JPEG, configurable URL, not yet active)

### Modified Capabilities

(none — audio pipeline and Deepgram STT are unaffected)

## Impact

- `GeminiConfig.kt`: rename `VIDEO_FRAME_INTERVAL_MS` to `DISPLAY_FRAME_INTERVAL_MS = 200L`; add `IMAGE_API_FRAME_INTERVAL_MS = 1000L`
- `GeminiSessionViewModel.kt`: `sendVideoFrameIfThrottled` split into separate display (5fps) and image API (1fps) throttle paths; remove `geminiService.sendVideoFrame()` call
- New file: `gemini/ImageAPIService.kt` (stub — does nothing until endpoint is configured)
- `Secrets.kt.example`: new `imageAPIEndpointURL` field
- `settings/SettingsManager.kt`: new `imageAPIEndpointURL` preference
- No changes to audio pipeline, Deepgram STT, or WebRTC
