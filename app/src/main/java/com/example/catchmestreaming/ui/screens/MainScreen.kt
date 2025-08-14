package com.example.catchmestreaming.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.catchmestreaming.ui.theme.CatchMeStreamingTheme
import com.example.catchmestreaming.viewmodel.MainViewModel
import com.example.catchmestreaming.data.StreamState
import com.example.catchmestreaming.data.RecordingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (permission, granted) ->
            viewModel.onPermissionResult(permission, granted)
        }
    }
    
    // Listen for permission requests
    LaunchedEffect(Unit) {
        viewModel.permissionRequests.collect { permissions ->
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
    
    // Show error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Clear error after showing it
            viewModel.clearError()
        }
    }
    
    // Handle stream errors separately
    if (uiState.streamState.isError) {
        val streamError = uiState.streamState as StreamState.Error
        LaunchedEffect(streamError) {
            // Auto-clear stream errors after delay
            kotlinx.coroutines.delay(5000)
            viewModel.clearStreamError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "CatchMeStreaming",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .background(
                        Color.Black,
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.hasCameraPermission && uiState.isCameraInitialized) {
                    // Real camera preview
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                viewModel.startCameraPreview(lifecycleOwner, surfaceProvider)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Placeholder content
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Camera",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (!uiState.hasCameraPermission) {
                            Text(
                                "Camera Permission Required",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.requestPermissions() }
                            ) {
                                Text("Grant Camera Permission")
                            }
                        } else if (!uiState.isCameraInitialized) {
                            Text(
                                "Initializing Camera...",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            Text(
                                "Camera Preview",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            
            // Status Information
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusItem(
                            label = "Camera",
                            isActive = uiState.isCameraPreviewStarted,
                            color = if (uiState.isCameraPreviewStarted) Color.Green else Color.Gray
                        )
                        StatusItem(
                            label = "Streaming",
                            isActive = uiState.isStreaming,
                            color = when {
                                uiState.streamState.isStreaming -> Color.Green
                                uiState.streamState.isPreparing -> MaterialTheme.colorScheme.primary
                                uiState.streamState.isError -> Color.Red
                                else -> Color.Gray
                            }
                        )
                        StatusItem(
                            label = "Recording",
                            isActive = uiState.isRecording,
                            color = if (uiState.isRecording) Color.Red else Color.Gray
                        )
                    }
                    
                    // Stream state information
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Stream: ${uiState.streamState.getDisplayMessage()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            uiState.streamState.isError -> MaterialTheme.colorScheme.error
                            uiState.streamState.isStreaming -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    uiState.streamingDuration?.let { duration ->
                        Text(
                            "Duration: $duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    uiState.streamUrl?.let { url ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Stream URL: $url",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Show recording duration and file info
                    uiState.recordingDuration?.let { duration ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Recording: $duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    uiState.recordingFileSize?.let { fileSize ->
                        Text(
                            "File Size: $fileSize",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    uiState.availableStorage?.let { storage ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Available: $storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Show streaming config status
                    if (uiState.streamConfig == null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "⚠️ Streaming not configured - Go to Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // Show recording config status
                    if (uiState.recordingConfig == null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "⚠️ Recording not configured - Go to Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    uiState.error?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Error: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Stream Button
                FloatingActionButton(
                    onClick = { 
                        if (uiState.streamState.canStart) {
                            viewModel.startStreaming()
                        } else if (uiState.streamState.canStop) {
                            viewModel.stopStreaming()
                        }
                    },
                    containerColor = when {
                        uiState.streamState.isStreaming -> MaterialTheme.colorScheme.error
                        uiState.streamState.isPreparing -> MaterialTheme.colorScheme.secondary
                        uiState.streamState.canStart -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    val icon = when {
                        uiState.streamState.isStreaming -> Icons.Default.Close
                        uiState.streamState.isPreparing -> Icons.Default.Refresh
                        else -> Icons.Default.PlayArrow
                    }
                    
                    Icon(
                        icon,
                        contentDescription = when {
                            uiState.streamState.isStreaming -> "Stop Stream"
                            uiState.streamState.isPreparing -> "Starting..."
                            else -> "Start Stream"
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Record Button
                FloatingActionButton(
                    onClick = { viewModel.toggleRecording() },
                    containerColor = if (uiState.isRecording) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                Color.White,
                                if (uiState.isRecording) CircleShape else MaterialTheme.shapes.small
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusItem(
    label: String,
    isActive: Boolean,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    CatchMeStreamingTheme {
        MainScreen()
    }
}