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
import com.example.catchmestreaming.repository.StreamRepository
import com.example.catchmestreaming.data.RTSPConfig
import com.example.catchmestreaming.data.StreamState
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
    val canSwitchCamera: Boolean = false,
    val streamState: StreamState = StreamState.Idle,
    val rtspConfig: RTSPConfig? = null,
    val streamingDuration: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val cameraRepository = CameraRepository(application)
    private val streamRepository = StreamRepository(application)
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    private val _permissionRequests = MutableSharedFlow<List<String>>()
    val permissionRequests: SharedFlow<List<String>> = _permissionRequests.asSharedFlow()
    
    init {
        checkPermissions()
        observeCameraState()
        observeStreamState()
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
    
    private fun observeStreamState() {
        viewModelScope.launch {
            streamRepository.streamState.collect { streamState ->
                val isStreaming = streamState.isStreaming
                val rtspUrl = if (streamState is StreamState.Streaming) {
                    streamState.rtspUrl
                } else null
                
                val duration = if (streamState is StreamState.Streaming) {
                    streamState.getFormattedDuration()
                } else null
                
                _uiState.value = _uiState.value.copy(
                    streamState = streamState,
                    isStreaming = isStreaming,
                    rtspUrl = rtspUrl,
                    streamingDuration = duration,
                    rtspConfig = streamRepository.getCurrentConfig()
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
    
    fun startStreaming() {
        if (!_uiState.value.hasCameraPermission) {
            requestPermissions()
            return
        }
        
        viewModelScope.launch {
            val result = streamRepository.startStreaming()
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown streaming error"
                _uiState.value = _uiState.value.copy(error = error)
            }
        }
    }
    
    fun stopStreaming() {
        viewModelScope.launch {
            val result = streamRepository.stopStreaming()
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown error stopping stream"
                _uiState.value = _uiState.value.copy(error = error)
            }
        }
    }
    
    fun updateRTSPConfig(config: RTSPConfig) {
        viewModelScope.launch {
            val result = streamRepository.updateConfiguration(config)
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Configuration update failed"
                _uiState.value = _uiState.value.copy(error = error)
            }
        }
    }
    
    fun clearStreamError() {
        viewModelScope.launch {
            streamRepository.clearError()
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
        streamRepository.cleanup()
    }
}