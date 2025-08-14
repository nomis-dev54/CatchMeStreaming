package com.example.catchmestreaming.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.catchmestreaming.data.*
import com.example.catchmestreaming.security.SecureStorage
import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.ValidationResult
// Android native streaming imports
import android.media.MediaRecorder
import android.view.Surface
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Simple HTTP/WebRTC streaming server implementation using Android MediaRecorder
 * This creates an HTTP-based streaming endpoint that clients can connect to
 */
private class AndroidStreamingServer(
    private val context: Context,
    private val port: Int = 8080
) {
    private var server: NettyApplicationEngine? = null
    private var mediaRecorder: MediaRecorder? = null
    private var streamingFile: String? = null
    
    fun startServer(): String {
        try {
            server = embeddedServer(Netty, port = port) {
                routing {
                    get("/stream") {
                        call.response.headers.append("Access-Control-Allow-Origin", "*")
                        
                        // For now, return a helpful message since video recording isn't implemented yet
                        call.response.headers.append("Content-Type", "text/plain")
                        call.respond(
                            io.ktor.http.HttpStatusCode.ServiceUnavailable,
                            """
                            CatchMeStreaming - Video Stream Placeholder
                            
                            The streaming service is running, but video encoding is not yet implemented.
                            
                            Current status:
                            - HTTP server: Active
                            - Video capture: Not implemented
                            - Stream file: ${streamingFile ?: "Not set"}
                            
                            This is a development version. Video streaming functionality 
                            will be added in future updates.
                            
                            Check /status endpoint for more technical details.
                            """.trimIndent()
                        )
                    }
                    get("/status") {
                        call.response.headers.append("Access-Control-Allow-Origin", "*")
                        call.response.headers.append("Content-Type", "text/plain")
                        
                        val statusText = """
                            CatchMeStreaming Service Status
                            ===============================
                            
                            Service: CatchMeStreaming
                            Version: Development
                            Server Status: Running
                            Streaming Status: Placeholder Mode
                            Video Encoding: Not Implemented
                            
                            Stream URL: http://0.0.0.0:$port/stream
                            Configured Output Path: ${streamingFile ?: "not_set"}
                            
                            Message: HTTP server is running but video capture/encoding is not yet implemented.
                            The /stream endpoint returns helpful information instead of actual video content.
                            
                            This is a development version of CatchMeStreaming.
                        """.trimIndent()
                        
                        call.respond(io.ktor.http.HttpStatusCode.OK, statusText)
                    }
                }
            }
            server?.start(wait = false)
            
            val networkUtil = com.example.catchmestreaming.util.NetworkUtil
            val deviceIP = networkUtil.getLocalIPAddress() ?: "localhost"
            return "http://$deviceIP:$port/stream"
            
        } catch (e: Exception) {
            Log.e("StreamServer", "Failed to start server", e)
            throw e
        }
    }
    
    fun stopServer() {
        server?.stop(1000, 2000)
        server = null
        mediaRecorder?.release()
        mediaRecorder = null
    }
    
    fun startRecording(outputPath: String, surface: Surface? = null) {
        streamingFile = outputPath
        
        Log.i("StreamServer", "Streaming service configured for: $outputPath")
        Log.i("StreamServer", "Note: Video encoding not yet implemented - serving placeholder content")
        
        // In a real implementation, this would be where MediaRecorder or CameraX VideoCapture starts
        // For now, we just note the path and serve a helpful message via HTTP
    }
}

/**
 * Repository for managing HTTP streaming functionality.
 * Implements security-first approach with comprehensive validation and error handling.
 */
