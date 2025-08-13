package com.example.catchmestreaming.repository

import android.content.Context
import android.util.Log
import com.example.catchmestreaming.data.*
import com.example.catchmestreaming.security.SecureStorage
import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.ValidationResult
// RootEncoder imports - will be available when library is downloaded
// import com.pedro.encoder.input.video.CameraOpenException  
// import com.pedro.rtplibrary.rtsp.RtspCamera1
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

// Temporary placeholder classes for RootEncoder - will be replaced when library is available
private class CameraOpenException(message: String) : Exception(message)
private class RtspCamera1(private val context: Context) {
    fun prepareVideo(width: Int, height: Int, fps: Int, bitrate: Int, rotation: Int): Boolean = true
    fun prepareAudio(sampleRate: Int, stereo: Boolean, bitrate: Int): Boolean = true
    fun startStream(url: String): Boolean = true
    fun stopStream() {}
}

/**
 * Repository for managing RTSP streaming functionality.
 * Implements security-first approach with comprehensive validation and error handling.
 */
class StreamRepository(
    private val context: Context,
    private val secureStorage: SecureStorage = SecureStorage(context),
    private val inputValidator: InputValidator = InputValidator()
) {
    
    companion object {
        private const val TAG = "StreamRepository"
    }
    
    // State management
    private val _streamState = MutableStateFlow<StreamState>(StreamState.Idle)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()
    
    // Configuration storage
    private var currentConfig: RTSPConfig? = null
    
    // RootEncoder RTSP client
    private var rtspCamera: RtspCamera1? = null
    
    // Threading protection
    private val streamingMutex = Mutex()
    
    // Statistics tracking
    private var currentStats: StreamingStats? = null
    
    /**
     * Update the RTSP configuration with security validation
     */
    suspend fun updateConfiguration(config: RTSPConfig): Result<Unit> = streamingMutex.withLock {
        try {
            Log.d(TAG, "Updating configuration: ${config.toLogSafeString()}")
            
            // Validate the configuration using injected validator (for testability)
            val validationResult = validateConfiguration(config)
            if (!validationResult.isValid) {
                Log.w(TAG, "Configuration validation failed: ${validationResult.errorMessage}")
                return Result.failure(IllegalArgumentException(validationResult.errorMessage))
            }
            
            // Handle credential storage
            if (config.useAuthentication) {
                if (config.username.isBlank() || config.password.isBlank()) {
                    return Result.failure(IllegalArgumentException("Username and password required for authentication"))
                }
                
                // Store credentials securely
                val storageResult = secureStorage.storeCredentials(config.username, config.password)
                if (storageResult.isFailure) {
                    Log.e(TAG, "Failed to store credentials securely")
                    return Result.failure(IllegalStateException("Failed to store credentials securely"))
                }
            } else {
                // Clear credentials if authentication is disabled
                secureStorage.deleteCredentials()
            }
            
            // Store configuration
            currentConfig = config
            Log.i(TAG, "Configuration updated successfully")
            
            return Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error updating configuration", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Get the current configuration (without sensitive data)
     */
    fun getCurrentConfig(): RTSPConfig? = currentConfig
    
    /**
     * Start RTSP streaming with security checks
     */
    suspend fun startStreaming(): Result<String> = streamingMutex.withLock {
        try {
            Log.d(TAG, "Starting RTSP stream")
            
            // Check if already active
            if (_streamState.value.isActive) {
                Log.w(TAG, "Streaming already active")
                return Result.failure(IllegalStateException("Streaming already active"))
            }
            
            // Check configuration
            val config = currentConfig ?: run {
                Log.w(TAG, "No configuration available")
                return Result.failure(IllegalStateException("No configuration available. Please configure RTSP settings first."))
            }
            
            // Transition to preparing state
            _streamState.value = StreamState.Preparing("Validating configuration...")
            
            // Re-validate configuration
            val validationResult = config.validate()
            if (!validationResult.isValid) {
                Log.w(TAG, "Configuration validation failed during start: ${validationResult.errorMessage}")
                _streamState.value = StreamState.Error(
                    errorMessage = "Configuration validation failed: ${validationResult.errorMessage}",
                    errorCode = StreamErrorCode.CONFIGURATION_ERROR
                )
                return Result.failure(IllegalArgumentException(validationResult.errorMessage))
            }
            
            // Retrieve credentials if needed
            var finalConfig = config
            if (config.useAuthentication) {
                _streamState.value = StreamState.Preparing("Retrieving credentials...")
                
                val credentialsResult = secureStorage.retrieveCredentials()
                if (credentialsResult.isFailure) {
                    Log.e(TAG, "Failed to retrieve credentials")
                    _streamState.value = StreamState.Error(
                        errorMessage = "Failed to retrieve stored credentials",
                        errorCode = StreamErrorCode.CONFIGURATION_ERROR
                    )
                    return Result.failure(IllegalStateException("Failed to retrieve credentials"))
                }
                
                val (username, password) = credentialsResult.getOrThrow()
                finalConfig = config.copy(username = username, password = password)
            }
            
            // Initialize RTSP encoder
            _streamState.value = StreamState.Preparing("Initializing encoder...")
            
            val rtspUrl = finalConfig.generateRTSPUrl()
            val displayUrl = finalConfig.generateDisplayUrl()
            
            try {
                // Initialize RootEncoder RTSP camera
                rtspCamera = RtspCamera1(context)
                
                // Configure video parameters
                val videoWidth = finalConfig.quality.width
                val videoHeight = finalConfig.quality.height
                val videoBitrate = finalConfig.maxBitrate
                val videoFps = finalConfig.quality.fps
                
                val prepareVideo = rtspCamera?.prepareVideo(
                    videoWidth,
                    videoHeight,
                    videoFps,
                    videoBitrate,
                    0 // Rotation
                ) ?: false
                
                if (!prepareVideo) {
                    throw IllegalStateException("Failed to prepare video encoder")
                }
                
                // Configure audio if enabled
                if (finalConfig.enableAudio) {
                    val prepareAudio = rtspCamera?.prepareAudio(
                        44100, // Sample rate
                        true,  // Stereo
                        128000 // Audio bitrate
                    ) ?: false
                    
                    if (!prepareAudio) {
                        Log.w(TAG, "Failed to prepare audio encoder, continuing without audio")
                    }
                }
                
                // Start streaming
                _streamState.value = StreamState.Preparing("Connecting to server...")
                
                val startResult = rtspCamera?.startStream(rtspUrl) ?: false
                
                if (!startResult) {
                    throw IllegalStateException("Failed to start RTSP stream")
                }
                
                // Initialize statistics
                currentStats = StreamingStats(System.currentTimeMillis())
                
                // Transition to streaming state
                _streamState.value = StreamState.Streaming(
                    rtspUrl = displayUrl,
                    quality = finalConfig.quality,
                    audioEnabled = finalConfig.enableAudio
                )
                
                Log.i(TAG, "RTSP streaming started successfully to: $displayUrl")
                return Result.success(displayUrl)
                
            } catch (e: CameraOpenException) {
                Log.e(TAG, "Camera error during streaming start", e)
                _streamState.value = StreamState.Error(
                    errorMessage = "Camera access failed",
                    errorCode = StreamErrorCode.CAMERA_ERROR,
                    throwable = e
                )
                return Result.failure(e)
                
            } catch (e: ConnectException) {
                Log.e(TAG, "Network connection error", e)
                _streamState.value = StreamState.Error(
                    errorMessage = "Failed to connect to RTSP server",
                    errorCode = StreamErrorCode.SERVER_UNREACHABLE,
                    throwable = e
                )
                return Result.failure(e)
                
            } catch (e: UnknownHostException) {
                Log.e(TAG, "Server address resolution failed", e)
                _streamState.value = StreamState.Error(
                    errorMessage = "Cannot resolve server address",
                    errorCode = StreamErrorCode.NETWORK_ERROR,
                    throwable = e
                )
                return Result.failure(e)
                
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Connection timeout", e)
                _streamState.value = StreamState.Error(
                    errorMessage = "Connection timeout",
                    errorCode = StreamErrorCode.NETWORK_ERROR,
                    throwable = e
                )
                return Result.failure(e)
                
            } catch (e: SSLHandshakeException) {
                Log.e(TAG, "Authentication failed", e)
                _streamState.value = StreamState.Error(
                    errorMessage = "Authentication failed",
                    errorCode = StreamErrorCode.AUTHENTICATION_FAILED,
                    throwable = e
                )
                return Result.failure(e)
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during streaming start", e)
                _streamState.value = StreamState.Error(
                    errorMessage = "Failed to start streaming: ${e.localizedMessage}",
                    errorCode = StreamErrorCode.UNKNOWN,
                    throwable = e
                )
                return Result.failure(e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in startStreaming", e)
            _streamState.value = StreamState.Error(
                errorMessage = "Critical streaming error",
                errorCode = StreamErrorCode.UNKNOWN,
                throwable = e,
                canRetry = false
            )
            return Result.failure(e)
        }
    }
    
    /**
     * Stop RTSP streaming
     */
    suspend fun stopStreaming(): Result<Unit> = streamingMutex.withLock {
        try {
            Log.d(TAG, "Stopping RTSP stream")
            
            val currentState = _streamState.value
            
            // Handle stop when not active (idempotent operation)
            if (!currentState.isActive) {
                Log.d(TAG, "Stream not active, nothing to stop")
                return Result.success(Unit)
            }
            
            // Transition to stopping state
            _streamState.value = StreamState.Stopping("Stopping stream...")
            
            // Get duration from current streaming state
            val lastDuration = if (currentState is StreamState.Streaming) {
                currentState.getFormattedDuration()
            } else null
            
            try {
                // Stop the RTSP stream
                rtspCamera?.stopStream()
                rtspCamera = null
                
                // Reset statistics
                currentStats = null
                
                // Transition to stopped state
                _streamState.value = StreamState.Stopped(
                    reason = "Stream stopped by user",
                    lastDuration = lastDuration
                )
                
                Log.i(TAG, "RTSP streaming stopped successfully")
                return Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping stream", e)
                _streamState.value = StreamState.Error(
                    errorMessage = "Error stopping stream: ${e.localizedMessage}",
                    errorCode = StreamErrorCode.UNKNOWN,
                    throwable = e,
                    canRetry = false
                )
                return Result.failure(e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in stopStreaming", e)
            _streamState.value = StreamState.Error(
                errorMessage = "Critical error stopping stream",
                errorCode = StreamErrorCode.UNKNOWN,
                throwable = e,
                canRetry = false
            )
            return Result.failure(e)
        }
    }
    
    /**
     * Get current streaming statistics
     */
    fun getStreamingStats(): StreamingStats? = currentStats
    
    /**
     * Clear error state and return to idle
     */
    suspend fun clearError(): Result<Unit> = streamingMutex.withLock {
        try {
            if (_streamState.value.isError) {
                _streamState.value = StreamState.Idle
                Log.d(TAG, "Error state cleared")
                return Result.success(Unit)
            }
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing error state", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Validate RTSP configuration with comprehensive security checks
     */
    private fun validateConfiguration(config: RTSPConfig): ValidationResult {
        try {
            // Validate server URL
            val urlValidation = inputValidator.validateRTSPUrl(config.serverUrl)
            if (!urlValidation.isValid) {
                return urlValidation
            }
            
            // Validate credentials if authentication is enabled
            if (config.useAuthentication) {
                if (config.username.isBlank()) {
                    return ValidationResult(false, "Username required when authentication is enabled")
                }
                
                val passwordValidation = inputValidator.validatePassword(config.password)
                if (!passwordValidation.isValid) {
                    return passwordValidation
                }
            }
            
            // Validate port
            val portValidation = inputValidator.validatePort(config.port)
            if (!portValidation.isValid) {
                return portValidation
            }
            
            // Validate stream path
            if (config.streamPath.isBlank()) {
                return ValidationResult(false, "Stream path cannot be empty")
            }
            
            if (inputValidator.containsMaliciousContent(config.streamPath)) {
                return ValidationResult(false, "Stream path contains potentially dangerous content")
            }
            
            // Validate bitrate
            if (config.maxBitrate < 100000 || config.maxBitrate > 10000000) {
                return ValidationResult(false, "Bitrate must be between 100 Kbps and 10 Mbps")
            }
            
            // Validate key frame interval
            if (config.keyFrameInterval < 1 || config.keyFrameInterval > 60) {
                return ValidationResult(false, "Key frame interval must be between 1 and 60 seconds")
            }
            
            return ValidationResult(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating configuration", e)
            return ValidationResult(false, "Configuration validation failed")
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            rtspCamera?.stopStream()
            rtspCamera = null
            currentStats = null
            _streamState.value = StreamState.Idle
            Log.d(TAG, "StreamRepository cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}