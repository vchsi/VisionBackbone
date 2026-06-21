## MODIFIED Requirements

### Requirement: DeepgramSTTService connects to Deepgram streaming API
The system SHALL open a persistent WebSocket connection to `wss://api.deepgram.com/v1/listen` when the Gemini session starts, using the configured Deepgram API key for authentication. Connection parameters SHALL be `encoding=linear16`, `sample_rate=16000`, `channels=1`, `interim_results=true`, `endpointing=300`, `diarize=true`, `smart_format=true`, `punctuate=true`. The `diarize_model` parameter SHALL NOT be included — it is invalid on streaming requests and causes a 400 error.

#### Scenario: Successful connection
- **WHEN** `startSession()` is called and a valid Deepgram API key is configured
- **THEN** `DeepgramSTTService` SHALL open a WebSocket to the Deepgram endpoint with all required params and transition `connectionState` to `Connected`

#### Scenario: Missing API key
- **WHEN** `startSession()` is called and the Deepgram API key is empty or equals the placeholder value
- **THEN** `DeepgramSTTService` SHALL NOT attempt to connect and SHALL leave `connectionState` as `NotConfigured`

#### Scenario: Connection failure
- **WHEN** the WebSocket connection fails (network error or auth rejection)
- **THEN** `DeepgramSTTService` SHALL set `connectionState` to `Error` and SHALL NOT retry automatically

### Requirement: Deepgram transcript events are exposed as a StateFlow
`DeepgramSTTService` SHALL expose a `StateFlow<DeepgramTranscript?>` where `DeepgramTranscript` contains `text: String`, `isFinal: Boolean`, `confidence: Double`, and `speaker: Int?` (nullable — absent when diarization does not return a speaker field). Interim results (`isFinal=false`) SHALL update the flow as they arrive. Final results (`isFinal=true`) SHALL also update the flow.

#### Scenario: Interim transcript received
- **WHEN** Deepgram sends a transcript response with `is_final: false`
- **THEN** `transcript` StateFlow SHALL emit a `DeepgramTranscript` with `isFinal=false` and the partial text

#### Scenario: Final transcript received with speaker field
- **WHEN** Deepgram sends a transcript response with `is_final: true` and a `speaker` field present
- **THEN** `transcript` StateFlow SHALL emit a `DeepgramTranscript` with `isFinal=true`, the final text, and the non-null `speaker` value

#### Scenario: Final transcript received without speaker field
- **WHEN** Deepgram sends a transcript response with `is_final: true` and no `speaker` field (diarization silent failure)
- **THEN** `transcript` StateFlow SHALL emit a `DeepgramTranscript` with `isFinal=true`, the final text, and `speaker = null`

#### Scenario: Empty transcript skipped
- **WHEN** Deepgram sends a response with an empty or blank transcript string
- **THEN** `transcript` StateFlow SHALL NOT be updated
