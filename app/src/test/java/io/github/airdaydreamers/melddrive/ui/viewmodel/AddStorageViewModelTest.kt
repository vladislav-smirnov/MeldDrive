package io.github.airdaydreamers.melddrive.ui.viewmodel

import android.content.Context
import app.cash.turbine.test
import io.github.airdaydreamers.melddrive.R
import io.github.airdaydreamers.melddrive.data.repository.ServerRepository
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageIntent
import io.github.airdaydreamers.melddrive.ui.mvi.ServerType
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AddStorageViewModel] verifying form updates, validation logic,
 * and remote server persistence workflows via [ServerRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddStorageViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var serverRepository: ServerRepository
    private lateinit var mockContext: Context
    private lateinit var viewModel: AddStorageViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        serverRepository = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.getString(R.string.error_host_display_mandatory) } returns "Host and Display Name are mandatory"
        viewModel = AddStorageViewModel(serverRepository, mockContext)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Use Case: Server Type Change Updates Default Port
     * Given the add storage form is open
     * When ServerTypeChange intent is dispatched
     * Then serverType and port should be updated based on the enum default port
     */
    @Test
    fun testServerTypeChangeUpdatesPort() {
        // Given default is SMB with port 445
        assertEquals(ServerType.SMB, viewModel.state.value.serverType)
        assertEquals("445", viewModel.state.value.port)

        // When switching to WEBDAV
        viewModel.onIntent(AddStorageIntent.ServerTypeChange(ServerType.WEBDAV))

        // Then
        assertEquals(ServerType.WEBDAV, viewModel.state.value.serverType)
        assertEquals("80", viewModel.state.value.port)
    }

    /**
     * Use Case: Input Form Fields Update
     * Given the add storage form is open
     * When various field change intents are dispatched
     * Then the StateFlow should update the fields accordingly
     */
    @Test
    fun testFormFieldsUpdate() {
        // When
        viewModel.onIntent(AddStorageIntent.DisplayNameChange("NAS"))
        viewModel.onIntent(AddStorageIntent.HostChange("192.168.1.100"))
        viewModel.onIntent(AddStorageIntent.PortChange("4450"))
        viewModel.onIntent(AddStorageIntent.UsernameChange("bob"))
        viewModel.onIntent(AddStorageIntent.PasswordChange("secret"))
        viewModel.onIntent(AddStorageIntent.AnonymousChange(false))

        // Then
        val state = viewModel.state.value
        assertEquals("NAS", state.displayName)
        assertEquals("192.168.1.100", state.host)
        assertEquals("4450", state.port)
        assertEquals("bob", state.username)
        assertEquals("secret", state.password)
        assertFalse(state.isAnonymous)
    }

    /**
     * Use Case: Form Validation Error
     * Given the mandatory Host or Display Name fields are blank
     * When SaveServer intent is dispatched
     * Then the StateFlow should contain a validation error and not save to the repository
     */
    @Test
    fun testFormValidationFailure() {
        // Given - Display name is set but host is blank
        viewModel.onIntent(AddStorageIntent.DisplayNameChange("My NAS"))
        viewModel.onIntent(AddStorageIntent.HostChange(""))

        // When
        viewModel.onIntent(AddStorageIntent.SaveServer)

        // Then
        val state = viewModel.state.value
        assertEquals("Host and Display Name are mandatory", state.error)
        coVerify(exactly = 0) { serverRepository.addRemoteServer(any(), any()) }
    }

    /**
     * Use Case: Successful Server Creation
     * Given all form fields are correctly filled
     * When SaveServer intent is dispatched
     * Then the viewmodel should save the server and set the success state
     */
    @Test
    fun testSaveServerSuccess() = runBlocking {
        // Given
        viewModel.onIntent(AddStorageIntent.DisplayNameChange("Office"))
        viewModel.onIntent(AddStorageIntent.HostChange("10.0.0.5"))
        viewModel.onIntent(AddStorageIntent.PortChange("445"))
        viewModel.onIntent(AddStorageIntent.UsernameChange("user1"))
        viewModel.onIntent(AddStorageIntent.PasswordChange("pass1"))
        viewModel.onIntent(AddStorageIntent.AnonymousChange(false))

        // When & Then
        viewModel.state.test {
            viewModel.onIntent(AddStorageIntent.SaveServer)

            var state = awaitItem()
            while (!state.isSuccess) {
                state = awaitItem()
            }

            assertFalse(state.isLoading)
            assertTrue(state.isSuccess)
            assertNull(state.error)

            coVerify(exactly = 1) {
                serverRepository.addRemoteServer(
                    match {
                        it.displayName == "Office" &&
                            it.host == "10.0.0.5" &&
                            it.port == 445 &&
                            it.username == "user1" &&
                            it.password == null
                    },
                    "pass1",
                )
            }
        }
    }
}
