## Context

The app currently dispatches camera frames to Gemini at 1 fps via `GeminiSessionViewModel.sendVideoFrameIfThrottled`, gated by `VIDEO_FRAME_INTERVAL_MS = 1000L`. The display (on-screen camera preview rendered from the same bitmap) is implicitly tied to the same 1 fps rate. There is no current pathway to an external image API.

The change decouples three concerns that are currently entangled in one code path: display refresh rate, Gemini video input, and future external API calls. Gemini's vision is being removed from the loop entirely — it will remain active for audio only.

## Goals / Non-Goals

**Goals:**
- Display stream updates at 5 fps (200 ms throttle), independent of API send rate
- Image API stub receives frames at 1 fps (1000 ms throttle), independent of display rate
- Gemini receives no video frames
- Stub is activatable later without structural refactoring

**Non-Goals:**
- Actual HTTP POST implementation (stub only)
- Dynamic rate control or adaptive bitrate
- Settings UI for the image API endpoint (config via `Secrets.kt` and `SettingsManager` only)
- Authentication beyond a configurable bearer token stub field

## Decisions

**Two independent throttles, not one**
`sendVideoFrameIfThrottled` is renamed/refactored to maintain two `lastFrameTime` fields: `lastDisplayFrameTime` (200 ms gate) and `lastImageAPIFrameTime` (1000 ms gate). Each is checked separately on every camera callback. This avoids coupling display refresh to API send rate.

**Remove Gemini video send, not gate it**
Rather than adding a feature flag, we simply remove the `geminiService.sendVideoFrame(bitmap)` call. Gemini's audio-only session is unaffected. If Gemini vision is needed later it can be re-added as a third throttle path.

**Rename `VIDEO_FRAME_INTERVAL_MS` to `DISPLAY_FRAME_INTERVAL_MS`**
The old constant conflated two responsibilities. Splitting into `DISPLAY_FRAME_INTERVAL_MS = 200L` and `IMAGE_API_FRAME_INTERVAL_MS = 1000L` makes intent explicit.

**Stub pattern over feature flag** (unchanged from original design)
`ImageAPIService.sendFrame()` is a no-op when `imageAPIEndpointURL` is unconfigured, consistent with how Deepgram and OpenClaw are gated.

## Risks / Trade-offs

- **Display at 5 fps vs camera native rate**: The camera surface itself may run faster; the 5 fps gate only affects the bitmap path in the ViewModel (e.g., overlay rendering or ML pre-processing). If the display surface is driven directly by the camera hardware, this throttle has no visual effect — which is fine. → Mitigation: document that this throttle applies to the bitmap dispatch path, not the camera surface.
- **Removing Gemini video**: Any Gemini prompts that relied on visual context will now get audio only. → Accepted trade-off; Gemini vision was not the target backend.
- **1 fps image API stub**: Stub does nothing, so the rate is only validated at implementation time. → Mitigation: `lastImageAPIFrameTime` tracking is implemented now even though `sendFrame` is a no-op, so the rate logic is correct from day one.
