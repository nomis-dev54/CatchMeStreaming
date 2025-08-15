package com.example.catchmestreaming.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.catchmestreaming.ui.theme.CatchMeStreamingTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(HelpTab.GettingStarted) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Help & Support",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                HelpTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { 
                            Text(
                                tab.title,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize
                            ) 
                        },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }
            
            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    HelpTab.GettingStarted -> GettingStartedContent()
                    HelpTab.Features -> FeaturesContent()
                    HelpTab.Troubleshooting -> TroubleshootingContent()
                    HelpTab.Security -> SecurityContent()
                    HelpTab.FAQ -> FAQContent()
                }
            }
        }
    }
}

private enum class HelpTab(
    val title: String,
    val icon: ImageVector
) {
    GettingStarted("Getting Started", Icons.Default.PlayArrow),
    Features("Features", Icons.Default.Settings),
    Troubleshooting("Troubleshooting", Icons.Default.Build),
    Security("Security", Icons.Default.Lock),
    FAQ("FAQ", Icons.Default.Info)
}

@Composable
private fun GettingStartedContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Welcome to CatchMeStreaming!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "Follow these simple steps to start streaming and recording:",
            style = MaterialTheme.typography.bodyLarge
        )
        
        // Quick Start Steps
        HelpCard(
            icon = Icons.Default.Settings,
            title = "Step 1: Configure Settings",
            description = "Go to Settings to configure your streaming and recording preferences."
        ) {
            Text("• Set your streaming server URL and port\n" +
                 "• Choose video quality (720p, 1080p)\n" +
                 "• Enable/disable audio\n" +
                 "• Configure recording options")
        }
        
        HelpCard(
            icon = Icons.Default.Settings,
            title = "Step 2: Grant Camera Permission",
            description = "Allow camera access for video streaming and recording."
        ) {
            Text("• Tap 'Grant Camera Permission' button\n" +
                 "• Allow microphone access if recording audio\n" +
                 "• Camera preview will appear once granted")
        }
        
        HelpCard(
            icon = Icons.Default.PlayArrow,
            title = "Step 3: Start Streaming",
            description = "Tap the play button to begin streaming to your configured server."
        ) {
            Text("• Green play button starts streaming\n" +
                 "• Red stop button stops streaming\n" +
                 "• Stream URL is displayed when active")
        }
        
        HelpCard(
            icon = Icons.Default.PlayArrow,
            title = "Step 4: Record Videos (Optional)",
            description = "Use the record button to save videos locally."
        ) {
            Text("• Square record button starts recording\n" +
                 "• Round red button indicates active recording\n" +
                 "• Videos saved to configured directory")
        }
        
        // First Time User Tips
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Tips",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "First Time User Tips",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• Start with default settings for quick setup\n" +
                    "• Test streaming on local network first\n" +
                    "• Check available storage before recording\n" +
                    "• Use lower quality for slower devices",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun FeaturesContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "App Features",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        FeatureCard(
            icon = Icons.Default.PlayArrow,
            title = "HTTP Streaming",
            description = "Stream live video over your local network using HTTP protocol."
        ) {
            Text("• Compatible with VLC, browsers, and media players\n" +
                 "• Configurable video quality and bitrate\n" +
                 "• Optional audio streaming\n" +
                 "• Auto IP detection for easy setup")
        }
        
        FeatureCard(
            icon = Icons.Default.PlayArrow,
            title = "Local Recording",
            description = "Record videos locally in MP4 format with H.264/AAC codecs."
        ) {
            Text("• Multiple quality options (720p, 1080p)\n" +
                 "• Configurable file size and duration limits\n" +
                 "• Organized storage in custom directories\n" +
                 "• Real-time storage monitoring")
        }
        
        FeatureCard(
            icon = Icons.Default.Lock,
            title = "Security Features",
            description = "Built with security-first principles and best practices."
        ) {
            Text("• Credentials stored in Android Keystore\n" +
                 "• Input validation and sanitization\n" +
                 "• Local network streaming only\n" +
                 "• No external data transmission")
        }
        
        FeatureCard(
            icon = Icons.Default.Phone,
            title = "Adaptive UI",
            description = "Responsive design that works on phones, tablets, and foldables."
        ) {
            Text("• Phone layout: Compact vertical design\n" +
                 "• Tablet portrait: Larger preview and controls\n" +
                 "• Tablet landscape: Two-pane layout\n" +
                 "• Material 3 design with dynamic colors")
        }
        
        FeatureCard(
            icon = Icons.Default.Settings,
            title = "Advanced Configuration",
            description = "Comprehensive settings for power users."
        ) {
            Text("• Custom server URLs and ports\n" +
                 "• Bitrate and quality controls\n" +
                 "• Authentication options\n" +
                 "• File management settings")
        }
    }
}

