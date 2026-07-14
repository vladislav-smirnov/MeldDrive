package io.github.airdaydreamers.melddrive.ui.viewmodel

import app.cash.turbine.test
import io.github.airdaydreamers.melddrive.data.storage.SettingsManager
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsIntent
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SettingsViewModel] verifying state collection
 * and correct intent routing to [SettingsManager].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var settingsManager: SettingsManager
    private lateinit var viewModel: SettingsViewModel

    private val bufferingEnabledFlow = MutableStateFlow(false)
    private val bufferSizeMbFlow = MutableStateFlow(16)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsManager = mockk(relaxed = true)

        every { settingsManager.bufferingEnabled } returns bufferingEnabledFlow
        every { settingsManager.bufferSizeMb } returns bufferSizeMbFlow

        viewModel = SettingsViewModel(settingsManager)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Use Case: Observe State Changes from Settings Flow
     * Given the settings manager emits bufferingEnabled = true and bufferSize = 64
     * When viewmodel starts and is subscribed to
     * Then the StateFlow state should reflect these exact values
     */
    @Test
    fun testStateReflectsSettingsManager() = runBlocking {
        // Given
        bufferingEnabledFlow.value = true
        bufferSizeMbFlow.value = 64

        // When & Then
        viewModel.state.test {
            val currentState = awaitItem()
            assertEquals(true, currentState.bufferingEnabled)
            assertEquals(64, currentState.bufferSizeMb)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Use Case: Process SetBufferingEnabled Intent
     * Given the viewmodel is initialized
     * When onIntent(SettingsIntent.SetBufferingEnabled(true)) is called
     * Then it should launch a coroutine to enable buffering on settingsManager
     */
    @Test
    fun testSetBufferingEnabledIntent() {
        // When
        viewModel.onIntent(SettingsIntent.SetBufferingEnabled(true))

        // Then
        coVerify(exactly = 1) { settingsManager.setBufferingEnabled(true) }
    }

    /**
     * Use Case: Process SetBufferSizeMb Intent
     * Given the viewmodel is initialized
     * When onIntent(SettingsIntent.SetBufferSizeMb(32)) is called
     * Then it should launch a coroutine to update buffer size on settingsManager
     */
    @Test
    fun testSetBufferSizeMbIntent() {
        // When
        viewModel.onIntent(SettingsIntent.SetBufferSizeMb(32))

        // Then
        coVerify(exactly = 1) { settingsManager.setBufferSizeMb(32) }
    }
}
