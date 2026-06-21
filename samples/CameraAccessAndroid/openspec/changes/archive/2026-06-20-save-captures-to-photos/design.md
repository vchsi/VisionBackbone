## Context

`ImageAPIService.sendFrame(bitmap: Bitmap)` is currently a no-op stub called at 1 fps from `GeminiSessionViewModel.sendVideoFrameIfThrottled`. The app targets minSdk 31, meaning scoped storage is enforced — apps cannot write to arbitrary external paths. The correct API is `MediaStore.Images.Media` with a `ContentResolver` insert, which requires `Application` context.

`ImageAPIService` is currently a plain class (no Android context). It needs access to a `ContentResolver` to use `MediaStore`. The simplest fix is to pass `Application` context at construction — consistent with how `GeminiSessionViewModel` already extends `AndroidViewModel(application)`.

## Goals / Non-Goals

**Goals:**
- Save each 1 fps frame as a JPEG to `Pictures/VisionClaw/` on the device, visible in the Gallery app
- Zero new manifest permissions (scoped storage on API 31+ requires none for own-app media writes)
- Fail silently on I/O errors — a save failure must never crash the session

**Non-Goals:**
- Managing disk quota or deleting old frames
- Configurable output directory or format
- Saving at a different rate than the existing 1 fps image API throttle

## Decisions

**Pass `Application` context into `ImageAPIService`**
`MediaStore` requires a `ContentResolver`, which requires a `Context`. Rather than making `ImageAPIService` a singleton or using a static context, we pass `Application` at construction from the ViewModel (which already holds `application`). This avoids memory leaks and is consistent with Android best practices.

**`MediaStore` over `File` API**
On API 29+ the `File` API to `Environment.getExternalStoragePublicDirectory` is deprecated and blocked without `WRITE_EXTERNAL_STORAGE` (itself not grantable on API 31+ for media). `MediaStore.Images.Media` is the correct scoped-storage path and automatically surfaces files in the Gallery.

**`JPEG` at existing `VIDEO_JPEG_QUALITY` (50)**
Reuse the existing quality constant rather than introducing a separate one. If quality needs tuning for debugging, it's already a single constant in `GeminiConfig`.

**`saveEnabled` flag defaulting to `true`**
Allows disabling saves (e.g., for production builds) without a code change. Can be wired to a `SettingsManager` preference later; for now it's a constructor parameter defaulting `true`.

**Silent error handling**
An I/O failure during `MediaStore` insert is logged at `Log.e` level but swallowed — a disk-full or permissions error must not disrupt the audio session.

## Risks / Trade-offs

- **Disk usage at 1 fps**: JPEG at quality 50 is ~5–15 KB/frame. At 1 fps that's ~40–50 MB/hour. → Acceptable for short debugging sessions; document in logcat that frames are being saved.
- **`ContentResolver.insert` on main thread**: Must be called off the main thread. → `sendFrame` is already called from the ViewModel's coroutine scope implicitly via the camera callback; if needed, dispatch to `Dispatchers.IO` inside `sendFrame`.
- **Context leak**: Passing `Application` (not `Activity`) context avoids leaks entirely.
