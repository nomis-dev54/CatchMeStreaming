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
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import com.example.catchmestreaming.viewmodel.MainUiState
import com.example.catchmestreaming.data.StreamState
import com.example.catchmestreaming.data.RecordingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    viewModel: MainViewModel = viewModel(),
    windowSizeClass: WindowSizeClass? = null
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
    
    // Determine layout based on window size
    val isCompact = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Compact
    val isMedium = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Medium
    val isExpanded = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
    
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
                    IconButton(onClick = onNavigateToHelp) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Help"
                        )
                    }
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
        when {
            isExpanded -> {
                // Large screens (tablets in landscape): Two-pane layout
                TabletExpandedLayout(
                    uiState = uiState,
                    viewModel = viewModel,
                    lifecycleOwner = lifecycleOwner,
                    paddingValues = paddingValues
                )
            }
            isMedium -> {
                // Medium screens (tablets in portrait, large phones): Single column with more spacing
                TabletMediumLayout(
                    uiState = uiState,
                    viewModel = viewModel,
                    lifecycleOwner = lifecycleOwner,
                    paddingValues = paddingValues
                )
            }
            else -> {
                // Compact screens (phones): Original layout with optimizations
                PhoneCompactLayout(
                    uiState = uiState,
                    viewModel = viewModel,
                    lifecycleOwner = lifecycleOwner,
                    paddingValues = paddingValues
                )
            }
        }
    }
}

@Composable
private fun PhoneCompactLayout(
    uiState: MainUiState,
    viewModel: MainViewModel,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    paddingValues: PaddingValues
) {
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
        StatusCardContent(
            uiState = uiState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Control Buttons
        ControlButtonsContent(
            uiState = uiState,
            viewModel = viewModel,
            isTabletLayout = false
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    CatchMeStreamingTheme {
        MainScreen()
    }
}