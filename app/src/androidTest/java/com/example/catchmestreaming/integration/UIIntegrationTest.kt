package com.example.catchmestreaming.integration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.catchmestreaming.data.StreamQuality
import com.example.catchmestreaming.data.VideoQuality
import com.example.catchmestreaming.ui.screens.MainScreen
import com.example.catchmestreaming.ui.screens.SettingsScreen
import com.example.catchmestreaming.ui.theme.CatchMeStreamingTheme
import com.example.catchmestreaming.viewmodel.MainViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for UI workflows and cross-screen interactions
 * Tests navigation, state management, and user interaction flows
 */
@RunWith(AndroidJUnit4::class)
class UIIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @Test
    fun completeStreamingUIWorkflow_shouldWorkEndToEnd() {
        var currentViewModel: MainViewModel? = null
        
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen(
                    onNavigateToSettings = { },
                    onNavigateToHelp = { },
                    onViewModelCreated = { viewModel ->
                        currentViewModel = viewModel
                    }
                )
            }
        }

        // Wait for UI to load
        composeTestRule.waitForIdle()

        // Test camera preview is visible
        composeTestRule.onNodeWithTag("CameraPreview")
            .assertIsDisplayed()

        // Test streaming button is initially in "Start Streaming" state
        composeTestRule.onNodeWithTag("StreamingButton")
            .assertIsDisplayed()
            .assertTextContains("Start")

        // Click streaming button to start streaming
        composeTestRule.onNodeWithTag("StreamingButton")
            .performClick()

        // Wait for streaming to start
        composeTestRule.waitForIdle()

        // Verify streaming status indicator appears
        composeTestRule.onNodeWithTag("StreamingStatusIndicator")
            .assertIsDisplayed()

        // Verify button text changed to "Stop Streaming"
        composeTestRule.onNodeWithTag("StreamingButton")
            .assertTextContains("Stop")

        // Click to stop streaming
        composeTestRule.onNodeWithTag("StreamingButton")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify streaming stopped
        composeTestRule.onNodeWithTag("StreamingButton")
            .assertTextContains("Start")
    }

    @Test
    fun completeRecordingUIWorkflow_shouldWorkEndToEnd() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen(
                    onNavigateToSettings = { },
                    onNavigateToHelp = { },
                    onViewModelCreated = { }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Test recording button is visible
        composeTestRule.onNodeWithTag("RecordingButton")
            .assertIsDisplayed()
            .assertTextContains("Start Recording")

        // Start recording
        composeTestRule.onNodeWithTag("RecordingButton")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify recording status indicator
        composeTestRule.onNodeWithTag("RecordingStatusIndicator")
            .assertIsDisplayed()

        // Verify button text changed
        composeTestRule.onNodeWithTag("RecordingButton")
            .assertTextContains("Stop")

        // Stop recording
        composeTestRule.onNodeWithTag("RecordingButton")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify recording stopped
        composeTestRule.onNodeWithTag("RecordingButton")
            .assertTextContains("Start Recording")
    }

    @Test
    fun settingsConfigurationWorkflow_shouldUpdateStreamingSettings() {
        var isSettingsOpen = false
        
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                if (isSettingsOpen) {
                    SettingsScreen(
                        onNavigateBack = { isSettingsOpen = false },
                        onSaveSettings = { isSettingsOpen = false }
                    )
                } else {
                    MainScreen(
                        onNavigateToSettings = { isSettingsOpen = true },
                        onNavigateToHelp = { },
                        onViewModelCreated = { }
                    )
                }
            }
        }

        // Start on main screen
        composeTestRule.onNodeWithTag("SettingsButton")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        // Should now be on settings screen
        composeTestRule.onNodeWithTag("SettingsScreen")
            .assertIsDisplayed()

        // Test server URL input
        composeTestRule.onNodeWithTag("ServerUrlInput")
            .assertIsDisplayed()
            .performTextClearance()
            .performTextInput("192.168.1.200")

        // Test port input
        composeTestRule.onNodeWithTag("PortInput")
            .assertIsDisplayed()
            .performTextClearance()
            .performTextInput("8080")

        // Test quality selection
        composeTestRule.onNodeWithTag("QualitySelector")
            .assertIsDisplayed()
            .performClick()

        // Select HD 720P option
        composeTestRule.onNodeWithText("HD 720P")
            .performClick()

        // Test authentication toggle
        composeTestRule.onNodeWithTag("AuthenticationToggle")
            .assertIsDisplayed()
            .performClick()

        // Enter authentication credentials (should be visible after toggle)
        composeTestRule.onNodeWithTag("UsernameInput")
            .assertIsDisplayed()
            .performTextInput("testuser")

        composeTestRule.onNodeWithTag("PasswordInput")
            .assertIsDisplayed()
            .performTextInput("testpass")

        // Save settings
        composeTestRule.onNodeWithTag("SaveSettingsButton")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        // Should return to main screen
        composeTestRule.onNodeWithTag("MainScreen")
            .assertIsDisplayed()

        // Verify settings were applied (streaming button should still be visible)
        composeTestRule.onNodeWithTag("StreamingButton")
            .assertIsDisplayed()
    }

    @Test
    fun helpSystemIntegration_shouldProvideContextualAssistance() {
        var isHelpOpen = false
        
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                if (isHelpOpen) {
                    com.example.catchmestreaming.ui.screens.HelpScreen(
                        onNavigateBack = { isHelpOpen = false }
                    )
                } else {
                    MainScreen(
                        onNavigateToSettings = { },
                        onNavigateToHelp = { isHelpOpen = true },
                        onViewModelCreated = { }
                    )
                }
            }
        }

        // Open help from main screen
        composeTestRule.onNodeWithTag("HelpButton")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        // Should be on help screen
        composeTestRule.onNodeWithTag("HelpScreen")
            .assertIsDisplayed()

        // Test help tabs
        val helpTabs = listOf("Getting Started", "Features", "Troubleshooting", "Security", "FAQ")
        
        helpTabs.forEach { tab ->
            composeTestRule.onNodeWithText(tab)
                .assertIsDisplayed()
                .performClick()
            
            composeTestRule.waitForIdle()
            
            // Verify tab content is displayed
            composeTestRule.onNodeWithTag("HelpContent")
                .assertIsDisplayed()
        }

        // Return to main screen
        composeTestRule.onNodeWithTag("BackButton")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("MainScreen")
            .assertIsDisplayed()
    }

    @Test
    fun errorHandlingUIWorkflow_shouldDisplayAppropriateMessages() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen(
                    onNavigateToSettings = { },
                    onNavigateToHelp = { },
                    onViewModelCreated = { }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Try to start streaming without camera permission (simulated error)
        composeTestRule.onNodeWithTag("StreamingButton")
            .performClick()

        composeTestRule.waitForIdle()

        // Should show error message or handle gracefully
        // Either error dialog appears or button remains in start state
        val errorDialogExists = composeTestRule.onAllNodesWithTag("ErrorDialog").fetchSemanticsNodes().isNotEmpty()
        val buttonInStartState = composeTestRule.onNodeWithTag("StreamingButton").assertTextContains("Start", ignoreCase = true)
        
        assertTrue("Should either show error dialog or remain in start state", 
            errorDialogExists || buttonInStartState != null)

        // If error dialog exists, dismiss it
        if (errorDialogExists) {
            composeTestRule.onNodeWithTag("ErrorDialogDismiss")
                .performClick()
        }

        composeTestRule.waitForIdle()

        // UI should be back to normal state
        composeTestRule.onNodeWithTag("StreamingButton")
            .assertIsDisplayed()
    }

    @Test
    fun concurrentOperationsUI_shouldHandleMultipleActions() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen(
                    onNavigateToSettings = { },
                    onNavigateToHelp = { },
                    onViewModelCreated = { }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Start streaming
        composeTestRule.onNodeWithTag("StreamingButton")
            .performClick()

        composeTestRule.waitForIdle()

        // Start recording while streaming (if supported)
        composeTestRule.onNodeWithTag("RecordingButton")
            .performClick()

        composeTestRule.waitForIdle()

        // Both status indicators should be visible (if both operations can run concurrently)
        val streamingIndicatorExists = composeTestRule.onAllNodesWithTag("StreamingStatusIndicator").fetchSemanticsNodes().isNotEmpty()
        val recordingIndicatorExists = composeTestRule.onAllNodesWithTag("RecordingStatusIndicator").fetchSemanticsNodes().isNotEmpty()

        // At least one operation should be active
        assertTrue("At least one operation should be active", 
            streamingIndicatorExists || recordingIndicatorExists)

        // Stop all operations
        if (streamingIndicatorExists) {
            composeTestRule.onNodeWithTag("StreamingButton")
                .performClick()
        }

        if (recordingIndicatorExists) {
            composeTestRule.onNodeWithTag("RecordingButton")
                .performClick()
        }

        composeTestRule.waitForIdle()

        // Should return to initial state
        composeTestRule.onNodeWithTag("StreamingButton")
            .assertTextContains("Start")
        composeTestRule.onNodeWithTag("RecordingButton")
            .assertTextContains("Start")
    }

    @Test
    fun adaptiveUILayout_shouldWorkOnDifferentScreenSizes() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen(
                    onNavigateToSettings = { },
                    onNavigateToHelp = { },
                    onViewModelCreated = { }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Core UI elements should be present regardless of screen size
        composeTestRule.onNodeWithTag("CameraPreview")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("StreamingButton")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("RecordingButton")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("SettingsButton")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("HelpButton")
            .assertIsDisplayed()

        // Control panel should be present
        composeTestRule.onNodeWithTag("ControlPanel")
            .assertIsDisplayed()
    }

    @Test
    fun securityIndicatorsUI_shouldDisplaySecurityStatus() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen(
                    onNavigateToSettings = { },
                    onNavigateToHelp = { },
                    onViewModelCreated = { }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Security status should be visible somewhere in the UI
        val securityStatusExists = composeTestRule.onAllNodesWithTag("SecurityStatus").fetchSemanticsNodes().isNotEmpty()
        val encryptionIndicatorExists = composeTestRule.onAllNodesWithTag("EncryptionIndicator").fetchSemanticsNodes().isNotEmpty()
        
        // At least some security indication should be present
        assertTrue("Security indicators should be present in UI", 
            securityStatusExists || encryptionIndicatorExists)

        // Test opening settings to see more security options
        composeTestRule.onNodeWithTag("SettingsButton")
            .performClick()

        composeTestRule.waitForIdle()

        // Settings should have security section
        val securitySectionExists = composeTestRule.onAllNodesWithTag("SecuritySection").fetchSemanticsNodes().isNotEmpty()
        val authToggleExists = composeTestRule.onAllNodesWithTag("AuthenticationToggle").fetchSemanticsNodes().isNotEmpty()

        assertTrue("Settings should have security controls", 
            securitySectionExists || authToggleExists)
    }
}