@Composable
private fun TroubleshootingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Common Issues & Solutions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        TroubleshootingItem(
            problem = "Camera permission denied",
            solution = "Go to Android Settings > Apps > CatchMeStreaming > Permissions and enable Camera and Microphone."
        )
        
        TroubleshootingItem(
            problem = "Streaming not working",
            solution = "• Check network connection\n" +
                     "• Verify server URL and port\n" +
                     "• Ensure devices are on same network\n" +
                     "• Try default settings first"
        )
        
        TroubleshootingItem(
            problem = "Poor streaming quality",
            solution = "• Reduce video quality in Settings\n" +
                     "• Lower bitrate for slower networks\n" +
                     "• Check Wi-Fi signal strength\n" +
                     "• Close other network-intensive apps"
        )
        
        TroubleshootingItem(
            problem = "Recording fails",
            solution = "• Check available storage space\n" +
                     "• Verify storage permissions\n" +
                     "• Reduce recording quality\n" +
                     "• Clear app cache if needed"
        )
        
        TroubleshootingItem(
            problem = "App crashes or freezes",
            solution = "• Restart the app\n" +
                     "• Clear app cache and data\n" +
                     "• Update to latest version\n" +
                     "• Restart your device"
        )
        
        TroubleshootingItem(
            problem = "Can't connect to stream",
            solution = "• Verify IP address is correct\n" +
                     "• Check firewall settings\n" +
                     "• Try different port (8080, 8081)\n" +
                     "• Use VLC or browser to test connection"
        )
        
        // Advanced Troubleshooting
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Advanced",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Still having issues?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• Check device compatibility (Android 12+)\n" +
                    "• Monitor device temperature\n" +
                    "• Test with different video codecs\n" +
                    "• Reset app to default settings",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun SecurityContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Security & Privacy",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        SecurityCard(
            icon = Icons.Default.Lock,
            title = "Credential Protection",
            description = "Your sensitive information is protected with industry-standard encryption."
        ) {
            Text("• Passwords stored in Android Keystore\n" +
                 "• Hardware-backed security enclave\n" +
                 "• Never stored in plain text\n" +
                 "• Automatic credential encryption")
        }
        
        SecurityCard(
            icon = Icons.Default.Lock,
            title = "Network Security",
            description = "All streaming happens locally on your network."
        ) {
            Text("• No external servers or cloud services\n" +
                 "• Local network streaming only\n" +
                 "• No data collection or analytics\n" +
                 "• Optional authentication for streams")
        }
        
        SecurityCard(
            icon = Icons.Default.Check,
            title = "Input Validation",
            description = "All user inputs are validated and sanitized."
        ) {
            Text("• Prevents injection attacks\n" +
                 "• URL and credential validation\n" +
                 "• Safe file handling\n" +
                 "• Secure logging practices")
        }
        
        SecurityCard(
            icon = Icons.Default.Lock,
            title = "Privacy Protection",
            description = "Your privacy is our priority."
        ) {
            Text("• Camera access only when granted\n" +
                 "• Recordings stored locally only\n" +
                 "• No telemetry or tracking\n" +
                 "• Open source security model")
        }
        
        // Security Best Practices
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Best Practices",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Security Best Practices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• Use strong passwords (8+ chars, mixed case, numbers)\n" +
                    "• Keep app updated to latest version\n" +
                    "• Only stream on trusted networks\n" +
                    "• Regularly review app permissions\n" +
                    "• Use authentication when available",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun FAQContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Frequently Asked Questions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        FAQItem(
            question = "What video formats are supported?",
            answer = "The app streams in HTTP format and records MP4 files with H.264 video and AAC audio codecs."
        )
        
        FAQItem(
            question = "Can I stream to multiple devices?",
            answer = "Yes! The HTTP streaming server can handle multiple concurrent connections from different devices."
        )
        
        FAQItem(
            question = "What's the maximum recording time?",
            answer = "Recording time is configurable up to 10 hours per file, limited by available storage space."
        )
        
        FAQItem(
            question = "Does the app work without internet?",
            answer = "Yes! Streaming works on local networks without internet. Only local Wi-Fi is needed."
        )
        
        FAQItem(
            question = "Which Android versions are supported?",
            answer = "Android 12 (API 31) and higher. The app uses modern Android features for better performance."
        )
        
        FAQItem(
            question = "How do I change the stream URL?",
            answer = "Go to Settings > HTTP Streaming Configuration and update the Server URL field. The app auto-detects your device IP."
        )
        
        FAQItem(
            question = "Can I stream over mobile data?",
            answer = "While technically possible, it's recommended to use Wi-Fi for stability and to avoid data charges."
        )
        
        FAQItem(
            question = "How much storage does recording use?",
            answer = "720p uses ~1GB/hour, 1080p uses ~2GB/hour. Check available storage in the Settings screen."
        )
    }
}

@Composable
private fun HelpCard(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SecurityCard(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TroubleshootingItem(
    problem: String,
    solution: String
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Problem",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    problem,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Solution",
                            tint = Color.Green,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            solution,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FAQItem(
    question: String,
    answer: String
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Question",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HelpScreenPreview() {
    CatchMeStreamingTheme {
        HelpScreen()
    }
}