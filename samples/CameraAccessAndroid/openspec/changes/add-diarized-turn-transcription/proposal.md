## Why

Recall captures per-turn audio and sends it to Deepgram's batch endpoint after each turn — producing accurate, speaker-attributed transcripts that get written into the intent graph. The gap: users see nothing while they're still talking. This change adds a parallel live-transcript window powered by a streaming Deepgram websocket, so the UI shows a real-time feed without displacing or weakening the batch path that remains the source of record.

## What Changes

- Open a Deepgram streaming websocket (`wss://api.deepgram.com/v1/listen`) when a turn capture starts; close it when the turn ends
- Stream the same audio already being buffered for batch upload — fan-out, no extra capture
- Render interim and final streaming results into the UI transcript window, with interim results overwritten (not appended) as they arrive
- Display provisional speaker labels from the streaming diarizer; visually distinguish them as provisional since streaming diarization is less reliable than batch
- Add `diarize_model=latest` to the existing batch call (batch-only parameter; mutually exclusive with `diarize=true`)
- Parse `speaker` and `speaker_confidence` per word from batch results
- Group consecutive same-speaker words into `speaker_turn` structs before writing to the intent graph
- Once the batch result arrives, reconcile the UI transcript window to show the finalized, more accurate speaker-attributed version in place of the provisional streaming version

The two paths are intentionally separate and serve different purposes:

| | Streaming (new) | Batch (existing, unchanged in role) |
|---|---|---|
| Purpose | Live transcript window while speaking | Source of truth → intent graph |
| Trigger | Opens on turn start, closes on turn end | Fires once after turn clip is final |
| Diarization param | `diarize=true` (only option on streaming) | `diarize_model=latest` (v2 batch diarizer) |
| Accuracy | Lower; speaker labels may flicker early | Higher; what downstream features trust |
| Failure handling | Silent degradation; batch still succeeds | Must succeed |

Do not "simplify" these into a single path — the latency/accuracy tradeoff is the design.

## Capabilities

### New Capabilities
- `streaming-live-transcript`: Deepgram streaming websocket lifecycle, interim/final result rendering, provisional speaker display, failure handling without blocking batch
- `batch-speaker-diarization`: `diarize_model=latest` on batch call, per-word `speaker`/`speaker_confidence` parsing, `speaker_turn` grouping, reconciliation with live transcript once batch arrives

### Modified Capabilities
- `deepgram-stt`: Existing streaming connection extended with diarization params and transcript window wiring; batch call gains `diarize_model=latest`

## Impact

- `DeepgramSTTService.kt`: Add `diarize=true`, `smart_format=true`, `interim_results=true`, `punctuate=true` to streaming params; handle interim (`is_final:false`) results separately from final results
- Batch upload call site: Add `diarize_model=latest`; update response parser for `speaker`/`speaker_confidence`; add `speaker_turn` grouping logic
- `GeminiUiState` / transcript window data model: Add `pending`/`finalized` state flag for the live → batch reconciliation swap
- `GeminiSessionViewModel.kt`: Wire streaming transcript updates into UI state; wire batch result reconciliation
- Intent graph schema: Add `speaker_turn` type (`speaker: Int`, `start: Float`, `end: Float`, `text: String`, `words: List<Word>`)
- No new manifest permissions; no changes to turn-boundary logic (client-decided, unchanged)
