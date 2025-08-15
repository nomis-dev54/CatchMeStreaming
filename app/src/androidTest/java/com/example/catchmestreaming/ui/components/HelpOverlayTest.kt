package com.example.catchmestreaming.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catchmestreaming.ui.theme.CatchMeStreamingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelpOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun firstRunTutorial_displaysWelcomeStep() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FirstRunTutorial(
                    isVisible = true,
                    currentStep = TutorialStep.WELCOME,
                    onNextStep = {},
                    onPreviousStep = {},
                    onSkip = {},
                    onComplete = {}
                )
            }
        }

        // Check welcome content
        composeTestRule
            .onNodeWithText("Welcome to CatchMeStreaming!")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Let's get you set up for streaming and recording in just a few steps.")
            .assertIsDisplayed()

        // Check navigation buttons
        composeTestRule
            .onNodeWithText("Skip Tutorial")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Next")
            .assertIsDisplayed()
    }

    @Test
    fun firstRunTutorial_displaysProgressIndicator() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FirstRunTutorial(
                    isVisible = true,
                    currentStep = TutorialStep.WELCOME,
                    onNextStep = {},
                    onPreviousStep = {},
                    onSkip = {},
                    onComplete = {}
                )
            }
        }

        // Check step counter
        composeTestRule
            .onNodeWithText("1 of ${TutorialStep.values().size}")
            .assertIsDisplayed()
    }

    @Test
    fun firstRunTutorial_displaysPermissionsStep() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FirstRunTutorial(
                    isVisible = true,
                    currentStep = TutorialStep.PERMISSIONS,
                    onNextStep = {},
                    onPreviousStep = {},
                    onSkip = {},
                    onComplete = {}
                )
            }
        }

        // Check permissions content
        composeTestRule
            .onNodeWithText("Camera & Microphone Access")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Grant permissions to access your device's camera and microphone.")
            .assertIsDisplayed()

        // Check detailed information
        composeTestRule
            .onNodeWithText("• Camera permission is required for video streaming\n• Microphone permission enables audio recording\n• You can change these permissions later in Android Settings")
            .assertIsDisplayed()

        // Should show Previous button (not Skip)
        composeTestRule
            .onNodeWithText("Previous")
            .assertIsDisplayed()
    }

    @Test
    fun firstRunTutorial_displaysSettingsStep() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FirstRunTutorial(
                    isVisible = true,
                    currentStep = TutorialStep.SETTINGS,
                    onNextStep = {},
                    onPreviousStep = {},
                    onSkip = {},
                    onComplete = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Configure Your Settings")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Set up streaming server and recording preferences.")
            .assertIsDisplayed()
    }

    @Test
    fun firstRunTutorial_displaysStreamingStep() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FirstRunTutorial(
                    isVisible = true,
                    currentStep = TutorialStep.STREAMING,
                    onNextStep = {},
                    onPreviousStep = {},
                    onSkip = {},
                    onComplete = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Start Streaming")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Use the play button to begin streaming to your configured server.")
            .assertIsDisplayed()
    }

    @Test
    fun firstRunTutorial_displaysRecordingStep() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FirstRunTutorial(
                    isVisible = true,
                    currentStep = TutorialStep.RECORDING,
                    onNextStep = {},
                    onPreviousStep = {},
                    onSkip = {},
                    onComplete = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Record Videos")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Capture videos locally with the record button.")
            .assertIsDisplayed()
    }

    @Test
    fun firstRunTutorial_displaysHelpStep() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FirstRunTutorial(
                    isVisible = true,
                    currentStep = TutorialStep.HELP,
                    onNextStep = {},
                    onPreviousStep = {},
                    onSkip = {},
                    onComplete = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Get Help When Needed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Access comprehensive help and troubleshooting guides.")
            .assertIsDisplayed()

        // Last step should show "Get Started!" button
        composeTestRule
            .onNodeWithText("Get Started!")
            .assertIsDisplayed()
    }

    @Test
    fun firstRunTutorial_handlesNavigation() {
        var nextClicked = false
        var previousClicked = false
        var skipClicked = false
        var completeClicked = false

        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FirstRunTutorial(
                    isVisible = true,
                    currentStep = TutorialStep.PERMISSIONS,
                    onNextStep = { nextClicked = true },
                    onPreviousStep = { previousClicked = true },
                    onSkip = { skipClicked = true },
                    onComplete = { completeClicked = true }
                )
            }
        }

        // Test next button
        composeTestRule
            .onNodeWithText("Next")
            .performClick()

        assert(nextClicked) { "Next should be called" }

        // Test previous button
        composeTestRule
            .onNodeWithText("Previous")
            .performClick()

        assert(previousClicked) { "Previous should be called" }
    }

    @Test
    fun firstRunTutorial_handlesCompletion() {
        var completeClicked = false

        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FirstRunTutorial(
                    isVisible = true,
                    currentStep = TutorialStep.HELP, // Last step
                    onNextStep = {},
                    onPreviousStep = {},
                    onSkip = {},
                    onComplete = { completeClicked = true }
                )
            }
        }

        // Test complete button
        composeTestRule
            .onNodeWithText("Get Started!")
            .performClick()

        assert(completeClicked) { "Complete should be called" }
    }

    @Test
    fun helpTooltip_displaysAndDismisses() {
        var dismissed = false

        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpTooltip(
                    text = "This is a helpful tooltip",
                    isVisible = true,
                    onDismiss = { dismissed = true }
                )
            }
        }

        // Check tooltip content
        composeTestRule
            .onNodeWithText("This is a helpful tooltip")
            .assertIsDisplayed()

        // Check help icon
        composeTestRule
            .onNodeWithContentDescription("Help")
            .assertIsDisplayed()

        // Check close button
        composeTestRule
            .onNodeWithContentDescription("Close")
            .assertIsDisplayed()

        // Click to dismiss
        composeTestRule
            .onNodeWithText("This is a helpful tooltip")
            .performClick()

        assert(dismissed) { "Tooltip should be dismissed when clicked" }
    }

    @Test
    fun helpTooltip_hidesWhenNotVisible() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                HelpTooltip(
                    text = "This tooltip should be hidden",
                    isVisible = false,
                    onDismiss = {}
                )
            }
        }

        // Tooltip should not be displayed
        composeTestRule
            .onNodeWithText("This tooltip should be hidden")
            .assertDoesNotExist()
    }

    @Test
    fun floatingHelpButton_displaysAndRespondsToClick() {
        var clicked = false

        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FloatingHelpButton(
                    onClick = { clicked = true },
                    isVisible = true
                )
            }
        }

        // Check help button is displayed
        composeTestRule
            .onNodeWithContentDescription("Help")
            .assertIsDisplayed()

        // Click the button
        composeTestRule
            .onNodeWithContentDescription("Help")
            .performClick()

        assert(clicked) { "Help button click should be handled" }
    }

    @Test
    fun floatingHelpButton_hidesWhenNotVisible() {
        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FloatingHelpButton(
                    onClick = {},
                    isVisible = false
                )
            }
        }

        // Button should not be displayed
        composeTestRule
            .onNodeWithContentDescription("Help")
            .assertDoesNotExist()
    }

    @Test
    fun featureSpotlight_displaysCorrectly() {
        var dismissed = false

        composeTestRule.setContent {
            CatchMeStreamingTheme {
                FeatureSpotlight(
                    title = "Feature Title",
                    description = "This is a feature description",
                    isVisible = true,
                    onDismiss = { dismissed = true },
                    targetContent = {
                        // Target content to highlight
                    }
                )
            }
        }

        // Check spotlight content
        composeTestRule
            .onNodeWithText("Feature Title")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("This is a feature description")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Got it!")
            .assertIsDisplayed()

        // Test dismiss
        composeTestRule
            .onNodeWithText("Got it!")
            .performClick()

        assert(dismissed) { "Spotlight should be dismissed when button clicked" }
    }

    @Test
    fun tutorial_allStepsHaveValidContent() {
        // Test that all tutorial steps have proper content
        TutorialStep.values().forEach { step ->
            composeTestRule.setContent {
                CatchMeStreamingTheme {
                    FirstRunTutorial(
                        isVisible = true,
                        currentStep = step,
                        onNextStep = {},
                        onPreviousStep = {},
                        onSkip = {},
                        onComplete = {}
                    )
                }
            }

            // Each step should have a title and description
            composeTestRule
                .onNodeWithText(step.title)
                .assertIsDisplayed()

            composeTestRule
                .onNodeWithText(step.description)
                .assertIsDisplayed()

            // Steps with details should show them
            if (step.details.isNotEmpty()) {
                composeTestRule
                    .onNodeWithText(step.details)
                    .assertExists()
            }
        }
    }
}