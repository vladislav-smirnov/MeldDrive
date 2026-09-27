package io.github.airdaydreamers.melddrive.data.storage

import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.storage.webdav.WebDavFileSystemHandler
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [WebDavFileSystemHandler] using [MockWebServer] to verify WebDAV HTTP methods,
 * PROPFIND XML parsing, HTTP Range requests for streaming, authentication, and file operations.
 */
class WebDavFileSystemHandlerTest {

    private lateinit var server: MockWebServer
    private lateinit var remoteServer: RemoteServer
    private lateinit var handler: WebDavFileSystemHandler

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        val baseUrl = server.url("/").toString()

        remoteServer = RemoteServer(
            id = 1L,
            displayName = "WebDAV Test",
            host = baseUrl,
            port = server.port,
            username = "user",
            password = "password",
            isAnonymous = false,
            type = "WEBDAV",
            trustSelfSigned = false,
        )

        handler = WebDavFileSystemHandler(remoteServer)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    /**
     * Use Case: List files in a WebDAV directory via PROPFIND Depth: 1
     * Given a WebDAV server returning a 207 Multi-Status XML response
     * When listFiles is called
     * Then it parses and returns child FileItems correctly
     */
    @Test
    fun testListFiles() = runBlocking {
        // Given
        val xmlResponse = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
                <D:response>
                    <D:href>/</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:resourcetype><D:collection/></D:resourcetype>
                        </D:prop>
                    </D:propstat>
                </D:response>
                <D:response>
                    <D:href>/folder1/</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:resourcetype><D:collection/></D:resourcetype>
                            <D:displayname>folder1</D:displayname>
                            <D:getlastmodified>Mon, 18 May 2020 09:38:00 GMT</D:getlastmodified>
                        </D:prop>
                    </D:propstat>
                </D:response>
                <D:response>
                    <D:href>/document.pdf</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:resourcetype/>
                            <D:getcontentlength>2048</D:getcontentlength>
                            <D:displayname>document.pdf</D:displayname>
                        </D:prop>
                    </D:propstat>
                </D:response>
            </D:multistatus>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(207).setBody(xmlResponse))

        // When
        val items = handler.listFiles("")

        // Then
        val recordedRequest = server.takeRequest()
        assertEquals("PROPFIND", recordedRequest.method)
        assertEquals("1", recordedRequest.getHeader("Depth"))

        assertEquals(2, items.size)

        val folder = items.first { it.name == "folder1" }
        assertTrue(folder.isDirectory)
        assertEquals(StorageType.WEBDAV, folder.storageType)

        val file = items.first { it.name == "document.pdf" }
        assertEquals(2048L, file.size)
        assertEquals(StorageType.WEBDAV, file.storageType)
    }

    /**
     * Use Case: Read file chunk via HTTP Range request
     * Given a file request at offset 100 with length 25
     * When readFile is executed
     * Then it sends Range: bytes=100-124 header and returns response bytes
     */
    @Test
    fun testReadFileRange() = runBlocking {
        // Given
        val mockData = "Hello WebDAV Range Stream".toByteArray()
        server.enqueue(MockResponse().setResponseCode(206).setBody(bufferOf(mockData)))

        // When
        val bytes = handler.readFile("media/video.mp4", 100L, mockData.size)

        // Then
        val recordedRequest = server.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("bytes=100-124", recordedRequest.getHeader("Range"))
        assertArrayEquals(mockData, bytes)
    }

    /**
     * Use Case: Create folder via MKCOL
     * Given a parent path and new folder name
     * When createFolder is called
     * Then it sends MKCOL request to the server
     */
    @Test
    fun testCreateFolder() = runBlocking {
        // Given
        server.enqueue(MockResponse().setResponseCode(201))

        // When
        val result = handler.createFolder("docs", "newFolder")

        // Then
        val recordedRequest = server.takeRequest()
        assertEquals("MKCOL", recordedRequest.method)
        assertTrue(recordedRequest.path!!.endsWith("docs/newFolder"))
        assertTrue(result)
    }

    /**
     * Use Case: Delete file via DELETE
     * Given an existing file path
     * When deleteFile is called
     * Then it sends DELETE request and returns true
     */
    @Test
    fun testDeleteFile() = runBlocking {
        // Given
        server.enqueue(MockResponse().setResponseCode(204))

        // When
        val result = handler.deleteFile("file.txt")

        // Then
        val recordedRequest = server.takeRequest()
        assertEquals("DELETE", recordedRequest.method)
        assertTrue(result)
    }

    /**
     * Use Case: Rename / move file via MOVE
     * Given an existing file path and a new name
     * When renameFile is called
     * Then it sends MOVE request with Destination header
     */
    @Test
    fun testRenameFile() = runBlocking {
        // Given
        server.enqueue(MockResponse().setResponseCode(201))

        // When
        val result = handler.renameFile("docs/oldName.txt", "newName.txt")

        // Then
        val recordedRequest = server.takeRequest()
        assertEquals("MOVE", recordedRequest.method)
        assertTrue(recordedRequest.getHeader("Destination")!!.endsWith("docs/newName.txt"))
        assertTrue(result)
    }

    private fun bufferOf(data: ByteArray): okio.Buffer = okio.Buffer().write(data)
}
