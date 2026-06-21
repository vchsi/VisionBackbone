## Why

The `ImageAPIService` stub currently receives frames at 1 fps but does nothing with them. Before wiring up an external API, we need a way to visually verify that the capture pipeline is working correctly — saving each frame locally to the device's photo library provides a direct, inspectable artifact without needing a running server.

## What Changes

- In `ImageAPIService.sendFrame()`, save each received JPEG bitmap to the device's `Pictures/VisionClaw` folder via `MediaStore` (scoped storage, no legacy permissions needed on API 31+)
- Declare no additional manifest permissions — `MediaStore` on API 29+ with scoped storage requires none for writing to the app's own media collection
- Add a `saveEnabled: Boolean` flag (default `true`) so saving can be disabled without removing the code
- The file naming convention is `frame_<timestamp>.jpg`

## Capabilities

### New Capabilities

- `local-frame-capture`: Saves each 1 fps video frame as a JPEG to `Pictures/VisionClaw` on the device using `MediaStore`, with no extra permissions required

### Modified Capabilities

(none)

## Impact

- `gemini/ImageAPIService.kt`: `sendFrame()` gains actual save logic via `MediaStore.Images.Media`
- `AndroidManifest.xml`: no new permissions needed (minSdk 31, scoped storage handles it)
- No changes to `GeminiSessionViewModel`, `GeminiConfig`, or any other file
