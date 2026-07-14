package io.github.airdaydreamers.melddrive.data.storage

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [LocalFileSystemHandler] using JUnit 5 and a temporary disk directory.
 * Tests actual local filesystem CRUD, listing, sorting, ranged reading, and recursive searching.
 */
class LocalFileSystemHandlerTest {

    private lateinit var handler: LocalFileSystemHandler

    @BeforeEach
    fun setUp() {
        handler = LocalFileSystemHandler()
    }

    /**
     * Use Case: Directory Listing with Proper Sorting
     * Given a directory containing both directories and files with varied names
     * When listFiles is called
     * Then they should be returned with directories first (sorted alphabetically) followed by files sorted alphabetically
     */
    @Test
    fun testListFilesAndSorting(@TempDir tempDir: Path) = runBlocking {
        // Given
        Files.createDirectory(tempDir.resolve("subDirB"))
        Files.createDirectory(tempDir.resolve("subDirA"))
        Files.createFile(tempDir.resolve("fileD.txt"))
        Files.createFile(tempDir.resolve("fileC.txt"))

        // When
        val result = handler.listFiles(tempDir.toString())

        // Then
        assertEquals(4, result.size)

        // Directories first, alphabetically
        assertEquals("subDirA", result[0].name)
        assertTrue(result[0].isDirectory)

        assertEquals("subDirB", result[1].name)
        assertTrue(result[1].isDirectory)

        // Then files, alphabetically
        assertEquals("fileC.txt", result[2].name)
        assertFalse(result[2].isDirectory)

        assertEquals("fileD.txt", result[3].name)
        assertFalse(result[3].isDirectory)
    }

    /**
     * Use Case: Create Folder, Rename, and Delete
     * Given a temporary parent directory
     * When a folder is created, then renamed, and then deleted
     * Then the operations should complete successfully and update the filesystem accordingly
     */
    @Test
    fun testFolderCRUD(@TempDir tempDir: Path) = runBlocking {
        val parentPath = tempDir.toString()

        // 1. Create Folder
        val created = handler.createFolder(parentPath, "newFolder")
        assertTrue(created)
        assertTrue(Files.exists(tempDir.resolve("newFolder")))

        // 2. Rename Folder
        val renamed = handler.renameFile(tempDir.resolve("newFolder").toString(), "renamedFolder")
        assertTrue(renamed)
        assertFalse(Files.exists(tempDir.resolve("newFolder")))
        assertTrue(Files.exists(tempDir.resolve("renamedFolder")))

        // 3. Delete Folder
        val deleted = handler.deleteFile(tempDir.resolve("renamedFolder").toString())
        assertTrue(deleted)
        assertFalse(Files.exists(tempDir.resolve("renamedFolder")))
    }

    /**
     * Use Case: Ranged/Partial File Reading
     * Given a file with known text content "abcdefgh"
     * When readFile is called with offset 2 and length 4
     * Then it should return the byte array for "cdef"
     */
    @Test
    fun testRangedReadFile(@TempDir tempDir: Path) = runBlocking {
        // Given
        val file = tempDir.resolve("test.txt")
        Files.write(file, "abcdefgh".toByteArray())

        // When
        val bytes = handler.readFile(file.toString(), 2L, 4)

        // Then
        assertEquals("cdef", String(bytes))
    }

    /**
     * Use Case: Recursive Search by Query
     * Given a nested folder structure containing matching and non-matching files
     * When searchFiles is called with a case-insensitive query "target"
     * Then it should return all matching files and folders recursively
     */
    @Test
    fun testRecursiveSearch(@TempDir tempDir: Path) = runBlocking {
        // Given
        val subDir = Files.createDirectory(tempDir.resolve("subDir"))
        Files.createFile(tempDir.resolve("target1.txt"))
        Files.createFile(subDir.resolve("another_TARGET_file.log"))
        Files.createFile(tempDir.resolve("ignore.txt"))

        // When
        val result = handler.searchFiles(tempDir.toString(), "target")

        // Then
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "target1.txt" })
        assertTrue(result.any { it.name == "another_TARGET_file.log" })
        assertFalse(result.any { it.name == "ignore.txt" })
    }
}
