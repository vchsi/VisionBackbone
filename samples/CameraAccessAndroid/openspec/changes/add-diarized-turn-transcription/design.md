## Context

`DeepgramSTTService` already maintains a streaming websocket connection and feeds `deepgramTranscript` into `GeminiUiState`. The existing batch upload path fires once per turn after the clip is finalized. This design extends both paths: the streaming side gains diarization params and proper interim/final handling; the batch side gains `diarize_model=latest`, per-word speaker fields, and `speaker_turn` grouping. A new `pending`/`finalized` flag on the transcript data model drives the live → batch reconciliation swap in the UI.

## Goals / Non-Goals

**Goals:**
- Live transcript window showing streamed words with provisional speaker labels during a turn
- Batch result supersedes streaming result once available — seamless UI swap, no duplicate entry
- `speaker_turn` structs written to intent graph with start/end timestamps preserved
- Streaming failures degrade gracefully; batch path unaffected

**Non-Goals:**
- Multichannel audio (`multichannel=true`)
- Using Deepgram endpointing/UtteranceEnd for turn boundaries — client decides
- Speaker naming or identity resolution
- Replacing the batch call as the intent-graph source of record

## Decisions

### 1. Fan-out audio to streaming websocket at turn start — no extra capture
The same `ByteArray` chunks already flowing to the batch buffer are forwarded to the streaming websocket. The `audioManager.onAudioCaptured` callback already fans out to multiple consumers (Gemini, `DeepgramSTTService`); streaming diarization hooks into the same point.

*Alternative considered*: Capture a second audio track for streaming. Rejected — doubles device-side complexity and the audio data is identical.

### 2. `diarize=true` on streaming (not `diarize_model`)
`diarize_model` is a batch-only parameter; sending it on a streaming request returns HTTP 400. The streaming diarizer is the Nova streaming diarizer regardless of model. The boolean `diarize=true` is the only valid flag.

### 3. Overwrite-not-append for interim results
`is_final: false` transcripts represent Deepgram's current best guess for the segment it's still processing. Appending them produces duplicated/contradictory text. The UI state holds a single `pendingSegment: String?` that is replaced on each interim result and cleared (moved to `finalizedLines`) on `is_final: true`.

### 4. Provisional vs. finalized visual distinction via data model flag
Rather than inferring display style from which path produced the text, the transcript data model carries an explicit `TranscriptLine.source: Source` (`STREAMING_PROVISIONAL` vs `BATCH_FINALIZED`). The UI maps source to visual weight (lighter for provisional). When the batch result arrives, all lines for that turn are replaced with `BATCH_FINALIZED` lines — a single state update, no animation needed.

### 5. `diarize_model=latest` on batch — mutually exclusive with `diarize=true`
Deepgram batch treats `diarize=true` and `diarize_model=<value>` as mutually exclusive: sending both results in undefined behavior. The batch call will use only `diarize_model=latest` (which implies diarization is enabled). This is distinct from the streaming call which uses only `diarize=true`.

### 6. `speaker_turn` grouping before intent-graph write
Consecutive words sharing the same `speaker` index are grouped into a `SpeakerTurn(speaker, start, end, text, words)`. Grouping is done client-side after parsing the batch response, before any storage write. Turn boundaries are flush: a speaker change, end of transcript, or punctuated sentence ending triggers a new group.

## Risks / Trade-offs

- **Silent diarization failure** (both paths): Deepgram may return `speaker: 0` for all words even with multiple distinct voices. → Validate with a real multi-speaker test clip at realistic turn length on both paths before considering complete.
- **Bandwidth doubling**: Running streaming websocket + buffering for batch roughly doubles outbound audio per turn. → Confirm acceptable given venue network conditions (flagged as risk in WiFi-presence-detection calibration notes).
- **UI reconciliation glitch**: Swapping provisional → finalized lines must happen atomically in the StateFlow update to avoid a flash of empty content. → Replace the entire turn's line list in a single `copy()` call.
- **Streaming websocket lifecycle edge cases**: If a turn ends before the websocket handshake completes, or the connection drops mid-turn, the batch path must be unaffected. → Streaming errors are caught and logged; no exception propagates to the batch path caller.

## Open Questions

- Should provisional speaker labels be suppressed entirely until the batch result arrives, or shown immediately with a visual cue? (Current design: show immediately with lighter weight — faster feedback, but may confuse if labels flip.)
- What is an acceptable latency budget for the batch → UI reconciliation swap? (No hard number yet; should feel near-instant post-turn.)
