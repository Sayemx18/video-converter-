# Sayem Video Converter

Native Android starter project for a video conversion app, built with Kotlin and Jetpack Compose.

## Current scope

- Android system document picker for selecting a video (`video/*`)
- Reads the selected video's name, dimensions, duration, and file size
- Dark Compose home screen with a portrait/landscape Full HD output preset
- Modular `VideoConverter` interface for a future real encoder implementation
- GitHub Actions workflow that builds and uploads a debug APK

Video encoding and Gallery export are not implemented in this starter. The app does not claim a conversion is running and does not display simulated progress. Add a maintained encoder behind `VideoConverter`, then connect its real progress/output to the UI and MediaStore.

## Project settings

- Minimum Android version: API 26 (Android 8.0)
- Compile and target SDK: API 36
- Java: 17
- Gradle wrapper: 8.13

To build locally when an Android SDK is available, run `./gradlew assembleDebug`. GitHub Actions runs the same task on pushes to `main` or `master`, pull requests, and manual dispatch.
