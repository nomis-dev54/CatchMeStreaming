package com.example.catchmestreaming.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.catchmestreaming.repository.CameraRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val isStreaming: Boolean = false,
    val isRecording: Boolean = false,
    val isCameraInitialized: Boolean = false,
    val isCameraPreviewStarted: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val error: String? = null,
    val rtspUrl: String? = null,
    val canSwitchCamera: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val cameraRepository = CameraRepository(application)
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    private val _permissionRequests = MutableSharedFlow<List<String>>()
    val permissionRequests: SharedFlow<List<String>> = _permissionRequests.asSharedFlow()
    
    init {
        checkPermissions()
        observeCameraState()
    }
    
    private fun checkPermissions() {
        val context = getApplication<Application>()
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        _uiState.value = _uiState.value.copy(
            hasCameraPermission = hasCameraPermission,
            hasAudioPermission = hasAudioPermission
        )
        
        // Initialize camera if we have permissions
        if (hasCameraPermission) {
            initializeCamera()
        }
    }
    
    private fun observeCameraState() {
        viewModelScope.launch {
            cameraRepository.cameraState.collect { cameraState ->
                _uiState.value = _uiState.value.copy(
                    isCameraInitialized = cameraState.isInitialized,
                    isCameraPreviewStarted = cameraState.isPreviewStarted,
                    error = cameraState.error,
                    canSwitchCamera = cameraState.availableCameras.size > 1
                )
            }
        }
    }
    
    fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (!_uiState.value.hasCameraPermission) {
            permissionsToRequest.add(android.Manifest.permission.CAMERA)
        }
        
        if (!_uiState.value.hasAudioPermission) {
            permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            viewModelScope.launch {
                _permissionRequests.emit(permissionsToRequest)
            }
        }
    }
    
    fun onPermissionResult(permission: String, granted: Boolean) {
        when (permission) {
            android.Manifest.permission.CAMERA -> {
                _uiState.value = _uiState.value.copy(hasCameraPermission = granted)
                if (granted) {
                    initializeCamera()
                }
            }
            android.Manifest.permission.RECORD_AUDIO -> {
                _uiState.value = _uiState.value.copy(hasAudioPermission = granted)
            }
        }
    }
    
    private fun initializeCamera() {
        viewModelScope.launch {
            val result = cameraRepository.initializeCamera()
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to initialize camera: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }
    
    fun startCameraPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        if (!_uiState.value.hasCameraPermission) {
            requestPermissions()
            return
        }
        
        viewModelScope.launch {
            val result = cameraRepository.startPreview(lifecycleOwner, surfaceProvider)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to start camera preview: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }
    
    fun stopCameraPreview() {
        viewModelScope.launch {
            cameraRepository.stopPreview()
        }
    }
    
    fun switchCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        if (!_uiState.value.canSwitchCamera) return
        
        viewModelScope.launch {
            // Stop current preview
            cameraRepository.stopPreview()
            
            // Switch camera
            val switchResult = cameraRepository.switchCamera()
            if (switchResult.isSuccess) {
                // Restart preview with new camera
                val startResult = cameraRepository.startPreview(lifecycleOwner, surfaceProvider)
                if (startResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to restart preview with new camera: ${startResult.exceptionOrNull()?.message}"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to switch camera: ${switchResult.exceptionOrNull()?.message}"
                )
            }
        }
    }
    
    fun toggleStreaming() {
        val newStreamingState = !_uiState.value.isStreaming
        _uiState.value = _uiState.value.copy(
            isStreaming = newStreamingState,
            rtspUrl = if (newStreamingState) "rtsp://192.168.1.100:8554/stream" else null
        )
        
        // TODO: Implement actual RTSP streaming logic
        if (newStreamingState) {
            // Start streaming
        } else {
            // Stop streaming
        }
    }
    
    fun toggleRecording() {
        val newRecordingState = !_uiState.value.isRecording
        _uiState.value = _uiState.value.copy(isRecording = newRecordingState)
        
        // TODO: Implement actual recording logic
        if (newRecordingState) {
            // Start recording
        } else {
            // Stop recording
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    override fun onCleared() {
        super.onCleared()
        cameraRepository.release()
    }
}