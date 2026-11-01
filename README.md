# AI Assistant (Part 1): Gemini Live voice + phone control

Native Android (Kotlin, Jetpack Compose). The user names the assistant on first launch; the name is stored in
DataStore and injected into every Gemini Live session's system instruction.

## Versions
AGP 8.7.3 · Gradle 8.9 · Kotlin 2.0.21 · JDK 17 · Compose BOM 2024.10.01 · compileSdk/targetSdk 35 · minSdk 26

## Build with only a phone (no computer)
1. Create a free GitHub account, a new repository, and upload this project's files (GitHub mobile web: Add file > Upload).
2. Open the **Actions** tab > "Build debug APK" > Run workflow (it also runs on every push to `main`).
3. When finished, open the run > Artifacts > `app-debug-apk`, download, unzip, and install the APK
   (allow "Install unknown apps" for your browser/files app).
With Android Studio you can instead run `gradle :app:assembleDebug` (Gradle 8.9; no wrapper jar is included).

## First run
1. Create the assistant name. 2. Settings > paste your Gemini API key (https://aistudio.google.com/app/apikey) > Test connection.
3. Start Conversation (grants microphone). 4. For phone control: Settings > Phone control setup > open Accessibility
   Settings and enable the service yourself.

## Architecture
- `voice/` GeminiLiveManager (raw Live API WebSocket, `BidiGenerateContent`, v1beta), AudioInput (16 kHz PCM16),
  AudioOutput (24 kHz PCM16, flushable), state machine, bounded reconnect (5 tries, backoff), session resumption, goAway handling.
- `ai/` system instructions (rebuilt per session from saved name), action allowlist, strict ActionParser.
- `automation/` PhoneActionRouter (policy -> confirmation -> execute -> honest result), AppLauncher, ScreenNodeInspector,
  GestureController, AccessibilityAutomationService, ActionPermissionManager (block/confirm rules), AutomationLogger.
- `data/` + `memory/` DataStore settings, encrypted API key, optional local transcript.

## Model
Default `gemini-3.1-flash-live-preview` (from Google's Live API docs). It is a preview ID; it is editable in Settings and
"Test connection" tells you if your key can use it. Gemini 3.1 supports synchronous function calling only, which this app uses.

## Security notes
- Dev setup: user-supplied key stored encrypted on device, never logged. A key shipped inside a distributed app is extractable.
  For production, mint short-lived **ephemeral tokens** on your own backend (not implemented here).
- Confirmation is enforced locally, not by the model: sensitive taps (send, pay, delete, call, ...) need Approve via dialog or notification.
  Permission dialogs, consent, CAPTCHAs and password fields are refused outright.

## Not implemented / honest limits
- Wake word: NOT implemented. Voice is started manually. (Integration point: start `GeminiLiveManager.startSession()` from a detector.)
- "Tap the first visible result" (ordinal targeting) is not supported; targets must be identified by visible text/description.
- Long-term memory: not implemented; only name, settings and an optional transcript.
- Ephemeral-token backend: not implemented.
- Background: mic runs in a foreground service with a visible notification; Android may still stop it, and launching apps
  from the background can be blocked by the OS (the app reports this instead of claiming success).
- Echo: relies on device AEC; use headphones or turn off barge-in if the assistant hears itself.
- Wire-protocol field names were taken from Google's docs; verify against https://ai.google.dev/api/live if a session is rejected.

## Test checklist
Name persists after force-stop/reboot; rename mid-call then ask "What is your name?"; invalid key/model/no network messages;
"Open YouTube", "Scroll down", "Go back", "Tap Search"; say "Send" button target -> confirmation appears; End Call releases mic
(notification disappears); Stop Speaking mid-answer; Pause phone control.
