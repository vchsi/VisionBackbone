## ADDED Requirements

### Requirement: DeepgramSTTService connects to Deepgram streaming API
The system SHALL open a persistent WebSocket connection to `wss://api.deepgram.com/v1/listen` when the Gemini session starts, using the configured Deepgram API key for authentication. Connection parameters SHALL be `encoding=linear16`, `sample_rate=16000`, `channels=1`, `interim_results=true`, `endpointing=300`.

#### Scenario: Successful connection
- **WHEN** `startSession()` is called and a valid Deepgram API key is configured
- **THEN** `DeepgramSTTService` SHALL open a WebSocket to the Deepgram endpoint and transition `connectionState` to `Connected`

#### Scenario: Missing API key
- **WHEN** `startSession()` is called and the Deepgram API key is empty or equals the placeholder value
- **THEN** `DeepgramSTTService` SHALL NOT attempt to connect and SHALL leave `connectionState` as `NotConfigured`

#### Scenario: Connection failure
- **WHEN** the WebSocket connection fails (network error or auth rejection)
- **THEN** `DeepgramSTTService` SHALL set `connectionState` to `Error` and SHALL NOT retry automatically

### Requirement: Audio is fanned out to Deepgram in parallel with Gemini
The system SHALL forward every PCM16 audio chunk produced by `AudioManager` to both `GeminiLiveService` and `DeepgramSTTService` without modification. Neither consumer SHALL block the other.

#### Scenario: Audio delivered to both services
- **WHEN** `AudioManager.onAudioCaptured` fires with a `ByteArray`
- **THEN** the same bytes SHALL be passed to `GeminiLiveService.sendAudio()` AND `DeepgramSTTService.sendAudio()` within the same callback

#### Scenario: Deepgram connection down, Gemini unaffected
- **WHEN** `DeepgramSTTService` is in `Error` or `NotConfigured` state and audio arrives
- **THEN** `GeminiLiveService.sendAudio()` SHALL still be called normally; Deepgram SHALL silently drop the chunk

### Requirement: Deepgram transcript events are exposed as a StateFlow
`DeepgramSTTService` SHALL expose a `StateFlow<DeepgramTranscript?>` where `DeepgramTranscript` contains `text: String`, `isFinal: Boolean`, and `confidence: Double`. Interim results (isFinal=false) SHALL update the flow as they arrive. Final results (isFinal=true) SHALL also update the flow.

#### Scenario: Interim transcript received
- **WHEN** Deepgram sends a transcript response with `is_final: false`
- **THEN** `transcript` StateFlow SHALL emit a `DeepgramTranscript` with `isFinal=false` and the partial text

#### Scenario: Final transcript received
- **WHEN** Deepgram sends a transcript response with `is_final: true`
- **THEN** `transcript` StateFlow SHALL emit a `DeepgramTranscript` with `isFinal=true` and the final text

#### Scenario: Empty transcript skipped
- **WHEN** Deepgram sends a response with an empty or blank transcript string
- **THEN** `transcript` StateFlow SHALL NOT be updated

### Requirement: Deepgram connection lifecycle is coupled to the Gemini session
`DeepgramSTTService` SHALL be started when `GeminiSessionViewModel.startSession()` is called (after Gemini connects successfully) and SHALL be stopped when `stopSession()` is called or the Gemini connection is lost.

#### Scenario: Session start wires Deepgram
- **WHEN** `startSession()` completes Gemini setup successfully
- **THEN** `DeepgramSTTService.connect()` SHALL be called

#### Scenario: Session stop tears down Deepgram
- **WHEN** `stopSession()` is called or Gemini fires `onDisconnected`
- **THEN** `DeepgramSTTService.disconnect()` SHALL be called, closing the WebSocket cleanly

### Requirement: Deepgram API key is configurable via SettingsManager
`SettingsManager` SHALL expose a `deepgramAPIKey: String` property backed by SharedPreferences, defaulting to `Secrets.deepgramAPIKey`. The Settings UI SHALL include a field for this key alongside the existing Gemini API key field.

#### Scenario: Key read from preferences
- **WHEN** the app starts and a Deepgram API key has been saved to SharedPreferences
- **THEN** `SettingsManager.deepgramAPIKey` SHALL return the saved value

#### Scenario: Key falls back to compile-time secret
- **WHEN** no key has been saved to SharedPreferences
- **THEN** `SettingsManager.deepgramAPIKey` SHALL return `Secrets.deepgramAPIKey`

### Requirement: Deepgram transcript is surfaced in the UI overlay
`GeminiUiState` SHALL include a `deepgramTranscript: String` field. The `GeminiOverlayView` SHALL display the Deepgram transcript text alongside (or in place of) the Gemini input transcription, updating in real time as interim and final results arrive.

#### Scenario: Interim transcript displayed
- **WHEN** `DeepgramSTTService` emits an interim `DeepgramTranscript`
- **THEN** `GeminiUiState.deepgramTranscript` SHALL be updated and the overlay SHALL render the new text

#### Scenario: Transcript cleared on turn complete
- **WHEN** `GeminiLiveService.onTurnComplete` fires
- **THEN** `GeminiUiState.deepgramTranscript` SHALL be reset to empty string
