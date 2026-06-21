### Requirement: Image API endpoint configuration
The system SHALL support an optional `imageAPIEndpointURL` configuration key in `Secrets.kt` and `SettingsManager`. When the key is empty or set to the placeholder value `"YOUR_IMAGE_API_ENDPOINT"`, the image API service SHALL be considered unconfigured and all frame sends SHALL be no-ops.

#### Scenario: Endpoint not configured
- **WHEN** `imageAPIEndpointURL` is empty or equals `"YOUR_IMAGE_API_ENDPOINT"`
- **THEN** `ImageAPIService.sendFrame()` SHALL silently no-op and log a warning at most once per session

#### Scenario: Endpoint configured
- **WHEN** `imageAPIEndpointURL` is a non-empty string that differs from the placeholder
- **THEN** `ImageAPIService.isConfigured` SHALL return `true`

### Requirement: Image API service stub interface
The system SHALL provide an `ImageAPIService` class with a `sendFrame(bitmap: Bitmap)` method. In this release, the method SHALL be a no-op stub. The class SHALL be instantiated in `GeminiSessionViewModel` and called from the video dispatch path at a rate independent of the display refresh rate.

#### Scenario: Frame dispatched to image API while unconfigured
- **WHEN** the image API throttle interval has elapsed and `ImageAPIService.isConfigured` is `false`
- **THEN** `ImageAPIService.sendFrame` SHALL be called and SHALL no-op without error

#### Scenario: Frame dispatched to image API while configured (stub behavior)
- **WHEN** the image API throttle interval has elapsed and `ImageAPIService.isConfigured` is `true`
- **THEN** `ImageAPIService.sendFrame` SHALL be called with the bitmap and SHALL no-op (HTTP call not yet implemented)

### Requirement: Independent frame rate throttles
The system SHALL maintain two independent throttles in the video dispatch path: one for the display stream at 5 fps (200 ms) and one for the image API at 1 fps (1000 ms). Gemini SHALL receive no video frames.

#### Scenario: Display throttle at 5 fps
- **WHEN** the video dispatch path is called more frequently than every 200 ms
- **THEN** display-path processing SHALL be skipped for calls within 200 ms of the last display frame

#### Scenario: Image API throttle at 1 fps
- **WHEN** the video dispatch path is called more frequently than every 1000 ms
- **THEN** `ImageAPIService.sendFrame` SHALL be skipped for calls within 1000 ms of the last image API frame

#### Scenario: No video sent to Gemini
- **WHEN** the video dispatch path is called under any conditions
- **THEN** `geminiService.sendVideoFrame` SHALL NOT be called
