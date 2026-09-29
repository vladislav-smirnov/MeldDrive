---
applyTo: "app/src/main/java/**/ui/**/*.kt"
---

# Jetpack Compose & MVI Architectural Guidelines

- **MVI (Model-View-Intent) & Unidirectional Data Flow (UDF)**:
  - Each screen defines its contract in a dedicated MVI file (e.g. `FileManagerMvi.kt`, `AddStorageMvi.kt`, `SettingsMvi.kt`):
    - **State**: Immutable data class holding full UI state (e.g., `FileManagerState`).
    - **Intent (Events)**: Sealed interface/class representing all user actions and events (e.g., `FileManagerIntent`).
    - **Effect**: Sealed interface/class representing single-event side effects like toasts or navigation (e.g., `FileManagerEffect`).
  - ViewModels manage UI state via `MutableStateFlow<State>` exposed as immutable `StateFlow<State>`, emit effects via a `Channel<Effect>` exposed via `receiveAsFlow()`, and handle user actions via `fun onIntent(intent: Intent)`.
- **Stateful Container vs. Stateless Content Separation**:
  - Stateful screen composables collect ViewModel state using `collectAsStateWithLifecycle()`, observe effects, and handle navigation.
  - Stateless `*Content` composables MUST be pure: they accept immutable `State` objects and emit user actions via an `onIntent: (Intent) -> Unit` lambda callback.
- **Modifiers & Test Tags**:
  - All interactive UI nodes (buttons, text fields, list items, dialog controls) MUST have `Modifier.testTag("tag_name")` applied for Compose UI test identification.
  - Always pass an optional `modifier: Modifier = Modifier` parameter as the first optional parameter in Composable functions.
- **Previews**: Use dedicated stateful and stateless preview wrappers located in `ui/preview/` with `@DevicePreviews` and `@ThemePreviews`.
