# Contributing to MeldDrive

Thank you for contributing to MeldDrive! To maintain high code quality and clear repository history, we follow standard contribution guidelines and commit formatting rules.

---

## 1. Local Repository Setup & Commit Template

To ensure your commit messages follow our Conventional Commits specification, configure Git to use our local commit template (`.gitmessage`):

```bash
git config commit.template .gitmessage
```

---

## 2. Commit Message Structure

Commit messages must follow the **Conventional Commits** format:

```text
<type>(<scope>): <short summary in present tense>

[optional body describing details and motivation]

[optional footer: Closes #issue]
```

### Commit Types

| Type | Description |
| :--- | :--- |
| `feat` | A new feature or user-facing enhancement |
| `fix` | A bug fix |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `perf` | Performance improvements |
| `test` | Adding or updating unit/instrumented tests |
| `docs` | Documentation updates only |
| `style` | Formatting, missing semi-colons, whitespace (no code logic changes) |
| `build` | Build system, Gradle configurations, dependency updates |
| `ci` | CI/CD workflows, Mergeable configuration, GitHub Actions |
| `chore` | Maintenance tasks, repository housekeeping |
| `revert` | Reverts a previous commit |

### How to Choose a Scope

Scopes indicate the subsystem or package impacted by the commit. Common scopes include:

- `ui`: Jetpack Compose screens, components, previews, view models, theme.
- `data`: Repositories, DAOs, Room database, DataStore.
- `storage`: `StorageSource` implementations, `LocalFileSystemHandler`, `SmbFileSystemHandler`, `WebDavFileSystemHandler`, `FileStreamProvider`.
- `navigation`: Navigation routes, backstack handling, drawer/rail layouts.
- `security`: Tink encryption, KeyStore, credential storage.
- `discovery`: Network discovery (`NsdDiscoveryManager`, `NetBiosDiscoveryManager`).
- `deps`: Gradle dependencies, SDK target updates.
- `ci`: GitHub Actions workflows, Mergeable rules.

#### Examples of Good Commit Titles

```text
feat(ui): add grid view mode toggle in file browser

fix(storage): resolve byte range handling in FileStreamProvider

docs(readme): add contribution instructions and build guide
```

---

## 3. Creating a Pull Request

When submitting a Pull Request (PR):

1. **PR Title**: Must follow the Conventional Commits specification (e.g. `feat(ui): add grid view mode toggle`). Mergeable checks the PR title format.
2. **PR Description**: Fill out all mandatory sections in `.github/pull_request_template.md`:
   - **Summary & Context**
   - **Type of Change**
   - **Related Issues**
   - **Manual Testing & Verification**
   - **Author Checklist**

---

## 4. Automated Verification (Mergeable & CI)

When a PR is opened or updated, GitHub Actions run two sets of checks:
1. **Mergeable Validation (`.github/workflows/mergeable.yml`)**: Checks that the PR title adheres to Conventional Commits format and that required template sections exist in the description.
2. **PR Check Workflow (`.github/workflows/pr_check.yml`)**: Runs `./gradlew detekt`, `./gradlew spotlessCheck`, unit tests (`./gradlew :app:testDebug`), and builds debug/release APKs.

---

## 5. Local Pre-commit Commands

Before pushing your changes, run these local checks:

```bash
# Run static analysis
./gradlew detekt

# Apply automatic code formatting
./gradlew spotlessApply

# Run JVM unit tests
./gradlew :app:testDebug

# Assemble debug build
./gradlew :app:assembleDebug
```
