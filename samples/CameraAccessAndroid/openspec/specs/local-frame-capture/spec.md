### Requirement: Save 1 fps frames to device photo library
The system SHALL save each bitmap received by `ImageAPIService.sendFrame()` as a JPEG file to the device's `Pictures/VisionClaw` directory using `MediaStore.Images.Media`, without requiring any additional manifest permissions beyond those already declared.

#### Scenario: Frame saved successfully
- **WHEN** `ImageAPIService.sendFrame(bitmap)` is called and `saveEnabled` is `true`
- **THEN** a JPEG file named `frame_<unix_timestamp_ms>.jpg` SHALL be written to `Pictures/VisionClaw/` and SHALL be visible in the device Gallery app

#### Scenario: Save disabled
- **WHEN** `saveEnabled` is `false`
- **THEN** `sendFrame` SHALL no-op without writing any file

#### Scenario: I/O error during save
- **WHEN** `MediaStore.Images.Media` insert fails (e.g., disk full, I/O error)
- **THEN** the error SHALL be logged at `Log.e` level and swallowed — no exception SHALL propagate to the caller

### Requirement: No additional manifest permissions
The system SHALL NOT declare `WRITE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, or any media permission in `AndroidManifest.xml` for this feature. Scoped storage via `MediaStore` on API 31+ requires no additional permissions for writing to the app's own media collection.

#### Scenario: App runs without new permissions
- **WHEN** the app is installed fresh with no permission grants beyond the existing set
- **THEN** frame saves SHALL succeed without any runtime permission prompt

### Requirement: Application context passed to ImageAPIService
`ImageAPIService` SHALL accept an `android.app.Application` instance at construction time to access the `ContentResolver` needed for `MediaStore` operations.

#### Scenario: ViewModel constructs ImageAPIService with application context
- **WHEN** `GeminiSessionViewModel` initializes `ImageAPIService`
- **THEN** it SHALL pass `getApplication<Application>()` to the constructor
