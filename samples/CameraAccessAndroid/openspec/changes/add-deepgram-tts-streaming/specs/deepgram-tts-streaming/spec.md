## ADDED Requirements

### Requirement: TTS service connects on Direct Voice session start
The app SHALL connect `DeepgramTTSService` to the Deepgram TTS WebSocket when a Direct Voice session starts, reusing the existing Deepgram API key. The connection SHALL be established once per session and reused across multiple agent responses.

#### Scenario: Direct Voice session starts with Deepgram configured
- **WHEN** `startDirectVoiceSession()` is called and the Deepgram API key is configured
- **THEN** `DeepgramTTSService` opens a WebSocket connection to the Deepgram TTS endpoint

#### Scenario: Deepgram not configured
- **WHEN** `startDirectVoiceSession()` is called and no Deepgram API key is set
- **THEN** `DeepgramTTSService` does not attempt to connect and TTS is silently skipped

### Requirement: Agent response text is spoken via streaming TTS
After a successful OpenClaw `delegateTask()` call in Direct Voice mode, the app SHALL send the response text to `DeepgramTTSService`, which SHALL stream the resulting PCM audio to `AudioManager.playAudio()` as frames arrive.

#### Scenario: Successful agent response
- **WHEN** `delegateTask()` returns a non-empty success result while Direct Voice mode is active
- **THEN** the response text is sent to `DeepgramTTSService` and audio playback begins within one WebSocket round-trip

#### Scenario: Empty or failed agent response
- **WHEN** `delegateTask()` returns a failure or an empty string
- **THEN** no TTS call is made

### Requirement: TTS WebSocket sends text and flushes
`DeepgramTTSService` SHALL send the response text as a single `{"type":"Speak","text":"..."}` message followed immediately by `{"type":"Flush"}`. It SHALL begin forwarding incoming binary PCM frames to `AudioManager.playAudio()` upon receipt, before the flush completes.

#### Scenario: Text sent and audio streaming begins
- **WHEN** `speak(text)` is called on a connected `DeepgramTTSService`
- **THEN** a Speak message and Flush message are sent over the WebSocket, and binary PCM frames received in response are passed to `AudioManager.playAudio()`

### Requirement: TTS disconnects cleanly when Direct Voice stops
When Direct Voice mode stops, `DeepgramTTSService` SHALL stop forwarding audio and close the WebSocket connection.

#### Scenario: Direct Voice session stopped
- **WHEN** `stopDirectVoiceSession()` is called
- **THEN** `DeepgramTTSService.disconnect()` is called, audio forwarding stops, and the WebSocket is closed with code 1000
