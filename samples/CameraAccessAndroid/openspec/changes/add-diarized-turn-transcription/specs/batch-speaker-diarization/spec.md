## ADDED Requirements

### Requirement: Batch request includes diarize_model=latest
The system SHALL include `diarize_model=latest` in the batch upload query params sent to `POST https://api.deepgram.com/v1/listen`. The `diarize=true` boolean param SHALL NOT be present on the same request — the two are mutually exclusive on batch requests.

#### Scenario: Batch upload fires after turn end
- **WHEN** the batch upload request is constructed after a turn ends
- **THEN** the query string SHALL include `diarize_model=latest` and SHALL NOT include `diarize=true`

#### Scenario: Batch response contains per-word speaker fields
- **WHEN** the batch response is received with `speaker` and `speaker_confidence` fields in `results.channels[0].alternatives[0].words[]`
- **THEN** both fields SHALL be parsed and stored per word

### Requirement: Per-word speaker and speaker_confidence fields parsed
The system SHALL parse `speaker: Int` and `speaker_confidence: Float` from each word object in `results.channels[0].alternatives[0].words[]` of the batch response. These fields SHALL be stored alongside the existing `start`, `end`, and `word` fields on the word data model.

#### Scenario: Batch response word includes speaker field
- **WHEN** a word object in the batch response contains `"speaker": 1, "speaker_confidence": 0.92`
- **THEN** the parsed word model SHALL have `speaker = 1` and `speakerConfidence = 0.92`

#### Scenario: Batch response word omits speaker field (silent diarization failure)
- **WHEN** a word object in the batch response contains no `speaker` field
- **THEN** the parsed word model SHALL default `speaker` to `0` and `speakerConfidence` to `null`, and no exception SHALL be thrown

### Requirement: Consecutive same-speaker words grouped into speaker_turn structs
The system SHALL group consecutive words sharing the same `speaker` index into `SpeakerTurn` structs before writing to the intent graph. Each `SpeakerTurn` SHALL carry: `speaker: Int`, `start: Float` (first word's start), `end: Float` (last word's end), `text: String` (concatenated words), `words: List<Word>`.

#### Scenario: Two speakers alternate in a transcript
- **WHEN** a batch response contains words `[{speaker:0,...}, {speaker:0,...}, {speaker:1,...}, {speaker:0,...}]`
- **THEN** the grouping result SHALL be three `SpeakerTurn` structs with `speaker` values `[0, 1, 0]` in order

#### Scenario: Single speaker throughout
- **WHEN** all words in a batch response share `speaker: 0`
- **THEN** the grouping result SHALL be a single `SpeakerTurn` with `speaker = 0` spanning the full transcript

#### Scenario: Empty transcript
- **WHEN** the batch response contains an empty words array
- **THEN** the grouping result SHALL be an empty list with no error

### Requirement: Word-level start/end timestamps preserved in speaker_turn
Each `Word` within a `SpeakerTurn.words` list SHALL retain the original `start` and `end` timestamps from the batch response without modification. The `SpeakerTurn.start` and `SpeakerTurn.end` SHALL be derived from the first and last word's timestamps respectively.

#### Scenario: Speaker turn start and end derived from words
- **WHEN** a `SpeakerTurn` is constructed from words with timestamps `[{start:0.0, end:0.5}, {start:0.6, end:1.1}]`
- **THEN** `SpeakerTurn.start` SHALL be `0.0` and `SpeakerTurn.end` SHALL be `1.1`
