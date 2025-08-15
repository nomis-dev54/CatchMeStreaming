# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CatchMeStreaming is an Android HTTP video streaming application built with:
- **Kotlin** with **Jetpack Compose** and **Material 3** UI
- **CameraX** for camera handling
- **Ktor/Netty HTTP server** for video streaming
- **MediaRecorder API** for local MP4 recording
- **MVVM architecture** with repositories and ViewModels
- Target API: 31+ (compileSdk: 36, minSdk: 31, targetSdk: 36)

## Build Commands

### Essential Build Commands
- `./gradlew assembleDebug` - Build debug APK (always run after completing tasks)
- `./gradlew build` - Full build with tests
- `./gradlew clean` - Clean build artifacts
- `./gradlew test` - Run unit tests
- `./gradlew connectedAndroidTest` - Run instrumented tests

### Installation
- Tell user to run `./gradlew installDebug` (don't run it yourself to save tokens)

### Testing Commands
- `./gradlew testDebugUnitTest` - Run unit tests only
- `./gradlew connectedDebugAndroidTest` - Run instrumented tests only
- Single test: `./gradlew test --tests "com.example.catchmestreaming.ExampleUnitTest"`

## Architecture and Code Structure

### Package Structure
- `com.example.catchmestreaming` - Root package
- `com.example.catchmestreaming.ui.theme` - Material 3 theming
- Future structure (based on Design.md):
  - `repository/` - Data layer (CameraRepository, StreamRepository, RecordingRepository, SettingsRepository)
  - `viewmodel/` - ViewModels for MVVM pattern
  - `ui/screens/` - Compose screens (MainScreen, SettingsScreen, HelpOverlay)

### Key Dependencies (libs.versions.toml)
- Jetpack Compose BOM: 2024.09.00
- Kotlin: 2.0.21
- AGP: 8.11.1
- Material 3 via Compose
- CameraX (already integrated)
- Ktor/Netty (already integrated for HTTP streaming server)

## Development Guidelines

### Context7 Integration
- **ALWAYS** use Context7 to verify syntax for any package used in this app
- If there's no Context7 entry for a package, notify user and provide options
- Required for: CameraX, Jetpack Compose, Ktor, testing frameworks

### Error Handling and Debugging
- If issue takes longer than 3 attempts to fix, enable `android.util.Log` on 4th attempt
- Explain logs needed to monitor via `adb logcat`
- Don't work around issues - fix them properly
- After 4 failed attempts: provide options and risk analysis to user

### Test-Driven Development
- **Write tests BEFORE implementing features** (unless explicitly told not to)
- Tests must cover happy path AND edge cases
- Test structure:
  - Unit tests: `app/src/test/java/` (JUnit + MockK)
  - Instrumented tests: `app/src/androidTest/java/` (Espresso + AndroidJUnit4)
- Always run tests after implementation to verify functionality
- Always run `./gradlew assembleDebug` at end of each completed task

### Code Quality Standards
- Use descriptive variable names
- Code MUST pass type checking (Kotlin compiler)
- Code MUST pass linting
- Follow MVVM architecture pattern
- Use Jetpack Compose for UI with Material 3 design

### Security Best Practices
- Use Android Keystore for sensitive data (streaming credentials)
- Request runtime permissions with clear user prompts
- Use placeholder values for API keys in documentation (e.g., "Your_Key")
- No hardcoded secrets or credentials
- Sanitize user inputs to prevent injection attacks

## Project Management and Documentation

### Required Documentation Files
- `PromptLog.md` - Maintain copy of all prompts and instructions
- `ProjPlans.md` - Record all implementation plans and issue resolution plans
- `Design.md` - Already exists with comprehensive app design

### Development Workflow
1. Create tests first (TDD approach)
2. Implement feature following MVVM pattern
3. Use Context7 to verify library syntax
4. Run tests to verify implementation
5. Run `./gradlew assembleDebug` to ensure build success
6. Manual testing on USB-connected device

## Security and Privacy
- Security-first design principles
- Android Keystore for credential storage
- Runtime permission handling
- Input validation and sanitization
- Code obfuscation with ProGuard/R8 

- if a test fails every effort should be made to fix an issue before simplifying a test
- if a function fails every effort should be made to fix the issue instead of removing or simplifying, if it can not be resolved inform me of the options that can be taken
- there is alway a device conected via usb, if the test requires real world testing uses it
- if a test need to use a device to test a function then update the Debug verions on the device and start the app is required, in interaction is required infrom me so I can do it.