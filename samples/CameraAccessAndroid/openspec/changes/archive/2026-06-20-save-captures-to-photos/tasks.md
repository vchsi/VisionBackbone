## 1. ImageAPIService — add Application context and save logic

- [x] 1.1 Change `ImageAPIService` constructor to accept `application: Application` parameter; store as `private val context: Context = application.applicationContext`
- [x] 1.2 Add `val saveEnabled: Boolean = true` constructor parameter
- [x] 1.3 Implement `sendFrame(bitmap: Bitmap)`: when `saveEnabled` is `true` and `isConfigured` check passes (or always for save purposes — save regardless of API config), insert JPEG into `MediaStore.Images.Media` using `ContentValues` with `DISPLAY_NAME = "frame_${System.currentTimeMillis()}.jpg"`, `MIME_TYPE = "image/jpeg"`, `RELATIVE_PATH = "Pictures/VisionClaw"`, `IS_PENDING = 1`; write bitmap bytes via `contentResolver.openOutputStream`; then clear `IS_PENDING = 0`
- [x] 1.4 Wrap the entire MediaStore block in `try/catch`, logging errors with `Log.e(TAG, "Failed to save frame", e)` and swallowing the exception

## 2. ViewModel — pass Application context

- [x] 2.1 In `GeminiSessionViewModel`, update `ImageAPIService()` instantiation to `ImageAPIService(getApplication())`

## 3. Verification

- [x] 3.1 Build succeeds: `./gradlew assembleDebug`
- [ ] 3.2 Install on device and start a session; after ~10 seconds stop the session
- [ ] 3.3 Open the Gallery app on device and confirm JPEG files appear under `Pictures/VisionClaw` at ~1 fps
- [ ] 3.4 Confirm no crashes and no permission prompts appear during the session
