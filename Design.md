# CatchMeStreaming Android Camera App Design Document

## Overview
CatchMeStreaming is an Android application designed to stream live video and audio from a device's camera over a local network via RTSP, suitable for use cases like baby monitoring or content streaming trials. The app supports local recording of streams in MP4 format for later editing and emphasizes a user-friendly setup with guided help features. The design adheres to **Security First** and **Test-Driven Development (TDD)** principles, targeting Android API 32+ for phones and tablets with cameras, using a modern, Material 3-based UI.

## Objectives
- Provide seamless RTSP streaming (video and audio) over a local network.
- Enable local recording of streams in MP4 format with royalty-free codecs.
- Offer a user-friendly interface with guided setup, tooltips, and tutorials.
- Ensure robust security practices and comprehensive unit testing.
- Use Context7 MCP to verify syntax for all libraries, plug-ins, and packages.

## Tech Stack
- **Language**: Kotlin (modern, type-safe, Android-recommended).
- **UI Framework**: Jetpack Compose with Material 3 (responsive, modern design, compatible with all libraries).
- **Camera Access**: CameraX (simplified camera handling, supports API 32+).
- **RTSP Streaming**: GStreamer (native Android port, supports RTSP video/audio streaming, avoids NDK).
- **Recording**: MediaRecorder API (local MP4 recording with H.264 video and AAC audio codecs).
- **Testing**: JUnit 5 (unit testing), Espresso (UI testing), and MockK (mocking for TDD).
- **Dependency Management**: Gradle with Kotlin DSL.
- **Security**: Android Keystore for sensitive data, runtime permissions, and secure network communication.
- **Context7 MCP**: Used to verify syntax for GStreamer, CameraX, Jetpack Compose, and other libraries.

## Architecture
The app follows a **Model-View-ViewModel (MVVM)** architecture to ensure separation of concerns, testability, and maintainability.

### Components
1. **Model**:
   - **CameraRepository**: Manages CameraX for capturing video and audio.
   - **StreamRepository**: Configures and controls GStreamer for RTSP streaming.
   - **RecordingRepository**: Handles MediaRecorder for local MP4 storage.
   - **SettingsRepository**: Manages user preferences (stream URL, credentials).

2. **ViewModel**:
   - **MainViewModel**: Coordinates camera preview, streaming, and recording states.
   - **SettingsViewModel**: Handles settings configuration and validation.

3. **View** (Jetpack Compose):
   - **MainScreen**: Displays camera preview, Stream/Record buttons, and status indicators.
   - **SettingsScreen**: Configures stream URL, username, password, and recording quality.
   - **HelpOverlay**: Displays tooltips and tutorials for camera, streaming, and recording.

### Data Flow
- User configures defaults (RTSP URL, credentials) in SettingsScreen.
- MainScreen uses CameraX to show live camera preview.
- On clicking "Stream," StreamRepository initializes GStreamer, and a popup displays the RTSP URL, username, and password.
- On clicking "Record," RecordingRepository saves the stream as MP4 to local storage.
- SettingsRepository persists configurations using SharedPreferences (encrypted via Android Keystore).

## Security First Principles
- **Permissions**: Request camera, microphone, and storage permissions at runtime, with clear user prompts.
- **Data Protection**: Store sensitive data (e.g., RTSP credentials) in Android Keystore.
- **Network Security**: Use HTTPS for any external API calls (if added later) and validate RTSP connections.
- **Input Validation**: Sanitize user inputs in Settings to prevent injection attacks.
- **Code Security**: Use ProGuard/R8 for code obfuscation and minimize exposed APIs.

## Test-Driven Development (TDD)
- **Unit Tests** (JUnit 5, MockK):
  - Test CameraRepository for camera initialization and stream output.
  - Test StreamRepository for GStreamer pipeline setup and RTSP URL generation.
  - Test RecordingRepository for MP4 file creation and codec compatibility.
  - Test SettingsRepository for secure storage and retrieval.
- **UI Tests** (Espresso):
  - Verify MainScreen displays camera preview and buttons.
  - Test SettingsScreen input fields and validation.
  - Confirm HelpOverlay tooltips and tutorials appear contextually.
