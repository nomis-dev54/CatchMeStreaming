package com.example.catchmestreaming.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catchmestreaming.ui.theme.CatchMeStreamingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelpScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun helpScreen_displaysTopBar() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        composeTestRule
            .onNodeWithText("Help & Support")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_displaysAllTabs() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Check all tab titles are present
        composeTestRule
            .onNodeWithText("Getting Started")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Features")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Troubleshooting")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Security")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("FAQ")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_displaysGettingStartedContent() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Getting Started should be the default tab
        composeTestRule
            .onNodeWithText("Welcome to CatchMeStreaming!")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Follow these simple steps to start streaming and recording:")
            .assertIsDisplayed()

        // Check for step cards
        composeTestRule
            .onNodeWithText("Step 1: Configure Settings")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Step 2: Grant Camera Permission")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Step 3: Start Streaming")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Step 4: Record Videos (Optional)")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_switchesBetweenTabs() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Click on Features tab
        composeTestRule
            .onNodeWithText("Features")
            .performClick()

        // Should display Features content
        composeTestRule
            .onNodeWithText("App Features")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("HTTP Streaming")
            .assertIsDisplayed()

        // Click on Security tab
        composeTestRule
            .onNodeWithText("Security")
            .performClick()

        // Should display Security content
        composeTestRule
            .onNodeWithText("Security & Privacy")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Credential Protection")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_displaysSecurityInformation() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Navigate to Security tab
        composeTestRule
            .onNodeWithText("Security")
            .performClick()

        // Check security features are displayed
        composeTestRule
            .onNodeWithText("Credential Protection")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Network Security")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Input Validation")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Privacy Protection")
            .assertIsDisplayed()

        // Check security best practices
        composeTestRule
            .onNodeWithText("Security Best Practices")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_displaysTroubleshootingGuide() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Navigate to Troubleshooting tab
        composeTestRule
            .onNodeWithText("Troubleshooting")
            .performClick()

        // Check troubleshooting content
        composeTestRule
            .onNodeWithText("Common Issues & Solutions")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Camera permission denied")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Streaming not working")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Poor streaming quality")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Recording fails")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_expandsAndCollapsesTroubleshootingItems() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Navigate to Troubleshooting tab
        composeTestRule
            .onNodeWithText("Troubleshooting")
            .performClick()

        // Find the first troubleshooting item and click to expand
        composeTestRule
            .onNodeWithText("Camera permission denied")
            .assertIsDisplayed()

        // Click the expand button (arrow down)
        composeTestRule
            .onNodeWithContentDescription("Expand")
            .onFirst()
            .performClick()

        // Should display solution
        composeTestRule
            .onNodeWithText("Go to Android Settings > Apps > CatchMeStreaming > Permissions and enable Camera and Microphone.")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_displaysFAQContent() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Navigate to FAQ tab
        composeTestRule
            .onNodeWithText("FAQ")
            .performClick()

        // Check FAQ content
        composeTestRule
            .onNodeWithText("Frequently Asked Questions")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("What video formats are supported?")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Can I stream to multiple devices?")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("What's the maximum recording time?")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_expandsAndCollapsesFAQItems() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Navigate to FAQ tab
        composeTestRule
            .onNodeWithText("FAQ")
            .performClick()

        // Find the first FAQ item and click to expand
        composeTestRule
            .onNodeWithText("What video formats are supported?")
            .assertIsDisplayed()

        // Click the expand button
        composeTestRule
            .onAllNodesWithContentDescription("Expand")
            .onFirst()
            .performClick()

        // Should display answer
        composeTestRule
            .onNodeWithText("The app streams in HTTP format and records MP4 files with H.264 video and AAC audio codecs.")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_displaysFeatureInformation() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Navigate to Features tab
        composeTestRule
            .onNodeWithText("Features")
            .performClick()

        // Check feature content
        composeTestRule
            .onNodeWithText("App Features")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("HTTP Streaming")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Local Recording")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Security Features")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Adaptive UI")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Advanced Configuration")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_navigatesBackCorrectly() {
        var backCalled = false

        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen(
                    onNavigateBack = { backCalled = true }
                )
            }
        }

        // Click back button
        composeTestRule
            .onNodeWithContentDescription("Back")
            .performClick()

        assert(backCalled) { "Back navigation should be triggered" }
    }

    @Test
    fun helpScreen_displaysFirstTimeUserTips() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Should display first time user tips in Getting Started
        composeTestRule
            .onNodeWithText("First Time User Tips")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("• Start with default settings for quick setup\n• Test streaming on local network first\n• Check available storage before recording\n• Use lower quality for slower devices")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_displaysCompatibilityInformation() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpScreen()
            }
        }

        // Navigate to FAQ to find compatibility info
        composeTestRule
            .onNodeWithText("FAQ")
            .performClick()

        composeTestRule
            .onNodeWithText("Which Android versions are supported?")
            .assertIsDisplayed()
    }
}