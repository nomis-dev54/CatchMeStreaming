package com.example.catchmestreaming.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Represents the current state of video recording.
 * Uses sealed class pattern for type-safe state management.
 */
sealed class RecordingState {
    
    /**
     * Recording is idle and ready to start
     */
    object Idle : RecordingState()
    
    /**
     * Recording is being prepared (MediaRecorder setup)
     */
    data class Preparing(val message: String = "Preparing to record...") : RecordingState()
    
    /**
     * Currently recording video/audio to file
     */
    data class Recording(
        val filePath: String,
        val startTime: Long = System.currentTimeMillis()
    ) : RecordingState()
    
    /**
     * Recording is paused (API 24+)
     */
    data class Paused(
        val filePath: String,
        val pausedAt: Long = System.currentTimeMillis()
    ) : RecordingState()
    
    /**
     * Recording is being stopped (cleanup in progress)
     */
    data class Stopping(val message: String = "Stopping recording...") : RecordingState()
    
    /**
     * Recording has been stopped successfully
     */
    data class Stopped(
        val filePath: String,
        val duration: Long,
        val fileSize: Long = 0L
    ) : RecordingState()
    
    /**
     * An error occurred during recording
     */
    data class Error(
        val code: ErrorCode,
        val message: String,
        val filePath: String? = null
    ) : RecordingState()
    
    /**
     * Error codes for recording failures
     */
    enum class ErrorCode {
        INSUFFICIENT_STORAGE,
        PERMISSION_DENIED,
        MEDIA_RECORDER_ERROR,
        INVALID_OUTPUT_FILE,
        MAX_DURATION_REACHED,
        MAX_FILESIZE_REACHED,
        CAMERA_ERROR,
        AUDIO_ERROR,
        UNKNOWN_ERROR
    }
    
    // Computed properties for state checking
    val isIdle: Boolean get() = this is Idle
    val isPreparing: Boolean get() = this is Preparing
    val isRecording: Boolean get() = this is Recording
    val isPaused: Boolean get() = this is Paused
    val isStopping: Boolean get() = this is Stopping
    val isStopped: Boolean get() = this is Stopped
    val isError: Boolean get() = this is Error
    val isActive: Boolean get() = isRecording || isPaused
    val canStart: Boolean get() = isIdle || isStopped || isError
    val canStop: Boolean get() = isRecording || isPaused || isPreparing
    val canPause: Boolean get() = isRecording
    val canResume: Boolean get() = isPaused
    
    /**
     * Get user-friendly display message for current state
     */
    fun getDisplayMessage(): String = when (this) {
        is Idle -> "Ready to record"
        is Preparing -> message
        is Recording -> "Recording in progress"
        is Paused -> "Recording paused"
        is Stopping -> message
        is Stopped -> "Recording completed"
        is Error -> "Error: $message"
    }
    
    /**
     * Get formatted duration for recording states
     */
    fun getFormattedDuration(): String? = when (this) {
        is Recording -> {
            val duration = (System.currentTimeMillis() - startTime) / 1000
            formatDuration(duration)
        }
        is Stopped -> formatDuration(duration / 1000)
        else -> null
    }
    
    /**
     * Get file size in human-readable format
     */
    fun getFormattedFileSize(): String? = when (this) {
        is Stopped -> formatFileSize(fileSize)
        is Recording -> {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    formatFileSize(file.length())
                } else null
            } catch (e: Exception) {
                null
            }
        }
        else -> null
    }
    
    companion object {
        /**
         * Format duration in seconds to HH:MM:SS
         */
        fun formatDuration(seconds: Long): String {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            
            return when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
                else -> String.format("%d:%02d", minutes, secs)
            }
        }
        
        /**
         * Format file size in bytes to human-readable format
         */
        fun formatFileSize(bytes: Long): String {
            val kb = 1024
            val mb = kb * 1024
            val gb = mb * 1024
            
            return when {
                bytes >= gb -> String.format("%.1f GB", bytes.toDouble() / gb)
                bytes >= mb -> String.format("%.1f MB", bytes.toDouble() / mb)
                bytes >= kb -> String.format("%.1f KB", bytes.toDouble() / kb)
                else -> "$bytes B"
            }
        }
        
        /**
         * Generate filename with timestamp
         */
        fun generateFileName(prefix: String = "recording"): String {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            return "${prefix}_${timestamp}.mp4"
        }
    }
}