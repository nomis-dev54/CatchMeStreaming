package com.example.catchmestreaming.repository

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.view.Surface
import androidx.core.content.ContextCompat
import com.example.catchmestreaming.data.RecordingConfig
import com.example.catchmestreaming.data.RecordingState
import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.SecureStorage
import com.example.catchmestreaming.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for managing video recording operations with MediaRecorder.
 * Implements security-first design with comprehensive validation and error handling.
 */
class RecordingRepository(
    private val context: Context,
    private val inputValidator: InputValidator = InputValidator(),
    private val secureStorage: SecureStorage = SecureStorage(context)
) {
    
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private var currentConfig: RecordingConfig? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var recordingStartTime: Long = 0L
    
    // Recording info tracking
    data class RecordingInfo(
        val fileName: String,
        val filePath: String,
        val duration: Long,
        val fileSize: Long,
        val createdAt: Long,
        val quality: RecordingConfig.VideoQuality
    )
    
    companion object {
        private const val TAG = "RecordingRepository"
        private const val DEFAULT_AUDIO_SAMPLE_RATE = 44100
        private const val DEFAULT_AUDIO_BITRATE = 128000
        private const val RECORDING_UPDATE_INTERVAL = 1000L // 1 second
        private val REQUIRED_PERMISSIONS_LEGACY = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        private val REQUIRED_PERMISSIONS_API_33_PLUS = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_AUDIO
        )
    }
    
    /**
     * Update recording configuration with validation
     */
    suspend fun updateConfiguration(config: RecordingConfig): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d(TAG, "Updating recording configuration")
                
                // Validate configuration
                val validation = config.validate(inputValidator)
                if (!validation.isValid) {
                    Logger.e(TAG, "Invalid configuration: ${validation.errorMessage}")
                    return@withContext Result.failure(
                        SecurityException("Configuration validation failed: ${validation.errorMessage}")
                    )
                }
                
                // Check API compatibility
                if (!config.isCompatibleWithApi(Build.VERSION.SDK_INT)) {
                    Logger.e(TAG, "Configuration not compatible with API ${Build.VERSION.SDK_INT}")
                    return@withContext Result.failure(
                        UnsupportedOperationException("Configuration not supported on this device")
                    )
                }
                
                currentConfig = config
                Logger.d(TAG, "Recording configuration updated successfully")
                
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to update configuration", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Start recording with current configuration
     */
    suspend fun startRecording(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val config = currentConfig ?: RecordingConfig.createDefault().also {
                    Logger.i(TAG, "Using default recording configuration")
                    currentConfig = it
                }
                
                // Check if already recording
                if (_recordingState.value.isActive) {
                    Logger.w(TAG, "Recording already in progress")
                    return@withContext Result.failure(IllegalStateException("Recording already in progress"))
                }
                
                _recordingState.value = RecordingState.Preparing("Preparing to record...")
                
                // Validate permissions
                val permissionCheck = validatePermissions()
                if (!permissionCheck.isSuccess) {
                    Logger.e(TAG, "Permission validation failed")
                    _recordingState.value = RecordingState.Error(
                        RecordingState.ErrorCode.PERMISSION_DENIED,
                        "Required permissions not granted"
                    )
                    return@withContext Result.failure(permissionCheck.exceptionOrNull()!!)
                }
                
                // Check storage availability
                val storageCheck = validateStorageSpace(config)
                if (!storageCheck.isSuccess) {
                    Logger.e(TAG, "Storage validation failed")
                    _recordingState.value = RecordingState.Error(
                        RecordingState.ErrorCode.INSUFFICIENT_STORAGE,
                        storageCheck.exceptionOrNull()?.message ?: "Insufficient storage space"
                    )
                    return@withContext Result.failure(storageCheck.exceptionOrNull()!!)
                }
                
                // Create output directory
                val directoryResult = createOutputDirectory(config)
                if (!directoryResult.isSuccess) {
                    Logger.e(TAG, "Failed to create output directory")
                    _recordingState.value = RecordingState.Error(
                        RecordingState.ErrorCode.INVALID_OUTPUT_FILE,
                        "Failed to create output directory"
                    )
                    return@withContext Result.failure(directoryResult.exceptionOrNull()!!)
                }
                
                // Generate unique filename
                val outputPath = generateUniqueOutputPath(config)
                
                // Setup and start MediaRecorder
                val mediaRecorderResult = setupMediaRecorder(config, outputPath)
                if (!mediaRecorderResult.isSuccess) {
                    Logger.e(TAG, "Failed to setup MediaRecorder")
                    _recordingState.value = RecordingState.Error(
                        RecordingState.ErrorCode.MEDIA_RECORDER_ERROR,
                        mediaRecorderResult.exceptionOrNull()?.message ?: "Failed to setup MediaRecorder"
                    )
                    return@withContext Result.failure(mediaRecorderResult.exceptionOrNull()!!)
                }
                
                // Start recording
                mediaRecorder?.start()
                recordingStartTime = System.currentTimeMillis()
                
                _recordingState.value = RecordingState.Recording(outputPath, recordingStartTime)
                
                // Start monitoring recording
                startRecordingMonitoring(config, outputPath)
                
                Logger.i(TAG, "Recording started: ${inputValidator.sanitizeForLogging(outputPath)}")
                Result.success(outputPath)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to start recording", e)
                _recordingState.value = RecordingState.Error(
                    RecordingState.ErrorCode.MEDIA_RECORDER_ERROR,
                    e.message ?: "Unknown error occurred"
                )
                cleanup()
                Result.failure(e)
            }
        }
    }
    
    /**
     * Stop current recording
     */
    suspend fun stopRecording(): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val currentState = _recordingState.value
                if (!currentState.isActive && !currentState.isPreparing) {
                    Logger.w(TAG, "No active recording to stop")
                    return@withContext Result.failure(IllegalStateException("No active recording"))
                }
                
                _recordingState.value = RecordingState.Stopping("Stopping recording...")
                
                // Cancel monitoring
                recordingJob?.cancel()
                recordingJob = null
                
                val filePath = when (currentState) {
                    is RecordingState.Recording -> currentState.filePath
                    is RecordingState.Paused -> currentState.filePath
                    else -> null
                } ?: return@withContext Result.failure(IllegalStateException("No file path available"))
                
                // Stop and release MediaRecorder
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                
                // Get file info
                val outputFile = File(filePath)
                val duration = System.currentTimeMillis() - recordingStartTime
                val fileSize = if (outputFile.exists()) outputFile.length() else 0L
                
                _recordingState.value = RecordingState.Stopped(filePath, duration, fileSize)
                
                Logger.i(TAG, "Recording stopped successfully: ${inputValidator.sanitizeForLogging(filePath)}")
                Result.success(outputFile)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to stop recording", e)
                _recordingState.value = RecordingState.Error(
                    RecordingState.ErrorCode.MEDIA_RECORDER_ERROR,
                    e.message ?: "Failed to stop recording"
                )
                cleanup()
                Result.failure(e)
            }
        }
    }
    
    /**
     * Pause recording (API 24+)
     */
    suspend fun pauseRecording(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    return@withContext Result.failure(
                        UnsupportedOperationException("Pause/resume not supported on API < 24")
                    )
                }
                
                val currentState = _recordingState.value
                if (!currentState.isRecording) {
                    return@withContext Result.failure(IllegalStateException("Not currently recording"))
                }
                
                mediaRecorder?.pause()
                
                val recordingState = currentState as RecordingState.Recording
                _recordingState.value = RecordingState.Paused(recordingState.filePath)
                
                Logger.i(TAG, "Recording paused")
                Result.success(Unit)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to pause recording", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Resume recording (API 24+)
     */
    suspend fun resumeRecording(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    return@withContext Result.failure(
                        UnsupportedOperationException("Pause/resume not supported on API < 24")
                    )
                }
                
                val currentState = _recordingState.value
                if (!currentState.isPaused) {
                    return@withContext Result.failure(IllegalStateException("Not currently paused"))
                }
                
                mediaRecorder?.resume()
                
                val pausedState = currentState as RecordingState.Paused
                _recordingState.value = RecordingState.Recording(pausedState.filePath, recordingStartTime)
                
                Logger.i(TAG, "Recording resumed")
                Result.success(Unit)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to resume recording", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get list of recorded files
     */
    fun getRecordings(): Flow<List<RecordingInfo>> = flow {
        val config = currentConfig ?: RecordingConfig.createDefault()
        val outputDir = File(getOutputDirectory(config))
        
        if (!outputDir.exists() || !outputDir.isDirectory) {
            emit(emptyList())
            return@flow
        }
        
        val recordings = outputDir.listFiles { _, name ->
            name.lowercase().endsWith(".mp4")
        }?.mapNotNull { file ->
            try {
                RecordingInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    duration = 0L, // Would need MediaMetadataRetriever for actual duration
                    fileSize = file.length(),
                    createdAt = file.lastModified(),
                    quality = config.quality
                )
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to create RecordingInfo for ${file.name}", e)
                null
            }
        } ?: emptyList()
        
        emit(recordings.sortedByDescending { it.createdAt })
    }.flowOn(Dispatchers.IO)
    
    /**
     * Delete a recording file
     */
    suspend fun deleteRecording(filePath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Validate file path for security
                val pathValidation = inputValidator.validateDirectoryPath(filePath)
                if (!pathValidation.isValid) {
                    Logger.e(TAG, "Invalid file path for deletion")
                    return@withContext Result.failure(SecurityException("Invalid file path"))
                }
                
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(IllegalArgumentException("File does not exist"))
                }
                
                // Ensure file is within our app directory
                val config = currentConfig ?: RecordingConfig.createDefault()
                val appDir = File(getOutputDirectory(config)).canonicalPath
                if (!file.canonicalPath.startsWith(appDir)) {
                    Logger.e(TAG, "Attempted to delete file outside app directory")
                    return@withContext Result.failure(SecurityException("Cannot delete files outside app directory"))
                }
                
                val deleted = file.delete()
                if (deleted) {
                    Logger.i(TAG, "Recording deleted: ${inputValidator.sanitizeForLogging(filePath)}")
                    Result.success(Unit)
                } else {
                    Result.failure(RuntimeException("Failed to delete file"))
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to delete recording", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Clear current error state
     */
    suspend fun clearError(): Result<Unit> {
        return withContext(Dispatchers.Default) {
            if (_recordingState.value.isError) {
                _recordingState.value = RecordingState.Idle
                Logger.d(TAG, "Error state cleared")
            }
            Result.success(Unit)
        }
    }
    
    /**
     * Get current configuration
     */
    fun getCurrentConfig(): RecordingConfig = currentConfig ?: RecordingConfig.createDefault().also {
        currentConfig = it
    }
    
    
    /**
     * Prepare MediaRecorder for recording and return the surface for camera integration
     * This should be called before starting camera preview with surface integration
     */
    suspend fun prepareRecorderAndGetSurface(): Result<Surface> {
        return withContext(Dispatchers.IO) {
            try {
                val config = currentConfig ?: RecordingConfig.createDefault().also {
                    Logger.i(TAG, "Using default recording configuration")
                    currentConfig = it
                }
                
                // Validate permissions
                val permissionCheck = validatePermissions()
                if (!permissionCheck.isSuccess) {
                    Logger.e(TAG, "Permission validation failed")
                    return@withContext Result.failure(permissionCheck.exceptionOrNull()!!)
                }
                
                // Check storage availability
                val storageCheck = validateStorageSpace(config)
                if (!storageCheck.isSuccess) {
                    Logger.e(TAG, "Storage validation failed")
                    return@withContext Result.failure(storageCheck.exceptionOrNull()!!)
                }
                
                // Create output directory
                val directoryResult = createOutputDirectory(config)
                if (!directoryResult.isSuccess) {
                    Logger.e(TAG, "Failed to create output directory")
                    return@withContext Result.failure(directoryResult.exceptionOrNull()!!)
                }
                
                // Generate unique filename
                val outputPath = generateUniqueOutputPath(config)
                
                // Setup MediaRecorder
                val mediaRecorderResult = setupMediaRecorder(config, outputPath)
                if (!mediaRecorderResult.isSuccess) {
                    Logger.e(TAG, "Failed to setup MediaRecorder")
                    return@withContext Result.failure(mediaRecorderResult.exceptionOrNull()!!)
                }
                
                // Get surface from MediaRecorder
                val surface = mediaRecorder?.surface
                if (surface == null) {
                    Logger.e(TAG, "Failed to get surface from MediaRecorder")
                    return@withContext Result.failure(IllegalStateException("MediaRecorder surface is null"))
                }
                
                Logger.i(TAG, "MediaRecorder prepared and surface ready")
                Result.success(surface)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to prepare MediaRecorder", e)
                cleanup()
                Result.failure(e)
            }
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            recordingJob?.cancel()
            recordingJob = null
            
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Logger.w(TAG, "Error stopping MediaRecorder during cleanup", e)
                }
                try {
                    release()
                } catch (e: Exception) {
                    Logger.w(TAG, "Error releasing MediaRecorder during cleanup", e)
                }
            }
            mediaRecorder = null
            
            if (_recordingState.value.isActive) {
                _recordingState.value = RecordingState.Idle
            }
            
            Logger.d(TAG, "Repository cleanup completed")
        } catch (e: Exception) {
            Logger.e(TAG, "Error during cleanup", e)
        }
    }
    
    // Private helper methods
    
    private fun validatePermissions(): Result<Unit> {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            REQUIRED_PERMISSIONS_API_33_PLUS
        } else {
            REQUIRED_PERMISSIONS_LEGACY
        }
        
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        return if (missingPermissions.isEmpty()) {
            Result.success(Unit)
        } else {
            Logger.e(TAG, "Missing permissions: ${missingPermissions.joinToString()}")
            Result.failure(SecurityException("Missing required permissions: ${missingPermissions.joinToString()}"))
        }
    }
    
    private fun validateStorageSpace(config: RecordingConfig): Result<Unit> {
        val outputDir = File(getOutputDirectory(config))
        val freeSpace = outputDir.freeSpace
        
        return if (freeSpace >= config.minStorageRequired) {
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("Insufficient storage space. Required: ${config.minStorageRequired}, Available: $freeSpace"))
        }
    }
    
    private fun getOutputDirectory(config: RecordingConfig): String {
        val sanitizedDir = inputValidator.sanitizeDirectoryPath(config.outputDirectory)
        return if (sanitizedDir.startsWith("/")) {
            sanitizedDir
        } else {
            "${context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)}/$sanitizedDir"
        }
    }
    
    fun createOutputDirectory(config: RecordingConfig): Result<Unit> {
        return try {
            val outputDir = File(getOutputDirectory(config))
            if (!outputDir.exists()) {
                val created = outputDir.mkdirs()
                if (!created) {
                    Result.failure(RuntimeException("Failed to create output directory"))
                } else {
                    Result.success(Unit)
                }
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create output directory", e)
            Result.failure(e)
        }
    }
    
    fun generateUniqueFilename(config: RecordingConfig): String {
        val prefix = inputValidator.sanitizeFilename(config.filenamePrefix)
        return RecordingState.generateFileName(prefix)
    }
    
    private fun generateUniqueOutputPath(config: RecordingConfig): String {
        val outputDir = getOutputDirectory(config)
        val filename = generateUniqueFilename(config)
        return "$outputDir/$filename"
    }
    
    private fun setupMediaRecorder(config: RecordingConfig, outputPath: String): Result<MediaRecorder> {
        return try {
            cleanup() // Ensure clean state
            
            mediaRecorder = MediaRecorder().apply {
                // Audio source
                if (config.enableAudio) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                
                // Video source
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                
                // Output format
                setOutputFormat(config.outputFormat.mediaRecorderValue)
                
                // Video encoder
                setVideoEncoder(config.videoCodec.mediaRecorderValue)
                setVideoSize(config.quality.width, config.quality.height)
                setVideoFrameRate(config.quality.frameRate)
                setVideoEncodingBitRate(config.quality.bitrate)
                
                // Audio encoder (if enabled)
                if (config.enableAudio) {
                    setAudioEncoder(config.audioCodec.mediaRecorderValue)
                    setAudioSamplingRate(DEFAULT_AUDIO_SAMPLE_RATE)
                    setAudioEncodingBitRate(DEFAULT_AUDIO_BITRATE)
                }
                
                // Output file
                setOutputFile(outputPath)
                
                // Maximum file size
                setMaxFileSize(config.maxFileSize)
                
                // Maximum duration
                setMaxDuration(config.maxDuration * 1000) // Convert to milliseconds
                
                // Error listeners
                setOnErrorListener { _, what, extra ->
                    Logger.e(TAG, "MediaRecorder error: what=$what, extra=$extra")
                    GlobalScope.launch {
                        _recordingState.value = RecordingState.Error(
                            RecordingState.ErrorCode.MEDIA_RECORDER_ERROR,
                            "MediaRecorder error: $what"
                        )
                    }
                }
                
                // Info listeners for limits
                setOnInfoListener { _, what, extra ->
                    when (what) {
                        MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED -> {
                            Logger.i(TAG, "Maximum duration reached")
                            GlobalScope.launch {
                                _recordingState.value = RecordingState.Error(
                                    RecordingState.ErrorCode.MAX_DURATION_REACHED,
                                    "Maximum recording duration reached"
                                )
                            }
                        }
                        MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> {
                            Logger.i(TAG, "Maximum file size reached")
                            GlobalScope.launch {
                                _recordingState.value = RecordingState.Error(
                                    RecordingState.ErrorCode.MAX_FILESIZE_REACHED,
                                    "Maximum file size reached"
                                )
                            }
                        }
                    }
                }
                
                // Prepare MediaRecorder
                prepare()
            }
            
            Result.success(mediaRecorder!!)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to setup MediaRecorder", e)
            cleanup()
            Result.failure(e)
        }
    }
    
    /**
     * Get the MediaRecorder surface for camera integration
     * Must be called after MediaRecorder is prepared
     */
    fun getRecorderSurface(): Result<Surface> {
        return try {
            val recorder = mediaRecorder ?: return Result.failure(
                IllegalStateException("MediaRecorder not initialized. Call startRecording first.")
            )
            
            val surface = recorder.surface ?: return Result.failure(
                IllegalStateException("MediaRecorder surface not available. Ensure recorder is prepared.")
            )
            
            Result.success(surface)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get recorder surface", e)
            Result.failure(e)
        }
    }
    
    private fun startRecordingMonitoring(config: RecordingConfig, outputPath: String) {
        recordingJob?.cancel()
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && _recordingState.value.isActive) {
                try {
                    delay(RECORDING_UPDATE_INTERVAL)
                    
                    // Update recording state with current info
                    val currentState = _recordingState.value
                    if (currentState is RecordingState.Recording) {
                        _recordingState.value = RecordingState.Recording(
                            currentState.filePath,
                            currentState.startTime
                        )
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Logger.e(TAG, "Error in recording monitoring", e)
                    }
                    break
                }
            }
        }
    }
    
    fun validateOutputPath(path: String): Result<Unit> {
        val validation = inputValidator.validateDirectoryPath(path)
        return if (validation.isValid) {
            Result.success(Unit)
        } else {
            Result.failure(SecurityException(validation.errorMessage))
        }
    }
    
    fun validateRecordingCapabilities(config: RecordingConfig): Result<Unit> {
        // This would check if the device supports the requested configuration
        return try {
            if (!config.isCompatibleWithApi(Build.VERSION.SDK_INT)) {
                Result.failure(UnsupportedOperationException("Configuration not supported on this API level"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}