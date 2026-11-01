# Sayem Video Converter

Native Android starter project for a video conversion app, built with Kotlin and Jetpack Compose.

## Current scope

- Android system document picker for selecting a video (`video/*`)
- Reads the selected video's name, dimensions, duration, and file size
- Dark Compose home screen with a portrait/landscape Full HD output preset
- Media3 Transformer conversion to H.264/AVC and AAC
- Aspect-ratio-preserving fit into 1920×1080 landscape or 1080×1920 portrait output
- Real Transformer progress, cancellation, and Gallery output through MediaStore
- GitHub Actions workflow that builds and uploads a debug APK

The app encodes to an app-private temporary file, then streams it in bounded-size chunks into a pending MediaStore video and publishes it to Gallery. This avoids loading large files into memory, but requires temporary free storage for the encoded video. Android 8 and 9 request legacy write permission for Gallery access. Input selection uses the system document picker and does not request broad media-read permissions. Encoding depends on the device's MediaCodec decoder/encoder support; unsupported source codecs or unavailable H.264 encoders produce an error instead of a fake success.

## Project settings

- Minimum Android version: API 26 (Android 8.0)
- Compile and target SDK: API 36
- Java: 17
- Gradle wrapper: 8.13

To build locally when an Android SDK is available, run `./gradlew assembleDebug`. GitHub Actions runs the same task on pushes to `main` or `master`, pull requests, and manual dispatch.
