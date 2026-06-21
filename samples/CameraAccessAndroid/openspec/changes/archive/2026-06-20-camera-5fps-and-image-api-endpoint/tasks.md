## 1. Constants

- [x] 1.1 In `GeminiConfig.kt`, rename `VIDEO_FRAME_INTERVAL_MS` to `DISPLAY_FRAME_INTERVAL_MS` and set it to `200L`
- [x] 1.2 In `GeminiConfig.kt`, add `const val IMAGE_API_FRAME_INTERVAL_MS = 1000L`

## 2. Configuration

- [x] 2.1 In `Secrets.kt.example`, add `const val imageAPIEndpointURL = "YOUR_IMAGE_API_ENDPOINT"`
- [x] 2.2 In `SettingsManager.kt`, add `imageAPIEndpointURL` preference backed by `Secrets.imageAPIEndpointURL`

## 3. ImageAPIService Stub

- [x] 3.1 Create `gemini/ImageAPIService.kt` with `isConfigured: Boolean` (true when URL is set and not placeholder) and `sendFrame(bitmap: Bitmap)` (no-op; logs warning at most once per session if unconfigured)

## 4. ViewModel Wiring

- [x] 4.1 In `GeminiSessionViewModel`, add `private val imageAPIService = ImageAPIService()` field and `private var lastImageAPIFrameTime: Long = 0`
- [x] 4.2 Rename `lastVideoFrameTime` to `lastDisplayFrameTime`
- [x] 4.3 Rewrite `sendVideoFrameIfThrottled`: remove `geminiService.sendVideoFrame(bitmap)` call; add display throttle (200 ms gate) and image API throttle (1000 ms gate) as independent checks; call `imageAPIService.sendFrame(bitmap)` when image API interval has elapsed
- [x] 4.4 Update any reference to the old `VIDEO_FRAME_INTERVAL_MS` constant in the ViewModel (now `DISPLAY_FRAME_INTERVAL_MS`)

## 5. Verification

- [x] 5.1 Build succeeds: `./gradlew assembleDebug`
- [ ] 5.2 On device: confirm logcat shows no video frame messages from `GeminiLiveService` during an active session
- [ ] 5.3 On device: confirm display updates visibly faster than before (5 fps vs 1 fps)
- [ ] 5.4 On device: confirm no crashes or regressions in Gemini audio or Deepgram STT
