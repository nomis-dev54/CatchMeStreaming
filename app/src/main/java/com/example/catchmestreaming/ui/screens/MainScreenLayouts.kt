package com.example.catchmestreaming.ui.screens

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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.example.catchmestreaming.viewmodel.MainViewModel
import com.example.catchmestreaming.viewmodel.MainUiState
import com.example.catchmestreaming.data.StreamState

@Composable
fun TabletExpandedLayout(
    uiState: MainUiState,
    viewModel: MainViewModel,
    lifecycleOwner: LifecycleOwner,
    paddingValues: PaddingValues
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left pane: Camera preview (60% width)
        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(
                    Color.Black,
                    shape = MaterialTheme.shapes.large
                ),
            contentAlignment = Alignment.Center
        ) {
            CameraPreviewContent(
                uiState = uiState,
                viewModel = viewModel,
                lifecycleOwner = lifecycleOwner
            )
        }
        
        // Right pane: Status and controls (40% width)
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Status card
            StatusCardContent(
                uiState = uiState,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Control buttons
            ControlButtonsContent(
                uiState = uiState,
                viewModel = viewModel,
                isTabletLayout = true
            )
            
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun TabletMediumLayout(
    uiState: MainUiState,
    viewModel: MainViewModel,
    lifecycleOwner: LifecycleOwner,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Camera Preview - larger on medium screens
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(
                    Color.Black,
                    shape = MaterialTheme.shapes.large
                ),
            contentAlignment = Alignment.Center
        ) {
            CameraPreviewContent(
                uiState = uiState,
                viewModel = viewModel,
                lifecycleOwner = lifecycleOwner
            )
        }
        
        // Status and controls in separate sections
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Status card
            StatusCardContent(
                uiState = uiState,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Control buttons
        ControlButtonsContent(
            uiState = uiState,
            viewModel = viewModel,
            isTabletLayout = true
        )
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun CameraPreviewContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    lifecycleOwner: LifecycleOwner
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Camera",
                tint = Color.White,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!uiState.hasCameraPermission) {
                Text(
                    "Camera Permission Required",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.requestPermissions() }
                ) {
                    Text("Grant Camera Permission")
                }
            } else if (!uiState.isCameraInitialized) {
                Text(
                    "Initializing Camera...",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
            } else {
                Text(
                    "Camera Preview",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

@Composable
internal fun StatusCardContent(
    uiState: MainUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
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
            Spacer(modifier = Modifier.height(12.dp))
            
            // Status indicators
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stream state information
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
            
            // Show recording info
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
            
            // Configuration warnings
            if (uiState.streamConfig == null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "⚠️ Streaming not configured - Go to Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
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
}

@Composable
internal fun ControlButtonsContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    isTabletLayout: Boolean = false
) {
    val buttonSize = if (isTabletLayout) 84.dp else 72.dp
    val iconSize = if (isTabletLayout) 40.dp else 32.dp
    val spacing = if (isTabletLayout) 24.dp else 32.dp
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing),
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
            modifier = Modifier.size(buttonSize)
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
                modifier = Modifier.size(iconSize)
            )
        }
        
        // Record Button
        FloatingActionButton(
            onClick = { viewModel.toggleRecording() },
            containerColor = if (uiState.isRecording) 
                MaterialTheme.colorScheme.error 
            else 
                MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(buttonSize)
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .background(
                        Color.White,
                        if (uiState.isRecording) CircleShape else MaterialTheme.shapes.small
                    )
            )
        }
    }
}

@Composable
internal fun StatusItem(
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