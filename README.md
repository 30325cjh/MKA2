# MKA2

Android floating AI assistant prototype with a Gemini-powered chat backend.

## Android app
- Requests Android display-over-other-apps permission.
- Starts a foreground service after permission is granted.
- Displays a movable floating assistant bubble.
- A single tap shows a greeting; a double tap opens chat.
- Launches installed apps with messages beginning with `앱 실행 `.
- Opens a Google web search for messages beginning with `검색 `.
- Sends ordinary chat messages to the MKA2 Gemini backend and keeps a short conversation history in memory.

## Gemini backend (required for AI chat)
The Android app does **not** contain a Gemini API key. The backend in `server/` calls the Gemini API using a server-side environment variable.

1. Deploy the `server/` directory to a Node.js 20+ hosting service with HTTPS.
2. Set `GEMINI_API_KEY` in the hosting provider's secret/environment settings. Never commit a real key.
3. Optionally set `GEMINI_MODEL` (default: `gemini-3.8-flash`).
4. Check the deployed `/health` endpoint.
5. Update `BASE_URL` in `app/src/main/java/com/mka2/assistant/AssistantApi.java` to the backend's HTTPS origin, then rebuild the APK.

See [server/README.md](server/README.md) for details.

## Build Android APK
Open this repository in Android Studio and build the `app` configuration.

Requirements:
- Android Studio with Android Gradle Plugin 8.9.2 support
- JDK 17
- Android SDK Platform 35

GitHub Actions also runs `assembleDebug` on pushes and pull requests.

## Current limitations
- AI chat requires a deployed backend and a valid Gemini API key.
- Conversation history is kept only while the chat activity is alive.
- Search currently opens the browser; grounded search-result summaries are a later feature.
- Character artwork, expressions, animation, and voice interaction are not implemented yet.

Package: `com.mka2.assistant`
