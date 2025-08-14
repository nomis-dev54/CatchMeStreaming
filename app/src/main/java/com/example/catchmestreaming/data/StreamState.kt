package com.example.catchmestreaming.data

/**
 * Sealed class representing all possible states of HTTP streaming.
 * Provides type-safe state management for the streaming functionality.
 */
sealed class StreamState {
    
    /**
     * Initial state - streaming not configured or initialized
     */
    object Idle : StreamState()
    
    /**
     * Preparing to start streaming (validating config, initializing encoder)
     */
    data class Preparing(
        val message: String = "Preparing stream..."
    ) : StreamState()
    
    /**
     * Currently streaming
     */
    data class Streaming(
        val streamUrl: String,
        val startTime: Long = System.currentTimeMillis(),
        val quality: StreamQuality,
        val audioEnabled: Boolean = true
    ) : StreamState() {
        
        /**
         * Get streaming duration in milliseconds
         */
        fun getDurationMs(): Long = System.currentTimeMillis() - startTime
        
        /**
         * Get formatted streaming duration
         */
        fun getFormattedDuration(): String {
            val durationMs = getDurationMs()
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / (1000 * 60)) % 60
            val hours = (durationMs / (1000 * 60 * 60))
            
            return when {
                hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
                else -> String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
    
    /**
     * Stopping the stream
     */
    data class Stopping(
        val message: String = "Stopping stream..."
    ) : StreamState()
    
    /**
     * Stream stopped normally
     */
    data class Stopped(
        val reason: String = "Stream stopped",
        val lastDuration: String? = null
    ) : StreamState()
    
    /**
     * Error occurred during streaming
     */
    data class Error(
        val errorMessage: String,
        val errorCode: StreamErrorCode = StreamErrorCode.UNKNOWN,
        val throwable: Throwable? = null,
        val canRetry: Boolean = true
    ) : StreamState() {
        
        /**
         * Create user-friendly error message
         */
        fun getUserMessage(): String {
            return when (errorCode) {
                StreamErrorCode.NETWORK_ERROR -> "Network connection failed. Check your network settings."
                StreamErrorCode.AUTHENTICATION_FAILED -> "Authentication failed. Verify your credentials."
                StreamErrorCode.CAMERA_ERROR -> "Camera access failed. Check camera permissions."
                StreamErrorCode.ENCODER_ERROR -> "Video encoder failed. Try reducing quality settings."
                StreamErrorCode.CONFIGURATION_ERROR -> "Invalid configuration. Check your streaming settings."
                StreamErrorCode.PERMISSION_DENIED -> "Required permissions not granted."
                StreamErrorCode.SERVER_UNREACHABLE -> "Streaming server is unreachable. Check server address and port."
                StreamErrorCode.UNKNOWN -> errorMessage
            }
        }
    }
    
    /**
     * Convenience properties for state checking
     */
    val isIdle: Boolean get() = this is Idle
    val isPreparing: Boolean get() = this is Preparing
    val isStreaming: Boolean get() = this is Streaming
    val isStopping: Boolean get() = this is Stopping
    val isStopped: Boolean get() = this is Stopped
    val isError: Boolean get() = this is Error
    val isActive: Boolean get() = isPreparing || isStreaming || isStopping
    val canStart: Boolean get() = isIdle || isStopped || isError
    val canStop: Boolean get() = isPreparing || isStreaming
    
    /**
     * Get display message for current state
     */
    fun getDisplayMessage(): String {
        return when (this) {
            is Idle -> "Ready to stream"
            is Preparing -> message
            is Streaming -> "Streaming to $streamUrl"
            is Stopping -> message
            is Stopped -> reason
            is Error -> getUserMessage()
        }
    }
    
    /**
     * Get state name for logging
     */
    fun getStateName(): String {
        return when (this) {
            is Idle -> "IDLE"
            is Preparing -> "PREPARING"
            is Streaming -> "STREAMING"
            is Stopping -> "STOPPING"
            is Stopped -> "STOPPED"
            is Error -> "ERROR"
        }
    }
}

/**
 * Enum for different types of streaming errors
 */
enum class StreamErrorCode {
    NETWORK_ERROR,
    AUTHENTICATION_FAILED,
    CAMERA_ERROR,
    ENCODER_ERROR,
    CONFIGURATION_ERROR,
    PERMISSION_DENIED,
    SERVER_UNREACHABLE,
    UNKNOWN
}

/**
 * Data class for streaming statistics
 */
data class StreamingStats(
    val startTime: Long,
    val bytesTransmitted: Long = 0,
    val framesEncoded: Long = 0,
    val currentBitrate: Int = 0,
    val droppedFrames: Long = 0,
    val networkLatency: Long = 0
) {
    
    /**
     * Get transmission rate in KB/s
     */
    fun getTransmissionRate(): Double {
        val durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        return if (durationSeconds > 0) {
            (bytesTransmitted / 1024.0) / durationSeconds
        } else {
            0.0
        }
    }
    
    /**
     * Get frames per second
     */
    fun getFramesPerSecond(): Double {
        val durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        return if (durationSeconds > 0) {
            framesEncoded / durationSeconds
        } else {
            0.0
        }
    }
    
    /**
     * Get drop rate percentage
     */
    fun getDropRate(): Double {
        return if (framesEncoded > 0) {
            (droppedFrames.toDouble() / (framesEncoded + droppedFrames)) * 100
        } else {
            0.0
        }
    }
}