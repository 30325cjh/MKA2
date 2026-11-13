# MKA2

Android floating AI assistant prototype.

## Current prototype
- Requests Android display-over-other-apps permission.
- Starts a foreground service after permission is granted.
- Displays a movable floating assistant bubble.
- A single tap shows a short greeting; a double tap opens the chat screen.
- Basic web search intent from chat using messages beginning with `검색 `.

## Build
Open this repository in Android Studio and build the `app` configuration.

Requirements:
- Android Studio with Android Gradle Plugin 8.9.2 support
- JDK 17
- Android SDK Platform 35

## Planned
- Connect an AI provider through a secure backend (do not put API secrets in the APK).
- Resolve installed apps by name and launch them.
- Add character artwork, expressions, animation, and voice interaction.

Package: `com.mka2.assistant`
