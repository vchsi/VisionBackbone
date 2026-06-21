## Context

The app currently has two modes: idle and AI mode (Gemini active). AI mode handles the full loop — Deepgram STT feeds the live transcript overlay, and Gemini routes tool calls to OpenClaw via `ToolCallRouter → OpenClawBridge.delegateTask()`. 

For Direct Voice mode we want to short-circuit Gemini entirely: user speaks → Deepgram transcribes → `OpenClawBridge.delegateTask()` called directly → response shown in overlay. The `OpenClawBridge` already maintains conversation history and handles the HTTP round-trip. `DeepgramSTTService` already emits `is_final` results. Both can be reused as-is.

The stream screen control bar currently has an AI toggle button. Direct Voice mode adds a second button that is mutually exclusive with AI mode.

## Goals / Non-Goals

**Goals:**
- Push-to-talk or toggle button in the stream UI for Direct Voice mode
- Deepgram `is_final` transcripts sent directly to `OpenClawBridge.delegateTask()` (no Gemini)
- Agent response text displayed in the overlay
- Mutual exclusion with AI mode — only one mode active at a time
- Reuse existing `DeepgramSTTService`, `AudioManager`, and `OpenClawBridge` without modification

**Non-Goals:**
- Streaming the OpenClaw response (it's a single blocking HTTP call already)
- Visual push-to-hold UX (toggle is sufficient and simpler)
- Saving Direct Voice transcripts to files (AI mode already handles this separately)
- Running Direct Voice and AI mode concurrently

## Decisions

**Decision 1: Add state to `GeminiSessionViewModel` vs new ViewModel**

Chosen: extend `GeminiSessionViewModel`. It already owns `AudioManager`, `DeepgramSTTService`, and `OpenClawBridge`. Creating a separate ViewModel would duplicate all of these or require complex sharing. The mutual exclusion logic (`isGeminiActive` vs `isDirectVoiceActive`) also naturally lives in one place.

Alternative considered: `DirectVoiceViewModel` — cleaner separation, but duplicates audio/deepgram lifecycle management and creates a coordination problem for mutual exclusion.

**Decision 2: Trigger on `is_final` with accumulation vs single-shot per-turn**

Chosen: send each `is_final` segment directly to OpenClaw as a task. This keeps latency low — the agent starts processing as soon as a complete thought is transcribed. The Deepgram EOT settings (`eot_threshold=0.85`, `eot_timeout_ms=6500`) already give natural sentence boundaries.

Alternative considered: accumulate finals and send on silence (like transcript saving) — adds unnecessary latency for an interactive agent interaction.

**Decision 3: Response display location**

Chosen: add `directVoiceResponse: String?` to `GeminiUiState` and render it in `GeminiOverlayView` — same overlay system. No new overlay component needed.

**Decision 4: Button interaction model**

Chosen: toggle (tap to start, tap to stop), consistent with the existing AI mode toggle. PTT (hold) is more ergonomic for short queries but harder to implement reliably on glasses hardware where button events may be indirect.

## Risks / Trade-offs

- [Risk] Deepgram finals arrive mid-thought and trigger OpenClaw before the user finishes speaking → Mitigation: the `eot_threshold=0.85` + `eot_timeout_ms=6500` Flux EOT settings already lengthen boundaries; each final is still a meaningful sentence
- [Risk] OpenClaw `delegateTask` is a blocking 120s-timeout HTTP call — if the agent hangs, the UI appears stuck → Mitigation: show `ToolCallStatus.Executing` in the overlay while waiting (already wired for AI mode)
- [Risk] User accidentally activates Direct Voice while AI mode is on → Mitigation: mutual exclusion enforced in ViewModel; Direct Voice button disabled/hidden when AI mode is active

## Open Questions

- Should Direct Voice mode stop automatically after a configurable silence timeout, or require an explicit button tap to end the session?
