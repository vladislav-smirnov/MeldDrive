# GitHub Copilot Custom Instructions for MeldDrive 🤖

MeldDrive is a modern, unified file management application for Android built with Kotlin, Jetpack Compose, and MVI architecture following Clean Architecture principles.

---

## 1. Kotlin Style & Coding Guidelines
- **Companion Object Placement**: `companion object` MUST ALWAYS be placed at the very bottom of the class declaration, after all member functions, properties, and nested classes.
- **Naming Conventions**:
  - `Boolean` properties MUST start with `is`, `has`, or `are` (e.g., `isHidden`, `hasPermission`, `areNotificationsEnabled`).
  - Use lowerCamelCase for properties and functions, PascalCase for classes, interfaces, and composables.
- **Modifier Order**: Always adhere strictly to standard Kotlin modifier order: `override open suspend fun ...`.
- **Formatting & Static Analysis**: Code MUST pass Detekt analysis (`config/detekt/detekt.yml`) with zero issues (`warningsAsErrors: true`). Always use Spotless formatting rules (`./gradlew spotlessApply`).

---

## 2. Compose UI & MVI Architecture Rules
- **MVI (Model-View-Intent) Architecture**:
  - Each screen defines its contract in a dedicated MVI file (e.g., `FileManagerMvi.kt`):
    - **State**: Immutable data class holding complete UI state.
    - **Intent (Events)**: Sealed interface/class representing user events and UI intents.
    - **Effect**: Sealed interface/class representing single-event side-effects (e.g., Toasts, navigation).
  - ViewModels manage state with `StateFlow<State>` and handle user actions via `fun onIntent(intent: Intent)`.
- **Stateful vs. Stateless Separation**:
  - Always separate stateful screen containers from stateless `*Content` composable views.
  - Stateful screen composables collect ViewModel state using `collectAsStateWithLifecycle()` and handle navigation.
  - Stateless `*Content` composables MUST be pure: they accept immutable `State` objects and emit user actions via an `onIntent: (Intent) -> Unit` lambda callback.
- **Modifiers & Test Tags**:
  - All interactive UI nodes (buttons, text fields, list items, dialog controls) MUST have `Modifier.testTag("tag_name")` applied to enable robust Compose UI test identification.
  - Always pass an optional `modifier: Modifier = Modifier` parameter as the first optional parameter in Composable functions.
- **Previews**: Use dedicated stateful and stateless preview wrappers located in `ui/preview/` with `@DevicePreviews` and `@ThemePreviews`.

---

## 3. Testing Rules & KDoc Format
- **Behavior-Driven Testing**: Focus strictly on testing system and user behavior rather than chasing line code coverage.
- **KDoc Description Header & Structure**: Every test function MUST include a clear KDoc description header above `@Test` specifying the Use Case, Given, When, and Then conditions, as well as `// Given`, `// When`, and `// Then` in-body comments:
  ```kotlin
  /**
   * Use Case: Create fetcher for local video file
   * Given a VideoThumbnailModel representing a local mp4 file
   * When ModelFactory.create is called
   * Then it should return a non-null VideoFrameFetcher
   */
  @Test
  fun testModelFactoryCreatesFetcherForLocalVideo() = runTest {
      // Given
      val modelFactory = VideoFrameFetcher.ModelFactory(repository)
      val localVideoItem = FileItem(
          path = "/storage/emulated/0/video.mp4",
          name = "video.mp4",
          isDirectory = false,
          storageType = StorageType.LOCAL,
      )
      val model = VideoThumbnailModel(file = localVideoItem)

      // When
      val fetcher = modelFactory.create(model, options, imageLoader)

      // Then
      assertNotNull(fetcher)
  }
  ```
- **Mocking & Doubles**: Use `mockk` for JVM unit tests (`coEvery`, `coVerify`, `slot`, `coAnswers`).

---

## 4. Architecture & Domain Rules
- **Architecture**: Clean Architecture layers: `data/` (repositories, storage handlers, security, Room DB, discovery) and `ui/` (MVI viewmodels, screens, components).
- **Concurrency & Coroutines**:
  - Always inject dispatchers or scope coroutines within ViewModels using `viewModelScope`.
  - Avoid `runBlocking` everywhere across the app EXCEPT in `FileStreamProvider` callback bridging where synchronous system `ProxyFileDescriptorCallback` methods bridge to suspend repository methods.
- **Security & Credentials**:
  - Never hardcode credentials, unencrypted tokens, or keys.
  - Always use `CredentialStorage` and `SecurityManager` (Google Tink AEAD + Android Keystore + DataStore) for remote storage credentials.
