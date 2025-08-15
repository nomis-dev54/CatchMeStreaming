package com.example.catchmestreaming.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.catchmestreaming.ui.theme.CatchMeStreamingTheme

/**
 * Interactive tutorial system for guiding users through the app
 */
@Composable
fun FirstRunTutorial(
    isVisible: Boolean,
    currentStep: TutorialStep,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Dialog(
            onDismissRequest = onSkip
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tutorial step indicator
                    LinearProgressIndicator(
                        progress = (currentStep.ordinal + 1) / TutorialStep.values().size.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Step icon
                    Icon(
                        currentStep.icon,
                        contentDescription = currentStep.title,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Step title
                    Text(
                        currentStep.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Step description
                    Text(
                        currentStep.description,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (currentStep.details.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    currentStep.details,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Previous/Skip button
                        TextButton(
                            onClick = if (currentStep.ordinal == 0) onSkip else onPreviousStep
                        ) {
                            Text(
                                if (currentStep.ordinal == 0) "Skip Tutorial" else "Previous"
                            )
                        }
                        
                        // Next/Complete button
                        Button(
                            onClick = if (currentStep == TutorialStep.values().last()) onComplete else onNextStep
                        ) {
                            Text(
                                if (currentStep == TutorialStep.values().last()) "Get Started!" else "Next"
                            )
                        }
                    }
                    
                    // Step counter
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${currentStep.ordinal + 1} of ${TutorialStep.values().size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Contextual help tooltip that can be attached to any composable
 */
@Composable
fun HelpTooltip(
    text: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Info
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .padding(8.dp)
                .clickable { onDismiss() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = "Help",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.inverseOnSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

/**
 * Floating help button that can trigger contextual help
 */
@Composable
fun FloatingHelpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeOut(),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Help",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Feature spotlight that highlights specific UI elements
 */
@Composable
fun FeatureSpotlight(
    title: String,
    description: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    targetContent: @Composable () -> Unit
) {
    if (isVisible) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Semi-transparent overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { onDismiss() }
                    .zIndex(1f)
            )
            
            // Highlighted content
            Box(
                modifier = Modifier
                    .zIndex(2f)
            ) {
                targetContent()
            }
            
            // Spotlight explanation
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .zIndex(3f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Got it!")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tutorial steps for first-run experience
 */
enum class TutorialStep(
    val title: String,
    val description: String,
    val details: String,
    val icon: ImageVector
) {
    WELCOME(
        "Welcome to CatchMeStreaming!",
        "Let's get you set up for streaming and recording in just a few steps.",
        "This tutorial will guide you through the essential features and help you get started quickly.",
        Icons.Default.Star
    ),
    
    PERMISSIONS(
        "Camera & Microphone Access",
        "Grant permissions to access your device's camera and microphone.",
        "• Camera permission is required for video streaming\n" +
        "• Microphone permission enables audio recording\n" +
        "• You can change these permissions later in Android Settings",
        Icons.Default.Settings
    ),
    
    SETTINGS(
        "Configure Your Settings",
        "Set up streaming server and recording preferences.",
        "• Default settings work for most users\n" +
        "• Server URL auto-detects your device IP\n" +
        "• Choose video quality based on your network\n" +
        "• Configure recording location and limits",
        Icons.Default.Settings
    ),
    
    STREAMING(
        "Start Streaming",
        "Use the play button to begin streaming to your configured server.",
        "• Green play button starts streaming\n" +
        "• Stream URL appears when active\n" +
        "• Any device can connect using the URL\n" +
        "• Compatible with VLC, browsers, and media players",
        Icons.Default.PlayArrow
    ),
    
    RECORDING(
        "Record Videos",
        "Capture videos locally with the record button.",
        "• Square button starts recording\n" +
        "• Round red button indicates active recording\n" +
        "• Videos saved to your configured directory\n" +
        "• Monitor storage space in status area",
        Icons.Default.PlayArrow
    ),
    
    HELP(
        "Get Help When Needed",
        "Access comprehensive help and troubleshooting guides.",
        "• Tap the help button for contextual assistance\n" +
        "• Full help system covers all features\n" +
        "• Troubleshooting guide for common issues\n" +
        "• Security and privacy information included",
        Icons.Default.Info
    )
}

// Extension property for waving hand icon (fallback to star if not available)

@Preview(showBackground = true)
@Composable
fun FirstRunTutorialPreview() {
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

@Preview(showBackground = true)
@Composable
fun HelpTooltipPreview() {
    CatchMeStreamingTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            HelpTooltip(
                text = "Tap this button to start streaming to your configured server.",
                isVisible = true,
                onDismiss = {},
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FloatingHelpButtonPreview() {
    CatchMeStreamingTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            FloatingHelpButton(
                onClick = {},
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}