- **Integration Tests**:
  - Validate end-to-end streaming from CameraX to GStreamer RTSP output.
  - Ensure recording saves valid MP4 files with H.264/AAC codecs.

## User Interface
- **MainScreen**:
  - Camera preview (full-screen, adaptive to device aspect ratio).
  - Floating Action Buttons (FABs) for "Stream" and "Record."
  - Status bar showing streaming/recording state and network status.
  - Tooltip overlay for first-time users (e.g., "Tap Stream to start RTSP").
- **SettingsScreen**:
  - Fields for RTSP URL (default: `rtsp://[device-ip]:8554/stream`), username, and password.
  - Dropdown for recording quality (e.g., 720p, 1080p).
  - Tutorial dialog explaining each setting’s purpose.
- **HelpOverlay**:
  - Contextual tooltips (e.g., on Stream button: “Starts RTSP stream over local network”).
  - On-demand tutorial accessible via a “Help” button, covering camera selection, streaming setup, and recording.

## Setup and Configuration
- **Default Settings**: Pre-configured RTSP URL (`rtsp://[device-ip]:8554/stream`), default username/password (e.g., `admin`/`password`), and 720p recording quality.
- **Guided Setup**:
  - On first launch, a welcome tutorial guides users through SettingsScreen.
  - “Stream” button triggers a popup displaying RTSP URL, username, and password (pulled from Settings).
  - Auto-detects device IP for RTSP URL; users can override manually if needed.
- **Error Handling**: Display user-friendly messages for network issues, permission denials, or camera failures, with retry options.

## GStreamer Integration
- **Pipeline**: Use `cameraxsrc ! videoconvert ! x264enc ! rtspclientsink` for video and `audiocapturesrc ! aacenc ! rtspclientsink` for audio.
- **Configuration**: Dynamically set RTSP URL based on SettingsRepository.
- **Syntax Verification**: Use Context7 MCP to ensure correct GStreamer pipeline syntax and compatibility with Android port.

## Recording
- **Format**: MP4 with H.264 video and AAC audio (royalty-free, widely compatible).
- **Storage**: Save to device’s internal storage (`/Movies/CatchMeStreaming/`).
- **Quality Options**: Configurable in Settings (720p, 1080p).
- **Error Handling**: Notify users if storage is low or recording fails.

## Dependencies
- **CameraX**: `androidx.camera:camera-core`, `androidx.camera:camera-video` (latest versions via Context7 MCP).
- **Jetpack Compose**: `androidx.compose:compose-material3` (Material 3 support, verified for compatibility).
- **GStreamer**: `org.freedesktop.gstreamer:gstreamer-android` (prebuilt for Android, includes `rtspclientsink`).
- **MediaRecorder**: Android SDK (built-in, no external dependency).
- **Testing**: `junit:junit:5.x`, `androidx.test.espresso:espresso-core`, `io.mockk:mockk` (latest via Context7 MCP).
- **Security**: Android Keystore (built-in), `androidx.security:security-crypto` for encryption.

## Development Guidelines
- **Code Style**: Follow Kotlin coding conventions and Android best practices.
- **Version Control**: Use Git with descriptive commit messages.
- **CI/CD**: Integrate with GitHub Actions for automated testing and linting.
- **Context7 MCP**: Query for latest syntax and examples for all libraries during implementation.

## Future Considerations
- Add cloud export for recordings if requested.
- Support multiple camera selection for devices with front/back cameras.
- Implement advanced streaming options (e.g., adjustable bitrate).

## Milestones
1. **Setup**: Configure Gradle, dependencies, and GStreamer integration.
2. **Camera & Streaming**: Implement CameraX preview and GStreamer RTSP pipeline.
3. **Recording**: Add MediaRecorder for MP4 output.
4. **UI**: Build MainScreen and SettingsScreen with Jetpack Compose and Material 3.
5. **Help Features**: Add tooltips and tutorials.
6. **Testing**: Write unit, UI, and integration tests.
7. **Security**: Implement Keystore and permission handling.
8. **Polish**: Optimize performance and finalize UX.