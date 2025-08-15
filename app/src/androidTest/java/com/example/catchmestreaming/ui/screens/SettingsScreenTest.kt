package com.example.catchmestreaming.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.catchmestreaming.data.RecordingConfig
import com.example.catchmestreaming.data.StreamConfig
import com.example.catchmestreaming.ui.theme.CatchMeStreamingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun settingsScreen_displaysTopBar() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        composeTestRule
            .onNodeWithText("Settings")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAllSections() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        // Check all main sections are present
        composeTestRule
            .onNodeWithText("HTTP Streaming Configuration")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Streaming Configuration")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Recording Configuration")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Security & Privacy")
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysSecurityFeatures() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        // Check security features are displayed
        composeTestRule
            .onNodeWithText("🔒 Security Features")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("• Credentials stored in Android Keystore\n• Local network streaming only\n• No data collection or external transmission")
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_handlesSafePasswordInput() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        // Enable authentication to show password field
        composeTestRule
            .onNodeWithText("Enable Authentication")
            .performClick()

        // Check password field is present and masked
        composeTestRule
            .onNodeWithText("Password")
            .assertIsDisplayed()

        // Check show/hide password toggle
        composeTestRule
            .onNodeWithText("Show")
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_validatesInputs() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        // Enter invalid port
        composeTestRule
            .onNodeWithText("Port")
            .performTextReplacement("999999")

        // Try to save
        composeTestRule
            .onNodeWithText("Save Settings")
            .performClick()

        // Should show validation error
        composeTestRule
            .onNodeWithText("Port must be between 1 and 65535")
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_handlesAuthenticationToggle() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        // Initially authentication should be disabled
        composeTestRule
            .onNodeWithText("Username")
            .assertDoesNotExist()

        // Enable authentication
        composeTestRule
            .onNodeWithText("Enable Authentication")
            .performClick()

        // Username and password fields should appear
        composeTestRule
            .onNodeWithText("Username")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Password")
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysStorageInformation() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen(
                    currentRecordingConfig = RecordingConfig.createDefault()
                )
            }
        }

        // Recording information should be displayed
        composeTestRule
            .onNodeWithText("Recording Information")
            .assertExists()

        composeTestRule
            .onNodeWithText("• Format: MP4 (H.264 video + AAC audio)\n• Storage: /Movies/CatchMeStreaming/\n• Quality: Configurable resolution")
            .assertExists()
    }

    @Test
    fun settingsScreen_handlesQualitySelection() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        // Check stream quality dropdown
        composeTestRule
            .onNodeWithText("Stream Quality")
            .assertIsDisplayed()

        // Check recording quality dropdown
        composeTestRule
            .onNodeWithText("Recording Quality")
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_hasWorkingActionButtons() {
        var saveCalled = false
        var backCalled = false

        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen(
                    onSaveConfig = { saveCalled = true },
                    onNavigateBack = { backCalled = true }
                )
            }
        }

        // Test cancel button
        composeTestRule
            .onNodeWithText("Cancel")
            .performClick()

        assert(backCalled) { "Cancel should trigger navigation back" }

        // Test save button (should validate and call save if valid)
        composeTestRule
            .onNodeWithText("Save Settings")
            .performClick()

        // Should call save with valid default configuration
        assert(saveCalled) { "Save button should trigger save callback" }
    }

    @Test
    fun settingsScreen_displaysDefaultConfiguration() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        // Should show default configuration notice
        composeTestRule
            .onNodeWithText("✅ Default Configuration Applied")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Ready to stream with auto-detected settings. Modify below as needed.")
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_masksPasswordProperly() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        // Enable authentication
        composeTestRule
            .onNodeWithText("Enable Authentication")
            .performClick()

        // Enter password
        composeTestRule
            .onNodeWithText("Password")
            .performTextInput("secretpassword123")

        // Password should be masked by default
        composeTestRule
            .onNodeWithText("secretpassword123")
            .assertDoesNotExist()

        // Click show password
        composeTestRule
            .onNodeWithText("Show")
            .performClick()

        // Now password should be visible
        composeTestRule
            .onNodeWithText("secretpassword123")
            .assertExists()
    }

    @Test
    fun settingsScreen_validatesPasswordSecurity() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        // Enable authentication
        composeTestRule
            .onNodeWithText("Enable Authentication")
            .performClick()

        // Enter weak password
        composeTestRule
            .onNodeWithText("Password")
            .performTextInput("weak")

        // Try to save
        composeTestRule
            .onNodeWithText("Save Settings")
            .performClick()

        // Should show password strength error
        composeTestRule
            .onNodeWithText("Password must be at least 8 characters")
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_handlesNetworkConfiguration() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                SettingsScreen()
            }
        }

        // Check server URL field
        composeTestRule
            .onNodeWithText("Server URL")
            .assertIsDisplayed()

        // Check IP refresh button
        composeTestRule
            .onNodeWithContentDescription("Refresh IP")
            .assertIsDisplayed()

        // Check port field
        composeTestRule
            .onNodeWithText("Port")
            .assertIsDisplayed()

        // Check stream path field
        composeTestRule
            .onNodeWithText("Stream Path")
            .assertIsDisplayed()
    }
}