## ADDED Requirements

### Requirement: Finalized turn transcript saved to Downloads folder
After the batch result for a turn is received and reconciled into the UI, the system SHALL write the finalized transcript text to a `.txt` file in the device's `Downloads` folder using `MediaStore.Downloads`. Each turn SHALL produce one file. No additional manifest permissions SHALL be required — `MediaStore.Downloads` on API 29+ requires no extra permissions for files the app creates.

#### Scenario: Batch result received and reconciled
- **WHEN** the batch result for a completed turn arrives and `speaker_turn` grouping is complete
- **THEN** a `.txt` file SHALL be written to `Downloads/VisionClaw/` via `MediaStore.Downloads` containing the finalized, speaker-attributed transcript text for that turn

#### Scenario: Transcript file already exists for that timestamp
- **WHEN** a file with the same computed name already exists in the Downloads folder
- **THEN** the existing file SHALL be overwritten (or a de-duplicated suffix applied); no exception SHALL propagate to the caller

#### Scenario: I/O error during save
- **WHEN** the `MediaStore.Downloads` insert or `OutputStream` write fails (e.g., disk full, I/O error)
- **THEN** the error SHALL be logged at `Log.e` level and swallowed — no exception SHALL propagate to the caller and the UI SHALL remain unaffected

### Requirement: Transcript file format and naming
Each transcript file SHALL be named `transcript_<unix_timestamp_ms>.txt` where `<unix_timestamp_ms>` is the wall-clock time at the moment the batch result is received. The file content SHALL be plain UTF-8 text with one line per `SpeakerTurn`, formatted as `Speaker <N>: <text>`. If no speaker attribution is available (all words defaulted to `speaker: 0` with no reliable diarization), the prefix SHALL be `Speaker 0:` as a baseline — no special-case format.

#### Scenario: Multi-speaker turn transcript
- **WHEN** the batch result contains `SpeakerTurn` structs with two distinct speaker indices
- **THEN** the file content SHALL have one line per `SpeakerTurn`, e.g.:
  ```
  Speaker 0: Hello, how are you?
  Speaker 1: I'm doing well, thanks.
  ```

#### Scenario: Single-speaker turn transcript
- **WHEN** the batch result contains a single `SpeakerTurn` (speaker 0 throughout)
- **THEN** the file SHALL contain a single line: `Speaker 0: <full transcript text>`

#### Scenario: Empty batch result
- **WHEN** the batch result contains an empty words array and no transcript text
- **THEN** no file SHALL be written

### Requirement: No additional manifest permissions
The system SHALL NOT declare `WRITE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, or any media permission in `AndroidManifest.xml` for this feature. Scoped storage via `MediaStore.Downloads` on API 29+ requires no additional permissions for writing files to the app's designated media collection.

#### Scenario: App runs without new permissions
- **WHEN** the app is installed fresh with no permission grants beyond the existing set
- **THEN** transcript saves SHALL succeed without any runtime permission prompt

### Requirement: Application context passed for MediaStore access
The component responsible for transcript file writes SHALL accept an `android.content.Context` (application context) to access the `ContentResolver` needed for `MediaStore.Downloads` operations.

#### Scenario: Save invoked with valid application context
- **WHEN** the batch result arrives and the save method is called with a valid application context
- **THEN** the `ContentResolver` SHALL be obtained from that context and used for all `MediaStore` operations
