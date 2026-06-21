## ADDED Requirements

### Requirement: Direct Voice mode is mutually exclusive with AI mode
The app SHALL ensure that Direct Voice mode and AI mode (Gemini) cannot be active simultaneously. Activating one MUST stop the other if it is currently running.

#### Scenario: Starting Direct Voice while AI mode is off
- **WHEN** the user taps the Direct Voice button while AI mode is inactive
- **THEN** Direct Voice mode starts, the mic and Deepgram connection open, and the button reflects the active state

#### Scenario: Attempting to start Direct Voice while AI mode is active
- **WHEN** the user taps the Direct Voice button while AI mode is active
- **THEN** AI mode is stopped and Direct Voice mode starts

#### Scenario: Starting AI mode while Direct Voice is active
- **WHEN** the user taps the AI mode button while Direct Voice mode is active
- **THEN** Direct Voice mode is stopped and AI mode starts

### Requirement: Speech is transcribed via Deepgram and sent directly to OpenClaw
While Direct Voice mode is active, the app SHALL stream captured audio to Deepgram STT. On each `is_final` transcript result, the transcribed text SHALL be sent to the OpenClaw agent via the existing `delegateTask` API without passing through Gemini.

#### Scenario: Successful transcription and agent call
- **WHEN** Deepgram emits a final transcript while Direct Voice mode is active
- **THEN** the text is sent to `OpenClawBridge.delegateTask()` and the ToolCallStatus changes to Executing

#### Scenario: Empty or blank transcript
- **WHEN** Deepgram emits a final transcript with blank text while Direct Voice is active
- **THEN** no call is made to OpenClaw and the UI remains unchanged

### Requirement: OpenClaw agent response is shown in the overlay
The app SHALL display the OpenClaw agent's response text in the stream overlay when a Direct Voice agent call completes.

#### Scenario: Agent returns a response
- **WHEN** `delegateTask` completes with a successful result while Direct Voice mode is active
- **THEN** the response text is shown in the overlay and ToolCallStatus returns to Idle

#### Scenario: Agent call fails
- **WHEN** `delegateTask` returns a failure
- **THEN** an error indicator is shown via ToolCallStatus and no response text is displayed

### Requirement: Direct Voice mode stops cleanly when toggled off
When the user taps the Direct Voice button to stop, or when the mode is displaced by AI mode starting, the app SHALL stop capturing audio, disconnect Deepgram, and clear the active response from the overlay.

#### Scenario: User taps button to stop
- **WHEN** the user taps the Direct Voice button while Direct Voice mode is active
- **THEN** audio capture stops, Deepgram disconnects, and the overlay clears the last response

#### Scenario: OpenClaw not configured
- **WHEN** the user taps the Direct Voice button and OpenClaw is not configured
- **THEN** Direct Voice mode does not start and the button remains inactive
