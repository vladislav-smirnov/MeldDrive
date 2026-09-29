---
applyTo: "app/src/main/java/**/data/**/*.kt"
---

# Data Layer & Storage Guidelines

- **Storage Abstraction**: All file system operations must implement `StorageSource` (`listFiles`, `readFile`, `getFileSize`, `searchFiles`, `deleteFile`).
- **Security & Credentials**:
  - Never store plain text passwords or tokens.
  - Always use `CredentialStorage` and `SecurityManager` (Google Tink AEAD + Android Keystore + DataStore) for remote storage credentials.
- **Concurrency & Coroutines**:
  - Avoid `runBlocking` across the data layer except in `FileStreamProvider` callback bridging where synchronous `ProxyFileDescriptorCallback` methods bridge to suspend repository methods.
- **Room Database DAOs**:
  - Avoid using `suspend` on `@Delete` methods that implicitly return `Unit` to prevent Room KSP compilation errors; return `Int` or use synchronous calls.
