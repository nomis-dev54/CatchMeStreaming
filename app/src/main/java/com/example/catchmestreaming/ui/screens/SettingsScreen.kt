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
import com.example.catchmestreaming.data.RTSPConfig
import com.example.catchmestreaming.data.StreamQuality
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentConfig: RTSPConfig? = null,
    onSaveConfig: (RTSPConfig) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Initialize from current config or defaults
    val defaultServerUrl = NetworkUtil.getBestIpForStreaming(context)
    
    var serverUrl by remember { mutableStateOf(currentConfig?.serverUrl ?: defaultServerUrl) }
    var port by remember { mutableStateOf(currentConfig?.port?.toString() ?: "554") }
    var streamPath by remember { mutableStateOf(currentConfig?.streamPath ?: "/live") }
    var username by remember { mutableStateOf(currentConfig?.username ?: "admin") }
    var password by remember { mutableStateOf(currentConfig?.password ?: "Password123!") }
    var passwordVisible by remember { mutableStateOf(false) }
    var useAuthentication by remember { mutableStateOf(currentConfig?.useAuthentication ?: true) }
    var enableAudio by remember { mutableStateOf(currentConfig?.enableAudio ?: true) }
    var selectedQuality by remember { mutableStateOf(currentConfig?.quality ?: StreamQuality.MEDIUM) }
    var maxBitrate by remember { mutableStateOf(currentConfig?.maxBitrate?.toString() ?: "2000000") }
    
    // Validation errors
    var serverUrlError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var bitrateError by remember { mutableStateOf<String?>(null) }
    
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
            // RTSP Configuration Section
            SettingsSection(title = "RTSP Configuration") {
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
                        placeholder = { Text("554") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text(portError ?: "Default: 554") },
                        isError = portError != null
                    )
                    
                    OutlinedTextField(
                        value = streamPath,
                        onValueChange = { streamPath = it },
                        label = { Text("Stream Path") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("/live") },
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
                        supportingText = { Text("RTSP authentication username") }
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
                        
                        if (!hasErrors) {
                            // Create and validate RTSPConfig
                            val config = RTSPConfig(
                                serverUrl = if (serverUrl.startsWith("rtsp://")) serverUrl else "rtsp://$serverUrl",
                                username = if (useAuthentication) username else "",
                                password = if (useAuthentication) password else "",
                                port = portInt ?: 554,
                                streamPath = streamPath,
                                quality = selectedQuality,
                                enableAudio = enableAudio,
                                maxBitrate = bitrateInt ?: 2000000,
                                useAuthentication = useAuthentication
                            )
                            
                            onSaveConfig(config)
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