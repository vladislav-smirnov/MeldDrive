package io.github.airdaydreamers.melddrive.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MimeTypeMapCompat] validating extension-to-MIME-type mapping
 * and video file recognition across local and remote storage files.
 */
class MimeTypeMapCompatTest {

    @Test
    fun testIsVideoFile() {
        assertTrue(MimeTypeMapCompat.isVideoFile("sample.mp4"))
        assertTrue(MimeTypeMapCompat.isVideoFile("movie.mkv"))
        assertTrue(MimeTypeMapCompat.isVideoFile("clip.avi"))
        assertTrue(MimeTypeMapCompat.isVideoFile("video.mov"))
        assertTrue(MimeTypeMapCompat.isVideoFile("stream.webm"))
        assertTrue(MimeTypeMapCompat.isVideoFile("recording.3gp"))
        assertTrue(MimeTypeMapCompat.isVideoFile("flash.flv"))
        assertTrue(MimeTypeMapCompat.isVideoFile("transport.ts"))

        assertFalse(MimeTypeMapCompat.isVideoFile("document.txt"))
        assertFalse(MimeTypeMapCompat.isVideoFile("image.png"))
        assertFalse(MimeTypeMapCompat.isVideoFile("audio.mp3"))
        assertFalse(MimeTypeMapCompat.isVideoFile("archive.zip"))
    }

    @Test
    fun testIsImageFile() {
        assertTrue(MimeTypeMapCompat.isImageFile("photo.jpg"))
        assertTrue(MimeTypeMapCompat.isImageFile("image.png"))
        assertTrue(MimeTypeMapCompat.isImageFile("pic.webp"))

        assertFalse(MimeTypeMapCompat.isImageFile("sample.mp4"))
        assertFalse(MimeTypeMapCompat.isImageFile("document.txt"))
    }

    @Test
    fun testGetMimeTypeForVideoFiles() {
        assertEquals("video/mp4", MimeTypeMapCompat.getMimeType("sample.mp4"))
        assertEquals("video/x-matroska", MimeTypeMapCompat.getMimeType("movie.mkv"))
        assertEquals("video/avi", MimeTypeMapCompat.getMimeType("clip.avi"))
        assertEquals("video/quicktime", MimeTypeMapCompat.getMimeType("video.mov"))
        assertEquals("video/webm", MimeTypeMapCompat.getMimeType("stream.webm"))
    }
}
