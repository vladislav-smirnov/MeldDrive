# AGENTS.md — MeldDrive Developer & Agent Architecture Guide

This document defines the high-level architecture, coding conventions, testing guidelines, and guidelines for AI agents and developers working on the MeldDrive codebase.

---

## High-Level Architecture Overview

MeldDrive is a modern, unified file management application for Android built with Kotlin, Jetpack Compose, and MVI (Model-View-Intent) architecture following Clean Architecture principles.

- **Module Structure**: Single-module Android project (`app/`).
  - `data/`: Storage handlers (Local, SMB, WebDAV), database (`AppDatabase` / Room), network discovery (`NsdDiscoveryManager`, `NetBiosDiscoveryManager`), security (`SecurityManager`, `CredentialStorage`), repositories (`FileRepository`, `ServerRepository`).
  - `ui/`: Compose UI components, screens, ViewModels, MVI definitions (`FileManagerMvi`, `AddStorageMvi`, `SettingsMvi`), navigation, previews, and design theme.
  - `di/`: Dependency injection helpers and ViewModel factory.
  - `util/`: Helper utilities (MIME type compatibility, string resource extensions, file helpers).
- **UI Architecture & MVI (Model-View-Intent)**:
  - Jetpack Compose + Material 3. Navigation uses `NavHost` in `MainActivity.kt` with three main routes: `file_manager`, `add_storage`, `settings`.
  - Every screen defines its immutable state and contract in an MVI file (e.g., `FileManagerMvi.kt`):
    - **State**: Immutable data class representing the complete UI state at any moment (e.g., `FileManagerState`).
    - **Intent (Events)**: Sealed class or interface representing all possible user actions and events (e.g., `FileManagerIntent`).
    - **Effect**: Sealed class representing one-off side effects like showing toasts or navigating (e.g., `FileManagerEffect`).
  - **Unidirectional Data Flow (UDF)**:
    - ViewModels hold state in `MutableStateFlow<State>` exposed as public `StateFlow<State>`.
    - One-off effects use a `Channel<Effect>` exposed as `Flow<Effect>` via `receiveAsFlow()`.
    - User interactions in UI trigger `onIntent(intent: Intent)` on the ViewModel.
    - Pure stateless `*Content` composable views receive state data and emit events via `onIntent: (Intent) -> Unit`.
- **Dependency Injection**:
  - **Application Runtime**: Manual factory-based dependency injection (`ViewModelFactory.kt` constructed in `MainActivity.kt`).
  - **Android Instrumented Tests**: `HiltTestRunner` and Dagger Hilt are used for test dependency wiring.
- **Storage Abstraction & Streaming**:
  - Unified file operations via `StorageSource` interface (`listFiles`, `readFile`, `getFileSize`, `searchFiles`, `deleteFile`).
  - Streaming via `FileStreamProvider` (`ContentProvider`) using `StorageManager.openProxyFileDescriptor` and `ProxyFileDescriptorCallback`. Authority: `io.github.airdaydreamers.melddrive.filestream`.
  - `FileStreamProvider` uses `runBlocking` strictly to bridge synchronous system callbacks with asynchronous repository methods.
- **Security & Encryption**:
  - Sensitive server credentials (usernames/passwords) are encrypted using Google Tink AEAD with hardware-backed Android Keystore master keys and persisted in Jetpack Preferences DataStore (`credentials`).

---

## Coding & Style Guidelines

### Kotlin Coding Standards
1. **Companion Objects**: `companion object` MUST ALWAYS be placed at the very bottom of the class declaration, after all member functions, properties, and inner/nested classes.
2. **Naming Conventions**:
   - PascalCase for classes, interfaces, objects, and composable functions.
   - lowerCamelCase for variables, properties, functions, and parameters.
   - `Boolean` properties must be prefixed with `is`, `has`, or `are` (e.g., `isHidden`, `hasPermission`).
3. **Modifier Order**: Strictly follow standard Kotlin modifier ordering (`override open suspend fun ...`).
4. **Static Analysis & Quality Gates**:
   - Detekt static code analysis (`config/detekt/detekt.yml`) is enabled with `warningsAsErrors: true`. All code must pass `./gradlew detekt` with zero violations.
   - Code formatting is enforced by Spotless (`./gradlew spotlessApply`).

### Jetpack Compose & MVI Guidelines
1. **Stateful Container vs. Stateless Content**:
   - Separate stateful screen containers (handling ViewModel state collection and navigation) from stateless `*Content` composables.
   - Stateless `*Content` composables MUST be pure: they accept immutable `State` data objects and emit events via `onIntent: (Intent) -> Unit` lambda callbacks.
2. **Lifecycle Aware Collection**: Always collect `StateFlow` in composables using `collectAsStateWithLifecycle()`.
3. **UI Testing Identification**: Interactive Compose elements (buttons, text inputs, list items, dialogs) MUST include `Modifier.testTag("tag_name")` for reliable node identification in UI tests.
---

## Testing Philosophy & Standards

### Behavior-Driven Testing
- **Goal**: Tests MUST verify actual system and user behavior rather than chasing arbitrary code line coverage.
- **Test KDoc Header & Given-When-Then Structure**: Every test function MUST include a clear KDoc description header above `@Test` outlining the Use Case, Given, When, and Then criteria, as well as explicit in-body code comments:
  ```kotlin
  /**
   * Use Case: User navigates to local Downloads directory
   * Given an initial root directory state
   * When user triggers NavigateTo intent for Downloads directory
   * Then current path in state should update to Downloads
   */
  @Test
  fun userCanNavigateToDirectory() = runTest {
      // Given
      val initialState = FileManagerState(currentPath = "/", currentStorageType = StorageType.LOCAL)
      val targetPath = "/storage/emulated/0/Downloads"

      // When
      viewModel.onIntent(FileManagerIntent.NavigateTo(targetPath, StorageType.LOCAL))

      // Then
      val state = viewModel.state.value
      assertEquals(targetPath, state.currentPath)
  }
  ```

### Test Locations & Tools
- **Unit Tests (JVM)**: `app/src/test/java/io/github/airdaydreamers/melddrive/...`
  - Frameworks: JUnit 5, MockK, Turbine (for Flow verification), Coroutines Test Framework.
  - Robolectric: Used for tests requiring Android context or DAOs. Pass `-Drobolectric.offline=true` when running in offline environments.
- **Instrumented Tests (Android)**: `app/src/androidTest/java/io/github/airdaydreamers/melddrive/...`
  - Uses `HiltTestRunner` and `ComposeContentTestRule`. Nodes identified via `hasTestTag(...)`.

---

## Quick Reference Commands

- **Build Debug APK**: `./gradlew :app:assembleDebug`
- **Run Unit Tests**: `./gradlew :app:testDebugUnitTest -Drobolectric.offline=true`
- **Run Static Checks**: `./gradlew detekt`
- **Apply Formatting**: `./gradlew spotlessApply`
- **Run Instrumented Tests**: `./gradlew :app:connectedAndroidTest`
