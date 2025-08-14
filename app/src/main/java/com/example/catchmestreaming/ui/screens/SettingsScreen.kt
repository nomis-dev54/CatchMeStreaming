package com.example.catchmestreaming.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.catchmestreaming.ui.theme.CatchMeStreamingTheme
import com.example.catchmestreaming.util.NetworkUtil
import com.example.catchmestreaming.data.StreamConfig
import com.example.catchmestreaming.data.StreamQuality
import com.example.catchmestreaming.data.RecordingConfig
import com.example.catchmestreaming.util.FileManager
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentConfig: StreamConfig? = null,
    currentRecordingConfig: RecordingConfig? = null,
    onSaveConfig: (StreamConfig) -> Unit = {},
    onSaveRecordingConfig: (RecordingConfig) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Initialize from current config or create defaults
    val defaultConfig = currentConfig ?: StreamConfig.createDefault(context)
    
    var serverUrl by remember { mutableStateOf(defaultConfig.serverUrl) }
    var port by remember { mutableStateOf(defaultConfig.port.toString()) }
    var streamPath by remember { mutableStateOf(defaultConfig.streamPath) }
    var username by remember { mutableStateOf(defaultConfig.username) }
    var password by remember { mutableStateOf(defaultConfig.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var useAuthentication by remember { mutableStateOf(defaultConfig.useAuthentication) }
    var enableAudio by remember { mutableStateOf(defaultConfig.enableAudio) }
    var selectedQuality by remember { mutableStateOf(defaultConfig.quality) }
    var maxBitrate by remember { mutableStateOf(defaultConfig.maxBitrate.toString()) }
    
    // Recording configuration state
    var recordingQuality by remember { mutableStateOf(currentRecordingConfig?.quality ?: RecordingConfig.VideoQuality.HD_1080P) }
    var recordingEnableAudio by remember { mutableStateOf(currentRecordingConfig?.enableAudio ?: true) }
    var recordingOutputDirectory by remember { mutableStateOf(currentRecordingConfig?.outputDirectory ?: "Movies/CatchMeStreaming/") }
    var recordingFilenamePrefix by remember { mutableStateOf(currentRecordingConfig?.filenamePrefix ?: "recording") }
    var recordingMaxFileSize by remember { mutableStateOf((currentRecordingConfig?.maxFileSize ?: 2_000_000_000L).toString()) }
    var recordingMaxDuration by remember { mutableStateOf((currentRecordingConfig?.maxDuration ?: 3600).toString()) }
    
    // Storage info
    var storageInfo by remember { mutableStateOf<FileManager.StorageInfo?>(null) }
    
    // Validation errors
    var serverUrlError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var bitrateError by remember { mutableStateOf<String?>(null) }
    var recordingMaxFileSizeError by remember { mutableStateOf<String?>(null) }
    var recordingMaxDurationError by remember { mutableStateOf<String?>(null) }
    
    // Auto-apply default configuration if none provided
    LaunchedEffect(currentConfig) {
        if (currentConfig == null) {
            // Apply default configuration automatically
            val defaultStreamConfig = StreamConfig.createDefault(context)
            val defaultRecordingConfig = currentRecordingConfig ?: RecordingConfig.createDefault()
            onSaveConfig(defaultStreamConfig)
            onSaveRecordingConfig(defaultRecordingConfig)
        }
    }
    
    // Load storage information
    LaunchedEffect(currentRecordingConfig) {
        val config = currentRecordingConfig ?: RecordingConfig.createDefault()
        val fileManager = FileManager(context)
        val result = fileManager.getStorageInfo(config)
        if (result.isSuccess) {
            storageInfo = result.getOrNull()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Default Configuration Notice
            if (currentConfig == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "✅ Default Configuration Applied",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Ready to stream with auto-detected settings. Modify below as needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            // HTTP Streaming Configuration Section
            SettingsSection(title = "HTTP Streaming Configuration") {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { 
                        serverUrl = it
                        serverUrlError = null
                    },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("192.168.1.100 or domain.com") },
                    supportingText = { 
                        Text(serverUrlError ?: "Auto-detected device IP. Edit if needed.")
                    },
                    isError = serverUrlError != null,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val newIp = NetworkUtil.getBestIpForStreaming(context)
                                serverUrl = newIp
                                serverUrlError = null
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh IP"
                            )
                        }
                    }
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { 
                            port = it.filter { char -> char.isDigit() }
                            portError = null
                        },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("8080") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text(portError ?: "Default: 8080") },
                        isError = portError != null
                    )
                    
                    OutlinedTextField(
                        value = streamPath,
                        onValueChange = { streamPath = it },
                        label = { Text("Stream Path") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("/stream") },
                        supportingText = { Text("URL path component") }
                    )
                }
                
                // Authentication Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useAuthentication,
                        onCheckedChange = { useAuthentication = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Authentication")
                }
                
                // Authentication fields (only shown when enabled)
                if (useAuthentication) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter username") },
                        supportingText = { Text("Streaming authentication username (if required)") }
                    )
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            passwordError = null
                        },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter strong password") },
                        visualTransformation = if (passwordVisible) 
                            VisualTransformation.None 
                        else 
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = { 
                            Text(passwordError ?: "Min 8 chars, mixed case, numbers, symbols")
                        },
                        isError = passwordError != null,
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "Show")
                            }
                        }
                    )
                }
            }
            
            // Streaming Configuration Section
            SettingsSection(title = "Streaming Configuration") {
                var qualityExpanded by remember { mutableStateOf(false) }
                val qualityOptions = StreamQuality.values()
                
                ExposedDropdownMenuBox(
                    expanded = qualityExpanded,
                    onExpandedChange = { qualityExpanded = !qualityExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedQuality.displayName,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Stream Quality") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        supportingText = { 
                            Text("${selectedQuality.width}x${selectedQuality.height} @ ${selectedQuality.fps}fps")
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = qualityExpanded,
                        onDismissRequest = { qualityExpanded = false }
                    ) {
                        qualityOptions.forEach { quality ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(quality.displayName)
                                        Text(
                                            "${quality.width}x${quality.height} @ ${quality.fps}fps",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedQuality = quality
                                    // Update bitrate based on quality
                                    maxBitrate = quality.bitrate.toString()
                                    qualityExpanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = maxBitrate,
                    onValueChange = { 
                        maxBitrate = it.filter { char -> char.isDigit() }
                        bitrateError = null
                    },
                    label = { Text("Max Bitrate (bps)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("2000000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { 
                        val mbps = maxBitrate.toIntOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                        Text(bitrateError ?: "≈ %.1f Mbps".format(mbps))
                    },
                    isError = bitrateError != null
                )
                
                // Audio Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = enableAudio,
                        onCheckedChange = { enableAudio = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Audio")
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Streaming Information",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "• Protocol: HTTP streaming\n" +
                            "• Video: H.264 encoding\n" +
                            "• Audio: AAC encoding (optional)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Recording Configuration Section
            SettingsSection(title = "Recording Configuration") {
                var recordingQualityExpanded by remember { mutableStateOf(false) }
                val recordingQualityOptions = RecordingConfig.VideoQuality.values()
                
                ExposedDropdownMenuBox(
                    expanded = recordingQualityExpanded,
                    onExpandedChange = { recordingQualityExpanded = !recordingQualityExpanded }
                ) {
                    OutlinedTextField(
                        value = recordingQuality.displayName,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Recording Quality") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recordingQualityExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        supportingText = { 
                            Text("${recordingQuality.width}x${recordingQuality.height} @ ${recordingQuality.frameRate}fps")
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = recordingQualityExpanded,
                        onDismissRequest = { recordingQualityExpanded = false }
                    ) {
                        recordingQualityOptions.forEach { quality ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(quality.displayName)
                                        Text(
                                            "${quality.width}x${quality.height} @ ${quality.frameRate}fps",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    recordingQuality = quality
                                    recordingQualityExpanded = false
                                }
                            )
                        }
                    }
                }
                
                // Recording Audio Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = recordingEnableAudio,
                        onCheckedChange = { recordingEnableAudio = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Audio Recording")
                }
                
                OutlinedTextField(
                    value = recordingOutputDirectory,
                    onValueChange = { recordingOutputDirectory = it },
                    label = { Text("Output Directory") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Movies/CatchMeStreaming/") },
                    supportingText = { Text("Relative to app's external files directory") }
                )
                
                OutlinedTextField(
                    value = recordingFilenamePrefix,
                    onValueChange = { recordingFilenamePrefix = it },
                    label = { Text("Filename Prefix") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("recording") },
                    supportingText = { Text("Prefix for generated filenames") }
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = recordingMaxFileSize,
                        onValueChange = { 
                            recordingMaxFileSize = it.filter { char -> char.isDigit() }
                            recordingMaxFileSizeError = null
                        },
                        label = { Text("Max File Size (bytes)") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("2000000000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { 
                            val sizeInGb = recordingMaxFileSize.toLongOrNull()?.let { 
                                FileManager.formatFileSize(it) 
                            } ?: "0 B"
                            Text(recordingMaxFileSizeError ?: sizeInGb)
                        },
                        isError = recordingMaxFileSizeError != null
                    )
                    
                    OutlinedTextField(
                        value = recordingMaxDuration,
                        onValueChange = { 
                            recordingMaxDuration = it.filter { char -> char.isDigit() }
                            recordingMaxDurationError = null
                        },
                        label = { Text("Max Duration (sec)") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("3600") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { 
                            val durationText = recordingMaxDuration.toIntOrNull()?.let { seconds ->
                                val hours = seconds / 3600
                                val minutes = (seconds % 3600) / 60
                                val secs = seconds % 60
                                when {
                                    hours > 0 -> "${hours}h ${minutes}m"
                                    minutes > 0 -> "${minutes}m ${secs}s"
                                    else -> "${secs}s"
                                }
                            } ?: "0s"
                            Text(recordingMaxDurationError ?: durationText)
                        },
                        isError = recordingMaxDurationError != null
                    )
                }
                
                // Storage Information Card
                storageInfo?.let { storage ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "Storage Information",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "• Available Space: ${storage.freeSpaceFormatted}\n" +
                                "• Total Space: ${storage.totalSpaceFormatted}\n" +
                                "• Recordings Size: ${storage.recordingsSizeFormatted}\n" +
                                "• Free Space: ${String.format("%.1f", storage.freeSpacePercentage)}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } ?: run {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "Recording Information",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "• Format: MP4 (H.264 video + AAC audio)\n" +
                                "• Storage: /Movies/CatchMeStreaming/\n" +
                                "• Quality: Configurable resolution",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            // Security & Privacy Section
            SettingsSection(title = "Security & Privacy") {
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
                            "🔒 Security Features",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "• Credentials stored in Android Keystore\n" +
                            "• Local network streaming only\n" +
                            "• No data collection or external transmission",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { 
                        // Validate and save configuration
                        var hasErrors = false
                        
                        // Reset all errors
                        serverUrlError = null
                        portError = null
                        passwordError = null
                        bitrateError = null
                        recordingMaxFileSizeError = null
                        recordingMaxDurationError = null
                        
                        // Validate port
                        val portInt = port.toIntOrNull()
                        if (portInt == null || portInt !in 1..65535) {
                            portError = "Port must be between 1 and 65535"
                            hasErrors = true
                        }
                        
                        // Validate bitrate
                        val bitrateInt = maxBitrate.toIntOrNull()
                        if (bitrateInt == null || bitrateInt < 100000 || bitrateInt > 10000000) {
                            bitrateError = "Bitrate must be between 100 Kbps and 10 Mbps"
                            hasErrors = true
                        }
                        
                        // Validate server URL
                        if (serverUrl.isBlank()) {
                            serverUrlError = "Server URL is required"
                            hasErrors = true
                        }
                        
                        // Validate authentication fields
                        if (useAuthentication) {
                            if (username.isBlank()) {
                                hasErrors = true
                            }
                            if (password.length < 8) {
                                passwordError = "Password must be at least 8 characters"
                                hasErrors = true
                            }
                        }
                        
                        // Validate recording configuration
                        val recordingMaxFileSizeInt = recordingMaxFileSize.toLongOrNull()
                        if (recordingMaxFileSizeInt == null || recordingMaxFileSizeInt < 1_000_000L || recordingMaxFileSizeInt > 10_000_000_000L) {
                            recordingMaxFileSizeError = "File size must be between 1MB and 10GB"
                            hasErrors = true
                        }
                        
                        val recordingMaxDurationInt = recordingMaxDuration.toIntOrNull()
                        if (recordingMaxDurationInt == null || recordingMaxDurationInt < 60 || recordingMaxDurationInt > 36000) {
                            recordingMaxDurationError = "Duration must be between 1 minute and 10 hours"
                            hasErrors = true
                        }
                        
                        if (!hasErrors) {
                            // Create and validate StreamConfig
                            val streamConfig = StreamConfig(
                                serverUrl = if (serverUrl.startsWith("http://")) serverUrl else serverUrl,
                                username = if (useAuthentication) username else "",
                                password = if (useAuthentication) password else "",
                                port = portInt ?: 8080,
                                streamPath = streamPath,
                                quality = selectedQuality,
                                enableAudio = enableAudio,
                                maxBitrate = bitrateInt ?: 2000000,
                                useAuthentication = useAuthentication
                            )
                            
                            // Create and validate RecordingConfig
                            val recordingConfig = RecordingConfig(
                                quality = recordingQuality,
                                outputDirectory = recordingOutputDirectory,
                                enableAudio = recordingEnableAudio,
                                videoCodec = RecordingConfig.VideoCodec.H264,
                                audioCodec = RecordingConfig.AudioCodec.AAC,
                                maxFileSize = recordingMaxFileSizeInt ?: 2_000_000_000L,
                                maxDuration = recordingMaxDurationInt ?: 3600,
                                filenamePrefix = recordingFilenamePrefix.ifBlank { "recording" }
                            )
                            
                            onSaveConfig(streamConfig)
                            onSaveRecordingConfig(recordingConfig)
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Settings")
                }
                
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    CatchMeStreamingTheme {
        SettingsScreen()
    }
}