class StreamRepository(
    private val context: Context,
    private val secureStorage: SecureStorage = SecureStorage(context),
    private val inputValidator: InputValidator = InputValidator()
) {
    
    companion object {
        private const val TAG = "StreamRepository"
        private const val PREFS_NAME = "stream_config_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_PORT = "port"
        private const val KEY_STREAM_PATH = "stream_path"
        private const val KEY_QUALITY = "quality"
        private const val KEY_ENABLE_AUDIO = "enable_audio"
        private const val KEY_MAX_BITRATE = "max_bitrate"
        private const val KEY_KEY_FRAME_INTERVAL = "key_frame_interval"
        private const val KEY_USE_AUTHENTICATION = "use_authentication"
        private const val KEY_USERNAME = "username"
    }
    
    // State management
    private val _streamState = MutableStateFlow<StreamState>(StreamState.Idle)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()
    
    // Configuration storage
    private var currentConfig: StreamConfig? = null
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Android native streaming server
    private var streamingServer: AndroidStreamingServer? = null
    
    // Threading protection
    private val streamingMutex = Mutex()
    
    // Statistics tracking
    private var currentStats: StreamingStats? = null
    
    init {
        // Load saved configuration on initialization
        loadConfiguration()
    }
    
    /**
     * Update the HTTP streaming configuration with security validation
     */
    suspend fun updateConfiguration(config: StreamConfig): Result<Unit> = streamingMutex.withLock {
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
            saveConfiguration(config)
            Log.i(TAG, "Configuration updated successfully")
            
            return Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error updating configuration", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Get the current configuration (without sensitive data)
     * If no configuration exists, creates and saves a default one
     */
    fun getCurrentConfig(): StreamConfig? {
        if (currentConfig == null) {
            // Create default configuration
            val defaultConfig = StreamConfig.createDefault(context)
            currentConfig = defaultConfig
            saveConfiguration(defaultConfig)
            Log.i(TAG, "Created and saved default configuration")
        }
        return currentConfig
    }
    
    /**
     * Start HTTP streaming with security checks
     */
    suspend fun startStreaming(): Result<String> = streamingMutex.withLock {
        try {
            Log.d(TAG, "Starting HTTP stream")
            
            // Check if already active
            if (_streamState.value.isActive) {
                Log.w(TAG, "Streaming already active")
                return Result.failure(IllegalStateException("Streaming already active"))
            }
            
            // Check configuration
            val config = currentConfig ?: run {
                Log.w(TAG, "No configuration available")
                return Result.failure(IllegalStateException("No configuration available. Please configure streaming settings first."))
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
            
            // Initialize streaming server
            _streamState.value = StreamState.Preparing("Initializing streaming server...")
            
            val streamingUrl = finalConfig.generateStreamingUrl()
            val displayUrl = finalConfig.generateDisplayUrl()
            
            try {
                // Initialize Android native streaming server
                val serverPort = finalConfig.port
                streamingServer = AndroidStreamingServer(context, serverPort)
                
                // Start streaming server
                _streamState.value = StreamState.Preparing("Starting streaming server...")
                
                val serverUrl = streamingServer?.startServer() 
                    ?: throw IllegalStateException("Failed to start streaming server")
                
                // Create output file for streaming
                val outputDir = context.getExternalFilesDir("streaming")
                outputDir?.mkdirs()
                val outputFile = "$outputDir/current_stream.mp4"
                
                // Start recording/streaming
                streamingServer?.startRecording(outputFile)
                
                Log.i(TAG, "Streaming server started at: $serverUrl")
                
                // Update the display URL to show the HTTP endpoint
                val actualDisplayUrl = serverUrl.replace("0.0.0.0", finalConfig.serverUrl)
                
                // Initialize statistics
                currentStats = StreamingStats(System.currentTimeMillis())
                
                // Transition to streaming state
                _streamState.value = StreamState.Streaming(
                    streamUrl = actualDisplayUrl,
                    quality = finalConfig.quality,
                    audioEnabled = finalConfig.enableAudio
                )
                
                Log.i(TAG, "HTTP streaming started successfully at: $actualDisplayUrl")
                return Result.success(actualDisplayUrl)
                
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Streaming server error", e)
                _streamState.value = StreamState.Error(
                    errorMessage = "Failed to start streaming server",
                    errorCode = StreamErrorCode.SERVER_UNREACHABLE,
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
                // Stop the streaming server
                streamingServer?.stopServer()
                streamingServer = null
                
                // Reset statistics
                currentStats = null
                
                // Transition to stopped state
                _streamState.value = StreamState.Stopped(
                    reason = "Stream stopped by user",
                    lastDuration = lastDuration
                )
                
                Log.i(TAG, "HTTP streaming stopped successfully")
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
     * Save configuration to persistent storage
     */
    private fun saveConfiguration(config: StreamConfig) {
        try {
            with(sharedPrefs.edit()) {
                putString(KEY_SERVER_URL, config.serverUrl)
                putInt(KEY_PORT, config.port)
                putString(KEY_STREAM_PATH, config.streamPath)
                putString(KEY_QUALITY, config.quality.name)
                putBoolean(KEY_ENABLE_AUDIO, config.enableAudio)
                putInt(KEY_MAX_BITRATE, config.maxBitrate)
                putInt(KEY_KEY_FRAME_INTERVAL, config.keyFrameInterval)
                putBoolean(KEY_USE_AUTHENTICATION, config.useAuthentication)
                putString(KEY_USERNAME, config.username)
                apply()
            }
            Log.d(TAG, "Configuration saved to persistent storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving configuration", e)
        }
    }
    
    /**
     * Load configuration from persistent storage
     */
    private fun loadConfiguration() {
        try {
            if (sharedPrefs.contains(KEY_SERVER_URL)) {
                val serverUrl = sharedPrefs.getString(KEY_SERVER_URL, "") ?: ""
                val port = sharedPrefs.getInt(KEY_PORT, 8080)
                val streamPath = sharedPrefs.getString(KEY_STREAM_PATH, "/stream") ?: "/stream"
                val qualityName = sharedPrefs.getString(KEY_QUALITY, StreamQuality.MEDIUM.name) ?: StreamQuality.MEDIUM.name
                val quality = try {
                    StreamQuality.valueOf(qualityName)
                } catch (e: IllegalArgumentException) {
                    StreamQuality.MEDIUM
                }
                val enableAudio = sharedPrefs.getBoolean(KEY_ENABLE_AUDIO, true)
                val maxBitrate = sharedPrefs.getInt(KEY_MAX_BITRATE, 2000000)
                val keyFrameInterval = sharedPrefs.getInt(KEY_KEY_FRAME_INTERVAL, 15)
                val useAuthentication = sharedPrefs.getBoolean(KEY_USE_AUTHENTICATION, false)
                val username = sharedPrefs.getString(KEY_USERNAME, "") ?: ""
                
                currentConfig = StreamConfig(
                    serverUrl = serverUrl,
                    username = username,
                    password = "", // Password not stored in SharedPreferences for security
                    port = port,
                    streamPath = streamPath,
                    quality = quality,
                    enableAudio = enableAudio,
                    maxBitrate = maxBitrate,
                    keyFrameInterval = keyFrameInterval,
                    useAuthentication = useAuthentication
                )
                
                Log.d(TAG, "Configuration loaded from persistent storage")
            } else {
                Log.d(TAG, "No saved configuration found, will use defaults when needed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading configuration", e)
            currentConfig = null
        }
    }
    
    /**
     * Validate HTTP streaming configuration with comprehensive security checks
     */
    private fun validateConfiguration(config: StreamConfig): ValidationResult {
        try {
            // Validate server URL
            val urlValidation = inputValidator.validateHttpUrl(config.serverUrl)
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
     * Handle connection errors from ConnectChecker callbacks
     */
    internal fun handleConnectionError(errorMessage: String) {
        _streamState.value = StreamState.Error(
            errorMessage = errorMessage,
            errorCode = StreamErrorCode.SERVER_UNREACHABLE
        )
    }
    
    /**
     * Handle disconnection from ConnectChecker callbacks
     */
    internal fun handleDisconnection() {
        _streamState.value = StreamState.Stopped(
            reason = "Connection lost",
            lastDuration = if (_streamState.value is StreamState.Streaming) {
                (_streamState.value as StreamState.Streaming).getFormattedDuration()
            } else null
        )
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            streamingServer?.stopServer()
            streamingServer = null
            currentStats = null
            _streamState.value = StreamState.Idle
            Log.d(TAG, "StreamRepository cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}