---
applyTo: "app/src/test/**/*.kt,app/src/androidTest/**/*.kt"
---

# Testing Philosophy & Guidelines for MeldDrive

- **Behavior-Driven Testing**: Focus strictly on testing realistic system and user behavior rather than chasing line code coverage.
- **KDoc Description Header & Structure**: Every test function MUST include a clear KDoc description header above `@Test` specifying the Use Case, Given, When, and Then conditions, as well as explicit in-body comments:
  ```kotlin
  /**
   * Use Case: Create fetcher for local video file
   * Given a VideoThumbnailModel representing a local mp4 file
   * When ModelFactory.create is called
   * Then it should return a non-null VideoFrameFetcher
   */
  @Test
  fun testModelFactoryCreatesFetcherForLocalVideo() {
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
- **JVM Unit Tests**: Use JUnit 5, MockK, Turbine for Flow assertions, and Robolectric when Android context or Room DAOs are required.
- **Instrumented Tests**: Use `HiltTestRunner` and `ComposeContentTestRule` with `hasTestTag(...)` node matching.
