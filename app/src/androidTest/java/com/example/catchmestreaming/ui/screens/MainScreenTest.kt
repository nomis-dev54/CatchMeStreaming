package com.example.catchmestreaming.ui.screens

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catchmestreaming.ui.theme.CatchMeStreamingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainScreen_displaysTopBar() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen()
            }
        }

        composeTestRule
            .onNodeWithText("CatchMeStreaming")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_displaysNavigationButtons() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen()
            }
        }

        // Check for help button
        composeTestRule
            .onNodeWithContentDescription("Help")
            .assertIsDisplayed()

        // Check for settings button
        composeTestRule
            .onNodeWithContentDescription("Settings")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_displaysCameraPermissionMessage_whenPermissionNotGranted() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen()
            }
        }

        // Should display camera permission message
        composeTestRule
            .onNodeWithText("Camera Permission Required")
            .assertIsDisplayed()

        // Should display permission button
        composeTestRule
            .onNodeWithText("Grant Camera Permission")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_displaysStatusCard() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen()
            }
        }

        // Check for status section
        composeTestRule
            .onNodeWithText("Status")
            .assertIsDisplayed()

        // Check for status indicators
        composeTestRule
            .onNodeWithText("Camera")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Streaming")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Recording")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_displaysControlButtons() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen()
            }
        }

        // Check for stream button (play arrow icon)
        composeTestRule
            .onNodeWithContentDescription("Start Stream")
            .assertIsDisplayed()

        // Check for record button (should be visible even if not explicitly labeled)
        composeTestRule
            .onAllNodesWithContentDescription("Record")
            .assertCountEquals(1)
    }

    @Test
    fun mainScreen_adaptsToCompactLayout() {
        val compactWindowSize = WindowSizeClass.calculateFromSize(
            DpSize(400.dp, 800.dp)
        )

        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen(windowSizeClass = compactWindowSize)
            }
        }

        // Verify compact layout is used (camera preview should be full width)
        composeTestRule
            .onNodeWithText("CatchMeStreaming")
            .assertIsDisplayed()

        // Status should be displayed in compact layout
        composeTestRule
            .onNodeWithText("Status")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_adaptsToExpandedLayout() {
        val expandedWindowSize = WindowSizeClass.calculateFromSize(
            DpSize(1200.dp, 800.dp)
        )

        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen(windowSizeClass = expandedWindowSize)
            }
        }

        // Verify expanded layout elements are present
        composeTestRule
            .onNodeWithText("Status")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Start Stream")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_navigatesCorrectly() {
        var settingsClicked = false
        var helpClicked = false

        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen(
                    onNavigateToSettings = { settingsClicked = true },
                    onNavigateToHelp = { helpClicked = true }
                )
            }
        }

        // Test settings navigation
        composeTestRule
            .onNodeWithContentDescription("Settings")
            .performClick()

        assert(settingsClicked) { "Settings navigation should be triggered" }

        // Test help navigation
        composeTestRule
            .onNodeWithContentDescription("Help")
            .performClick()

        assert(helpClicked) { "Help navigation should be triggered" }
    }

    @Test
    fun mainScreen_displaysSecurityIndicators() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                MainScreen()
            }
        }

        // Check for configuration warnings (security indicators)
        composeTestRule
            .onNodeWithText("⚠️ Streaming not configured - Go to Settings")
            .assertExists() // May not be visible but should exist in composition

        composeTestRule
            .onNodeWithText("⚠️ Recording not configured - Go to Settings")
            .assertExists() // May not be visible but should exist in composition
    }
}