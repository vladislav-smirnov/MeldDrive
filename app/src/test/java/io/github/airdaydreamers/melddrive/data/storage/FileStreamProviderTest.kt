package io.github.airdaydreamers.melddrive.data.storage

import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import io.github.airdaydreamers.melddrive.data.model.StorageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FileStreamProviderTest {

    private lateinit var provider: FileStreamProvider

    @Before
    fun setUp() {
        provider = FileStreamProvider()
    }

    @Test
    fun testQueryReturnsValidCursorWithMetadata() {
        val uri = FileStreamProvider.buildUri(
            storageType = StorageType.SMB,
            serverId = 1L,
            path = "movies/films/Movie2025.mkv",
        )

        val cursor = provider.query(
            uri = uri,
            projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            selection = null,
            selectionArgs = null,
            sortOrder = null,
        )

        assertNotNull(cursor)
        assertEquals(1, cursor?.count)

        cursor?.use { c ->
            if (c.moveToFirst()) {
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)

                assertEquals("Movie2025.mkv", c.getString(nameIndex))
                assertEquals(-1L, c.getLong(sizeIndex))
            }
        }
    }

    @Test
    fun testQueryReturnsNullForInvalidUri() {
        val invalidUri = Uri.parse("content://io.github.airdaydreamers.melddrive.filestream/invalid")

        val cursor = provider.query(
            uri = invalidUri,
            projection = null,
            selection = null,
            selectionArgs = null,
            sortOrder = null,
        )

        assertNull(cursor)
    }

    @Test
    fun testGetTypeReturnsCorrectMimeType() {
        Shadows.shadowOf(MimeTypeMap.getSingleton())
            .addExtensionMimeTypeMapping("mkv", "video/x-matroska")

        val uri = FileStreamProvider.buildUri(
            storageType = StorageType.SMB,
            serverId = 1L,
            path = "movies/film.mkv",
        )

        val mimeType = provider.getType(uri)
        assertEquals("video/x-matroska", mimeType)
    }
}
