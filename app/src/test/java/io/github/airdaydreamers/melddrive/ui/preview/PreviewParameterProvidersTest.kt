package io.github.airdaydreamers.melddrive.ui.preview

import io.github.airdaydreamers.melddrive.ui.mvi.ViewMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests verifying [FileManagerPreviewParameterProvider], [SettingsPreviewParameterProvider],
 * and [AddStoragePreviewParameterProvider] provide complete, non-empty, and accurate preview state sequences.
 */
class PreviewParameterProvidersTest {

    /**
     * Use Case: Verify FileManagerPreviewParameterProvider Sequence
     * Given the FileManagerPreviewParameterProvider instance
     * When values sequence is retrieved
     * Then it should contain 8 states covering list, grid, card, empty, loading, search, selection, and error.
     */
    @Test
    fun testFileManagerPreviewParameterProviderSequence() {
        val provider = FileManagerPreviewParameterProvider()
        val states = provider.values.toList()

        assertEquals(8, states.size)

        // 1. Populated List View
        assertEquals(ViewMode.LIST, states[0].viewMode)
        assertFalse(states[0].files.isEmpty())

        // 2. Populated Grid View
        assertEquals(ViewMode.GRID, states[1].viewMode)

        // 3. Populated Card View
        assertEquals(ViewMode.CARD, states[2].viewMode)

        // 4. Empty Folder
        assertTrue(states[3].files.isEmpty())

        // 5. Loading
        assertTrue(states[4].isLoading)

        // 6. Search Active
        assertTrue(states[5].isSearchActive)
        assertEquals("photo", states[5].searchQuery)

        // 7. Selected Files
        assertEquals(2, states[6].selectedFiles.size)

        // 8. Error State
        assertNotNull(states[7].errorMessage)
    }

    /**
     * Use Case: Verify SettingsPreviewParameterProvider Sequence
     * Given the SettingsPreviewParameterProvider instance
     * When values sequence is retrieved
     * Then it should contain 3 states covering default, custom buffer, and alternate language states.
     */
    @Test
    fun testSettingsPreviewParameterProviderSequence() {
        val provider = SettingsPreviewParameterProvider()
        val states = provider.values.toList()

        assertEquals(3, states.size)

        // 1. Default State
        assertFalse(states[0].bufferingEnabled)
        assertEquals(16, states[0].bufferSizeMb)
        assertEquals("en", states[0].currentLanguageCode)

        // 2. Custom Buffering
        assertTrue(states[1].bufferingEnabled)
        assertEquals(32, states[1].bufferSizeMb)

        // 3. Alternate Language
        assertEquals("de", states[2].currentLanguageCode)
        assertTrue(states[2].showHiddenFiles)
    }

    /**
     * Use Case: Verify AddStoragePreviewParameterProvider Sequence
     * Given the AddStoragePreviewParameterProvider instance
     * When values sequence is retrieved
     * Then it should contain 5 states covering SMB, WebDAV, loading, discovery, and error states.
     */
    @Test
    fun testAddStoragePreviewParameterProviderSequence() {
        val provider = AddStoragePreviewParameterProvider()
        val states = provider.values.toList()

        assertEquals(5, states.size)

        // 1. Default SMB State
        assertEquals("SMB", states[0].serverType)
        assertEquals("My Home NAS", states[0].displayName)

        // 2. WebDAV State
        assertEquals("WEBDAV", states[1].serverType)
        assertTrue(states[1].trustSelfSigned)

        // 3. Loading State
        assertTrue(states[2].isLoading)

        // 4. Discovered Servers
        assertFalse(states[3].discoveredServers.isEmpty())

        // 5. Error State
        assertNotNull(states[4].error)
    }

    /**
     * Use Case: Verify PreviewMockData Properties
     * Given the PreviewMockData object
     * Then mock sample files, sidebar items, and discovered servers should be populated correctly.
     */
    @Test
    fun testPreviewMockData() {
        assertFalse(PreviewMockData.sampleFiles.isEmpty())
        assertFalse(PreviewMockData.sampleSidebarItems.isEmpty())
        assertFalse(PreviewMockData.sampleDiscoveredServers.isEmpty())
    }
}
