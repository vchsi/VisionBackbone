## ADDED Requirements

### Requirement: Streaming websocket opens on turn start
The system SHALL open a Deepgram streaming websocket (`wss://api.deepgram.com/v1/listen`) with query params `model=nova-3&diarize=true&smart_format=true&interim_results=true&punctuate=true` and `Authorization: Token <key>` header when a new turn capture begins. The `diarize_model` parameter SHALL NOT be included — it is invalid on streaming requests and causes a 400 error.

#### Scenario: Turn starts with Deepgram configured
- **WHEN** a new turn capture begins and `GeminiConfig.isDeepgramConfigured` is `true`
- **THEN** the streaming websocket SHALL be opened before the first audio chunk is sent

#### Scenario: Turn starts with Deepgram not configured
- **WHEN** a new turn capture begins and `GeminiConfig.isDeepgramConfigured` is `false`
- **THEN** no streaming websocket SHALL be opened and audio SHALL flow only to the batch buffer

### Requirement: Audio fan-out to streaming websocket
The system SHALL forward each captured audio chunk to the streaming websocket in addition to the existing batch buffer, using the same 16kHz mono PCM16 format already produced by `AudioManager`. This SHALL NOT require a second audio capture session.

#### Scenario: Audio chunk captured during active turn
- **WHEN** `audioManager.onAudioCaptured` fires during an active turn with an open streaming websocket
- **THEN** the audio chunk SHALL be sent to the streaming websocket AND SHALL also continue flowing to the existing batch buffer

#### Scenario: Streaming websocket drops mid-turn
- **WHEN** the streaming websocket connection drops during a turn
- **THEN** the audio chunk SHALL still flow to the batch buffer and the drop SHALL be logged but not surfaced to the user

### Requirement: Interim results overwrite, final results lock in
The system SHALL render streaming `Result` messages into the UI transcript window. Interim results (`is_final: false`) SHALL overwrite the current pending segment display rather than append to it. A result with `is_final: true` SHALL lock in the segment — moving it to the finalized-lines list — and clear the pending segment.

#### Scenario: Interim result arrives
- **WHEN** a streaming `Result` message arrives with `is_final: false`
- **THEN** the pending segment in the transcript window SHALL be replaced with the new transcript text, not appended

#### Scenario: Final result arrives
- **WHEN** a streaming `Result` message arrives with `is_final: true`
- **THEN** the transcript text SHALL be appended to the finalized-lines list and the pending segment SHALL be cleared

#### Scenario: No results arrive (connection silent)
- **WHEN** the streaming websocket is open but no `Result` messages arrive for the duration of a turn
- **THEN** the transcript window SHALL show no streaming content without error; batch path SHALL be unaffected

### Requirement: Provisional speaker labels displayed with visual distinction
If a streaming `Result` includes `speaker` fields on words or alternatives, the system SHALL display provisional speaker attribution in the transcript window. Provisional speaker labels SHALL be visually distinct from finalized (batch) speaker labels — for example, lighter text weight or a subtle indicator — to communicate to the user that these may change.

#### Scenario: Streaming result includes speaker field
- **WHEN** a streaming `Result` with `is_final: true` includes `speaker` indices on words
- **THEN** the transcript window SHALL display the speaker index alongside the text with a provisional visual style

#### Scenario: Streaming result omits speaker field
- **WHEN** a streaming `Result` contains no `speaker` field (diarization silent failure)
- **THEN** the transcript window SHALL render the text without any speaker label, with no error surfaced

### Requirement: Streaming websocket closes on turn end
The system SHALL close the streaming websocket when the client determines the turn has ended (the same signal that triggers the batch upload). Deepgram's UtteranceEnd or endpointing SHALL NOT be used to decide turn boundaries.

#### Scenario: Turn ends normally
- **WHEN** the client signals turn end
- **THEN** the streaming websocket SHALL be closed with a normal close frame

#### Scenario: Turn end while websocket is already closed (e.g., after a drop)
- **WHEN** the client signals turn end and the streaming websocket is not open
- **THEN** the system SHALL no-op without error and the batch upload SHALL proceed normally

### Requirement: Streaming failures degrade gracefully without blocking batch
Streaming websocket errors (connection failures, malformed messages, reconnect failures) SHALL be caught, logged at `Log.w` or `Log.e`, and silently swallowed from the user's perspective. The batch upload path SHALL never observe or depend on streaming websocket state.

#### Scenario: Streaming websocket fails to connect
- **WHEN** the websocket handshake fails at turn start
- **THEN** the system SHALL log the error, leave the transcript window empty or in a "transcribing…" fallback state, and allow the batch path to proceed normally

#### Scenario: Streaming websocket throws during message processing
- **WHEN** an exception is thrown while parsing a streaming `Result`
- **THEN** the exception SHALL be caught and logged, and the websocket SHALL be closed cleanly; the batch path SHALL be unaffected

### Requirement: Batch result supersedes streaming result in UI
When the batch result for a turn arrives, the system SHALL replace the streaming-provisional transcript lines for that turn with the batch-finalized lines in a single atomic state update. The UI SHALL NOT show both versions simultaneously or leave the provisional version as the permanent on-screen record.

#### Scenario: Batch result arrives after streaming has populated lines
- **WHEN** the batch result for a completed turn arrives and streaming transcript lines are present in the UI
- **THEN** all transcript lines for that turn SHALL be replaced with batch-finalized lines in a single `StateFlow` update

#### Scenario: Batch result arrives with no prior streaming content
- **WHEN** the batch result arrives and the streaming path produced no content (e.g., websocket never connected)
- **THEN** the batch-finalized lines SHALL be written to the UI transcript as if no streaming had occurred
