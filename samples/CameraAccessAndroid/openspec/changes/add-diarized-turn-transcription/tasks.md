## 1. Streaming Websocket Lifecycle

- [x] 1.1 Add diarization and smart-format params to `DeepgramSTTService` websocket query string: `diarize=true`, `smart_format=true`, `punctuate=true` (do NOT add `diarize_model` — invalid on streaming)
- [x] 1.2 Add `speaker: Int?` field to `DeepgramTranscript` data class; update all parse sites to extract `speaker` from the word/alternative JSON (nullable — default to `null` when absent)
- [x] 1.3 Create a turn-scoped streaming websocket connection class (or extend `DeepgramSTTService`) that opens at turn start and closes at turn end, distinct from the session-scoped connection

## 2. Audio Fan-out to Turn-Scoped Websocket

- [x] 2.1 In `GeminiSessionViewModel`, wire `audioManager.onAudioCaptured` to forward chunks to the turn-scoped streaming connection in addition to existing consumers
- [x] 2.2 Ensure the fan-out is non-blocking — streaming websocket failure MUST NOT prevent audio from reaching the batch buffer

## 3. Interim / Final Result Rendering

- [x] 3.1 Add `pendingSegment: String?` and `finalizedLines: List<TranscriptLine>` to the transcript window data model (or extend `GeminiUiState`)
- [x] 3.2 Add `TranscriptLine.source: Source` enum (`STREAMING_PROVISIONAL` / `BATCH_FINALIZED`) to drive visual distinction
- [x] 3.3 Wire `is_final: false` results to overwrite `pendingSegment` in the StateFlow (not append)
- [x] 3.4 Wire `is_final: true` results to move text into `finalizedLines` with `source = STREAMING_PROVISIONAL` and clear `pendingSegment`

## 4. Provisional Visual Distinction in the UI

- [x] 4.1 Update `GeminiOverlayView` (or transcript window composable) to render `STREAMING_PROVISIONAL` lines with lighter text weight or a subtle visual indicator
- [x] 4.2 Verify `BATCH_FINALIZED` lines render with normal weight, confirming the two styles are visually distinguishable

## 5. Batch Path: diarize_model=latest

- [x] 5.1 Add `diarize_model=latest` query param to the existing batch upload call (`POST https://api.deepgram.com/v1/listen`)
- [x] 5.2 Remove `diarize=true` from the batch call if present — these params are mutually exclusive on batch requests

## 6. Batch Response Parsing

- [x] 6.1 Parse `speaker: Int` and `speaker_confidence: Float` from each word in `results.channels[0].alternatives[0].words[]`; add both to the word data model
- [x] 6.2 Default `speaker` to `0` and `speaker_confidence` to `null` when fields are absent (silent diarization failure — must not throw)

## 7. Speaker Turn Grouping

- [x] 7.1 Implement `groupIntoSpeakerTurns(words: List<Word>): List<SpeakerTurn>` — group consecutive same-speaker words; each turn carries `speaker`, `start` (first word), `end` (last word), `text`, `words`
- [x] 7.2 Handle edge cases: empty word list → empty list; single speaker throughout → single turn
- [x] 7.3 Call `groupIntoSpeakerTurns` on the parsed batch word list before writing to the intent graph

## 8. Intent Graph Schema

- [ ] 8.1 Add `SpeakerTurn` type to the intent graph schema: `speaker: Int`, `start: Float`, `end: Float`, `text: String`, `words: List<Word>`
- [ ] 8.2 Update the intent graph write path to store `List<SpeakerTurn>` for each completed turn

## 9. Batch → UI Reconciliation

- [x] 9.1 When the batch result arrives for a completed turn, replace all `STREAMING_PROVISIONAL` transcript lines for that turn with `BATCH_FINALIZED` lines in a single `StateFlow` `copy()` call
- [x] 9.2 Verify the swap is atomic — no intermediate state where the transcript window is empty or shows both versions

## 10. Websocket Failure Handling

- [x] 10.1 Wrap streaming websocket connection, message handling, and close in try/catch; log failures at `Log.w`/`Log.e` and swallow — no exception propagates to batch path
- [x] 10.2 If turn ends while streaming websocket is not open (e.g., never connected or already dropped), no-op cleanly and allow batch upload to proceed

## 11. Transcript File Save to Downloads

- [x] 11.1 Create a `TranscriptSaver` helper (or add a method to the batch result handler) that accepts `List<SpeakerTurn>` and an application `Context`; writes a UTF-8 `.txt` file to `Downloads/VisionClaw/` via `MediaStore.Downloads`
- [x] 11.2 Format file content as one line per `SpeakerTurn`: `Speaker <N>: <text>`; skip write entirely if `SpeakerTurn` list is empty
- [x] 11.3 Name each file `transcript_<System.currentTimeMillis()>.txt`
- [x] 11.4 Wrap all `MediaStore` and `OutputStream` calls in try/catch; log failures at `Log.e` and swallow — no exception propagates to caller
- [x] 11.5 Call the save method from the batch result reconciliation site in `GeminiSessionViewModel`, after `groupIntoSpeakerTurns` returns and before or during the `StateFlow` update
- [x] 11.6 Confirm `AndroidManifest.xml` requires no new permissions for this feature

## 12. Verification

- [ ] 12.1 Build and deploy to glasses; start a session and speak — confirm the transcript window updates in real time with provisional speaker labels during the turn
- [ ] 12.2 Confirm interim results overwrite (not append) while speaking; confirm finalized lines appear as each `is_final: true` result arrives
- [ ] 12.3 Confirm that after the turn ends, the batch result replaces the streaming content in the UI with finalized speaker-attributed lines
- [ ] 12.4 Test with two distinct speakers at realistic Recall turn length — confirm both streaming and batch diarization assign distinct speaker indices (validate against silent-failure mode where all words return `speaker: 0`)
- [ ] 12.5 Confirm `SpeakerTurn` structs in the intent graph have correct `start`/`end` timestamps matching per-word timestamps from the batch response
- [ ] 12.6 Kill the streaming websocket mid-turn (e.g., network interrupt); confirm batch upload still fires and the UI reconciles correctly afterward
- [ ] 12.7 After a completed turn, open the Files app and confirm a `transcript_<timestamp>.txt` file appears in `Downloads/VisionClaw/` with correct `Speaker N: <text>` formatting
