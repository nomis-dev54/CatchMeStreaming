package com.example.catchmestreaming.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.catchmestreaming.repository.CameraRepository
import com.example.catchmestreaming.repository.StreamRepository
import com.example.catchmestreaming.repository.RecordingRepository
import com.example.catchmestreaming.integration.MediaRecorderCameraXIntegration
import com.example.catchmestreaming.data.StreamConfig
import com.example.catchmestreaming.data.StreamState
import com.example.catchmestreaming.data.RecordingConfig
import com.example.catchmestreaming.data.RecordingState
import com.example.catchmestreaming.util.FileManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val isStreaming: Boolean = false,
    val isRecording: Boolean = false,
    val isCameraInitialized: Boolean = false,
    val isCameraPreviewStarted: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val hasMediaVideoPermission: Boolean = false,
    val hasMediaAudioPermission: Boolean = false,
    val error: String? = null,
    val streamUrl: String? = null,
    val rtspUrl: String? = null, // Legacy property for backward compatibility
    val canSwitchCamera: Boolean = false,
    val streamState: StreamState = StreamState.Idle,
    val streamConfig: StreamConfig? = null,
    val streamingDuration: String? = null,
    // Recording-related state
    val recordingState: RecordingState = RecordingState.Idle,
    val recordingConfig: RecordingConfig? = null,
    val recordingDuration: String? = null,
    val recordingFilePath: String? = null,
    val recordingFileSize: String? = null,
    val availableStorage: String? = null,
    val recordingsList: List<FileManager.RecordingFileInfo> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val cameraRepository = CameraRepository(application)
    private val streamRepository = StreamRepository(application)
    private val recordingRepository = RecordingRepository(application)
    private val fileManager = FileManager(application)
    
    // MediaRecorder + CameraX integration layer
    private val recordingIntegration = MediaRecorderCameraXIntegration(
        recordingRepository, 
        cameraRepository
    )
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    private val _permissionRequests = MutableSharedFlow<List<String>>()
    val permissionRequests: SharedFlow<List<String>> = _permissionRequests.asSharedFlow()
    
    init {
        checkPermissions()
        observeCameraState()
        observeStreamState()
        observeRecordingState()
        loadInitialData()
    }
    
    private fun checkPermissions() {
        val context = getApplication<Application>()
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        // Check storage permissions based on API level
        val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: Check for granular media permissions
            true // We use scoped storage, no broad storage permission needed
        } else {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        val hasMediaVideoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasStoragePermission // On older APIs, covered by storage permission
        }
        
        val hasMediaAudioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasStoragePermission // On older APIs, covered by storage permission
        }
        
        _uiState.value = _uiState.value.copy(
            hasCameraPermission = hasCameraPermission,
            hasAudioPermission = hasAudioPermission,
            hasStoragePermission = hasStoragePermission,
            hasMediaVideoPermission = hasMediaVideoPermission,
            hasMediaAudioPermission = hasMediaAudioPermission
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
                val streamUrl = if (streamState is StreamState.Streaming) {
                    streamState.streamUrl
                } else null
                
                val duration = if (streamState is StreamState.Streaming) {
                    streamState.getFormattedDuration()
                } else null
                
                _uiState.value = _uiState.value.copy(
                    streamState = streamState,
                    isStreaming = isStreaming,
                    streamUrl = streamUrl,
                    streamingDuration = duration,
                    streamConfig = streamRepository.getCurrentConfig()
                )
            }
        }
    }
    
    private fun observeRecordingState() {
        viewModelScope.launch {
            recordingRepository.recordingState.collect { recordingState ->
                val isRecording = recordingState.isRecording
                val recordingFilePath = when (recordingState) {
                    is RecordingState.Recording -> recordingState.filePath
                    is RecordingState.Paused -> recordingState.filePath
                    is RecordingState.Stopped -> recordingState.filePath
                    else -> null
                }
                
                val recordingDuration = recordingState.getFormattedDuration()
                val recordingFileSize = recordingState.getFormattedFileSize()
                
                _uiState.value = _uiState.value.copy(
                    recordingState = recordingState,
                    isRecording = isRecording,
                    recordingFilePath = recordingFilePath,
                    recordingDuration = recordingDuration,
                    recordingFileSize = recordingFileSize,
                    recordingConfig = recordingRepository.getCurrentConfig()
                )
            }
        }
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            // Load storage information
            updateStorageInfo()
            
            // Load recordings list
            refreshRecordingsList()
            
            // Load configurations into UI state
            _uiState.value = _uiState.value.copy(
                streamConfig = streamRepository.getCurrentConfig(),
                recordingConfig = recordingRepository.getCurrentConfig()
            )
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
        
        // Request appropriate permissions based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: Request granular media permissions
            if (!_uiState.value.hasMediaVideoPermission) {
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (!_uiState.value.hasMediaAudioPermission) {
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // Legacy: Request broad storage permission
            if (!_uiState.value.hasStoragePermission) {
                permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
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
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE -> {
                _uiState.value = _uiState.value.copy(hasStoragePermission = granted)
            }
            android.Manifest.permission.READ_MEDIA_VIDEO -> {
                _uiState.value = _uiState.value.copy(hasMediaVideoPermission = granted)
            }
            android.Manifest.permission.READ_MEDIA_AUDIO -> {
                _uiState.value = _uiState.value.copy(hasMediaAudioPermission = granted)
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
            // Ensure camera is initialized and preview is started
            if (!_uiState.value.isCameraInitialized) {
                val initResult = cameraRepository.initializeCamera()
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()?.message ?: "Failed to initialize camera"
                    _uiState.value = _uiState.value.copy(error = error)
                    return@launch
                }
            }
            
            // Start the HTTP streaming server
            val streamResult = streamRepository.startStreaming()
            if (streamResult.isFailure) {
                val error = streamResult.exceptionOrNull()?.message ?: "Unknown streaming error"
                _uiState.value = _uiState.value.copy(error = error)
                return@launch
            }
            
            // Enable live streaming from camera to HTTP server
            val liveStreamResult = cameraRepository.enableLiveStreaming { jpegBytes ->
                streamRepository.updateLiveFrame(jpegBytes)
            }
            
            if (liveStreamResult.isFailure) {
                val error = liveStreamResult.exceptionOrNull()?.message ?: "Failed to enable live streaming"
                _uiState.value = _uiState.value.copy(error = error)
            }
        }
    }
    
    fun stopStreaming() {
        viewModelScope.launch {
            // Disable live streaming from camera first
            cameraRepository.disableLiveStreaming()
            
            // Stop the HTTP streaming server
            val result = streamRepository.stopStreaming()
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown error stopping stream"
                _uiState.value = _uiState.value.copy(error = error)
            }
        }
    }
    
    fun updateStreamConfig(config: StreamConfig) {
        viewModelScope.launch {
            val result = streamRepository.updateConfiguration(config)
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Configuration update failed"
                _uiState.value = _uiState.value.copy(error = error)
            } else {
                // Configuration updated successfully, refresh UI state
                _uiState.value = _uiState.value.copy(
                    streamConfig = streamRepository.getCurrentConfig()
                )
            }
        }
    }
    
    fun clearStreamError() {
        viewModelScope.launch {
            streamRepository.clearError()
        }
    }
    
    // Recording methods
    fun startRecording() {
        val hasRequiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            _uiState.value.hasCameraPermission && 
            _uiState.value.hasAudioPermission && 
            _uiState.value.hasMediaVideoPermission && 
            _uiState.value.hasMediaAudioPermission
        } else {
            _uiState.value.hasCameraPermission && 
            _uiState.value.hasAudioPermission && 
            _uiState.value.hasStoragePermission
        }
        
        if (!hasRequiredPermissions) {
            requestPermissions()
            return
        }
        
        viewModelScope.launch {
            // First setup the MediaRecorder + CameraX integration
            val setupResult = recordingIntegration.setupRecordingIntegration()
            if (setupResult.isFailure) {
                val error = setupResult.exceptionOrNull()?.message ?: "Failed to setup recording integration"
                _uiState.value = _uiState.value.copy(error = error)
                return@launch
            }
            
            // Then start recording using the integration layer
            val result = recordingIntegration.startRecording()
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown recording error"
                _uiState.value = _uiState.value.copy(error = error)
            } else {
                // Recording started successfully, refresh storage info
                updateStorageInfo()
            }
        }
    }
    
    fun stopRecording() {
        viewModelScope.launch {
            // Use integration layer to stop recording
            val result = recordingIntegration.stopRecording()
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown error stopping recording"
                _uiState.value = _uiState.value.copy(error = error)
            } else {
                // Recording stopped successfully, refresh data
                updateStorageInfo()
                refreshRecordingsList()
            }
        }
    }
    
    fun pauseRecording() {
        viewModelScope.launch {
            val result = recordingRepository.pauseRecording()
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown error pausing recording"
                _uiState.value = _uiState.value.copy(error = error)
            }
        }
    }
    
    fun resumeRecording() {
        viewModelScope.launch {
            val result = recordingRepository.resumeRecording()
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown error resuming recording"
                _uiState.value = _uiState.value.copy(error = error)
            }
        }
    }
    
    fun toggleRecording() {
        val currentState = _uiState.value.recordingState
        if (currentState.canStart) {
            startRecording()
        } else if (currentState.canStop) {
            stopRecording()
        }
    }
    
    fun updateRecordingConfig(config: RecordingConfig) {
        viewModelScope.launch {
            val result = recordingRepository.updateConfiguration(config)
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Configuration update failed"
                _uiState.value = _uiState.value.copy(error = error)
            } else {
                // Configuration updated successfully, refresh storage info and UI state
                updateStorageInfo()
                _uiState.value = _uiState.value.copy(
                    recordingConfig = recordingRepository.getCurrentConfig()
                )
            }
        }
    }
    
    fun deleteRecording(filePath: String) {
        viewModelScope.launch {
            val config = _uiState.value.recordingConfig ?: RecordingConfig.createDefault()
            val result = fileManager.deleteRecording(filePath, config)
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Failed to delete recording"
                _uiState.value = _uiState.value.copy(error = error)
            } else {
                // Deletion successful, refresh data
                updateStorageInfo()
                refreshRecordingsList()
            }
        }
    }
    
    fun refreshRecordingsList() {
        viewModelScope.launch {
            val config = _uiState.value.recordingConfig ?: RecordingConfig.createDefault()
            val result = fileManager.getAllRecordings(config)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    recordingsList = result.getOrNull() ?: emptyList()
                )
            }
        }
    }
    
    private fun updateStorageInfo() {
        viewModelScope.launch {
            val config = _uiState.value.recordingConfig ?: RecordingConfig.createDefault()
            val result = fileManager.getStorageInfo(config)
            if (result.isSuccess) {
                val storageInfo = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    availableStorage = storageInfo?.freeSpaceFormatted
                )
            }
        }
    }
    
    fun clearRecordingError() {
        viewModelScope.launch {
            recordingRepository.clearError()
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    override fun onCleared() {
        super.onCleared()
        cameraRepository.release()
        streamRepository.cleanup()
        recordingRepository.cleanup()
        recordingIntegration.cleanup()
    }
}