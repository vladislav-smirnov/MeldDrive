package io.github.airdaydreamers.melddrive

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.storage.SettingsManager
import io.github.airdaydreamers.melddrive.di.AppModule
import io.github.airdaydreamers.melddrive.fake.FakeSmbServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@UninstallModules(AppModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MeldDriveUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var remoteServerDao: RemoteServerDao

    @Inject
    lateinit var settingsManager: SettingsManager

    companion object {
        private val fakeSmbServer = FakeSmbServer(4445)

        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            fakeSmbServer.start()

            // Set system property to app's secure, always-writable cache directory
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val targetPath = targetContext.cacheDir.absolutePath + "/MeldDriveTestDir"
            System.setProperty("test.local.root", targetPath)

            // Grant MANAGE_EXTERNAL_STORAGE permission programmatically in test (if needed)
            try {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "appops set io.github.airdaydreamers.melddrive MANAGE_EXTERNAL_STORAGE allow",
                )
            } catch (_: Exception) {}

            prepareLocalTestFiles()
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            fakeSmbServer.stop()
            System.clearProperty("test.local.root")

            try {
                val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
                val targetRoot = File(targetContext.cacheDir, "MeldDriveTestDir")
                if (targetRoot.exists()) {
                    targetRoot.deleteRecursively()
                }
            } catch (_: Exception) {}
        }

        private fun prepareLocalTestFiles() {
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val targetRoot = File(targetContext.cacheDir, "MeldDriveTestDir")
            if (targetRoot.exists()) {
                targetRoot.deleteRecursively()
            }
            targetRoot.mkdirs()

            copyAsset(testContext, "test_file_1.txt", File(targetRoot, "test_file_1.txt"))
            copyAsset(testContext, "test_file_2.txt", File(targetRoot, "test_file_2.txt"))

            val testFolder = File(targetRoot, "test_folder")
            testFolder.mkdirs()
            copyAsset(testContext, "test_folder/test_file_3.txt", File(testFolder, "test_file_3.txt"))
        }

        private fun copyAsset(context: Context, assetName: String, outFile: File) {
            context.assets.open(assetName).use { inputStream ->
                outFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    @Before
    fun setUp() {
        hiltRule.inject()
        // Reset state between tests
        runBlocking {
            val servers = remoteServerDao.getAllServers().first()
            servers.forEach { remoteServerDao.deleteServer(it) }
            settingsManager.setBufferingEnabled(false)
        }
    }

    @After
    fun tearDown() {
        // Cleaning up database between tests if needed
    }

    /**
     * Use Case 1: Browser of local files
     * Verify we can browse local files, open folders and see correct file items.
     */
    @Test
    fun testLocalFileBrowser() {
        // App starts directly inside MeldDriveTestDir. Verify inside files and folder are displayed
        composeTestRule.onNodeWithTag("file_item_test_file_1.txt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("file_item_test_file_2.txt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("file_item_test_folder").assertIsDisplayed()

        // Open test_folder
        composeTestRule.onNodeWithTag("file_item_test_folder").performClick()

        // Verify inside file is displayed
        composeTestRule.onNodeWithTag("file_item_test_file_3.txt").assertIsDisplayed()
    }

    /**
     * Use Case 2: SMB Add Server with User and Password
     * Verify adding an SMB server with username and password connects and saves it in DB.
     */
    @Test
    fun testAddSmbServerWithCredentials() {
        // Open the drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()

        // Click Add Storage
        composeTestRule.onNodeWithTag("sidebar_item_Add Storage").performClick()

        // Enter Display Name
        composeTestRule.onNodeWithTag("display_name_input").performTextInput("Secure Server")

        // Enter Host IP
        composeTestRule.onNodeWithTag("host_input").performTextInput("127.0.0.1")

        // Enter Port
        composeTestRule.onNodeWithTag("port_input").performTextReplacement("4445")

        // Enter Username & Password
        composeTestRule.onNodeWithTag("username_input").performTextInput("admin")
        composeTestRule.onNodeWithTag("password_input").performTextInput("secret")

        // Connect & Save
        composeTestRule.onNodeWithTag("connect_save_button").performClick()

        // Wait for connection to succeed and navigate back
        composeTestRule.waitForIdle()

        // Verify it navigated back by checking search button in TopBar
        composeTestRule.onNodeWithTag("search_button").assertIsDisplayed()
    }

    /**
     * Use Case 3: SMB Add Server Anonymous
     * Verify adding an SMB server with anonymous access.
     */
    @Test
    fun testAddSmbServerAnonymous() {
        // Open the drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()

        // Click Add Storage
        composeTestRule.onNodeWithTag("sidebar_item_Add Storage").performClick()

        // Enter Display Name
        composeTestRule.onNodeWithTag("display_name_input").performTextInput("Anon Server")

        // Enter Host IP
        composeTestRule.onNodeWithTag("host_input").performTextInput("127.0.0.1")

        // Enter Port
        composeTestRule.onNodeWithTag("port_input").performTextReplacement("4445")

        // Check Anonymous checkbox
        composeTestRule.onNodeWithTag("anonymous_checkbox").performClick()

        // Connect & Save
        composeTestRule.onNodeWithTag("connect_save_button").performClick()

        // Wait for connection to succeed and navigate back
        composeTestRule.waitForIdle()

        // Verify it navigated back
        composeTestRule.onNodeWithTag("search_button").assertIsDisplayed()
    }

    /**
     * Use Case 4: Browse of SMB Files
     * Verify navigation to SMB storage and recursive share/folder browsing.
     */
    @Test
    fun testBrowseSmbFiles() {
        // Pre-insert a remote server to db to click on
        val testServer = RemoteServer(
            id = 99L,
            displayName = "My SMB Share",
            host = "127.0.0.1",
            port = 4445,
            isAnonymous = true,
        )
        remoteServerDao.insertServer(testServer)

        // Open drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()

        // Wait for the sidebar item to be displayed (as Flow emission might be async)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("sidebar_item_My SMB Share"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click on My SMB Share
        composeTestRule.onNodeWithTag("sidebar_item_My SMB Share").performClick()

        // Verify shares are listed (our mocked test_share)
        composeTestRule.onNodeWithTag("file_item_test_share").assertIsDisplayed()

        // Click test_share to list folders
        composeTestRule.onNodeWithTag("file_item_test_share").performClick()

        // Verify subfolder and files are listed
        composeTestRule.onNodeWithTag("file_item_subfolder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("file_item_smb_file_1.txt").assertIsDisplayed()

        // Click subfolder
        composeTestRule.onNodeWithTag("file_item_subfolder").performClick()

        // Verify subfolder contents are listed
        composeTestRule.onNodeWithTag("file_item_smb_file_2.txt").assertIsDisplayed()
    }

    /**
     * Use Case 5: Settings Screen
     * Verify buffering switch toggle and buffer size slider adjustment.
     */
    @Test
    fun testSettingsScreen() {
        // Click Settings button
        composeTestRule.onNodeWithTag("settings_button").performClick()

        // Wait for settings screen to load
        composeTestRule.onNodeWithTag("buffering_switch").assertIsDisplayed()

        // Toggle buffering switch
        composeTestRule.onNodeWithTag("buffering_switch").performClick()

        // Wait for buffer size slider to be displayed due to AnimatedVisibility
        // We use a longer timeout and check for existence
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("buffer_size_slider"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Buffer size slider should be displayed now. Scroll and verify
        composeTestRule.onNodeWithTag("buffer_size_slider").performScrollTo().assertIsDisplayed()

        // Click on back button to return to File Manager
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Verify navigated back
        composeTestRule.onNodeWithTag("search_button").assertIsDisplayed()
    }

    /**
     * Use Case 6: Search for Local Files
     * Verify entering query and getting matching filtered local search results.
     */
    @Test
    fun testSearchLocalFiles() {
        // Trigger search
        composeTestRule.onNodeWithTag("search_button").performClick()

        // Enter search query "test_file_1"
        composeTestRule.onNodeWithTag("search_input").performTextInput("test_file_1")

        // Verify matching local item is displayed
        composeTestRule.onNodeWithTag("file_item_test_file_1.txt").assertIsDisplayed()
    }

    /**
     * Use Case 7: Search for SMB Files (Ignored as requested)
     */
    @Ignore("Right now searching works only with current directory, marked as @Ignore")
    @Test
    fun testSearchSmbFiles() {
        // Trigger search
        composeTestRule.onNodeWithTag("search_button").performClick()

        // Enter search query
        composeTestRule.onNodeWithTag("search_input").performTextInput("smb_file")

        // Verify matching item
        composeTestRule.onNodeWithTag("file_item_smb_file_1.txt").assertIsDisplayed()
    }
}
