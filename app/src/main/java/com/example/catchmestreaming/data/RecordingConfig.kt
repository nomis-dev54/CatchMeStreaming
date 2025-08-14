package com.example.catchmestreaming.data

import android.media.MediaRecorder
import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.ValidationResult

/**
 * Configuration for video recording with validation and quality settings.
 * Follows security-first design principles with comprehensive validation.
 */
data class RecordingConfig(
    val quality: VideoQuality = VideoQuality.HD_1080P,
    val outputDirectory: String = "Movies/CatchMeStreaming/",
    val enableAudio: Boolean = true,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val audioCodec: AudioCodec = AudioCodec.AAC,
    val maxFileSize: Long = 2_000_000_000L, // 2GB default
    val maxDuration: Int = 3600, // 1 hour default (seconds)
    val minStorageRequired: Long = 500_000_000L, // 500MB minimum free space
    val filenamePrefix: String = "recording"
) {
    
    /**
     * Video quality presets with encoding parameters
     */
    enum class VideoQuality(
        val displayName: String,
        val width: Int,
        val height: Int,
        val bitrate: Int,
        val frameRate: Int
    ) {
        HD_720P("720p HD", 1280, 720, 2_000_000, 30),
        HD_1080P("1080p Full HD", 1920, 1080, 4_000_000, 30),
        UHD_4K("4K Ultra HD", 3840, 2160, 12_000_000, 30),
        MEDIUM("Medium (480p)", 854, 480, 1_000_000, 24),
        LOW("Low (360p)", 640, 360, 500_000, 24);
        
        val aspectRatio: Float get() = width.toFloat() / height.toFloat()
    }
    
    /**
     * Supported video codecs
     */
    enum class VideoCodec(val displayName: String, val mediaRecorderValue: Int) {
        H264("H.264 (AVC)", MediaRecorder.VideoEncoder.H264),
        H265("H.265 (HEVC)", MediaRecorder.VideoEncoder.HEVC), // API 21+
        VP8("VP8", MediaRecorder.VideoEncoder.VP8),
        DEFAULT("Default", MediaRecorder.VideoEncoder.DEFAULT)
    }
    
    /**
     * Supported audio codecs
     */
    enum class AudioCodec(val displayName: String, val mediaRecorderValue: Int) {
        AAC("AAC", MediaRecorder.AudioEncoder.AAC),
        AMR_NB("AMR NB", MediaRecorder.AudioEncoder.AMR_NB),
        AMR_WB("AMR WB", MediaRecorder.AudioEncoder.AMR_WB),
        HE_AAC("HE-AAC", MediaRecorder.AudioEncoder.HE_AAC), // API 16+
        AAC_ELD("AAC-ELD", MediaRecorder.AudioEncoder.AAC_ELD), // API 16+
        DEFAULT("Default", MediaRecorder.AudioEncoder.DEFAULT)
    }
    
    /**
     * Container format for output
     */
    enum class OutputFormat(val displayName: String, val mediaRecorderValue: Int) {
        MP4("MP4", MediaRecorder.OutputFormat.MPEG_4),
        THREE_GPP("3GP", MediaRecorder.OutputFormat.THREE_GPP),
        WEBM("WebM", MediaRecorder.OutputFormat.WEBM) // API 21+
    }
    
    // Computed properties
    val outputFormat: OutputFormat = OutputFormat.MP4 // Default to MP4
    val estimatedBitrate: Int get() = quality.bitrate + if (enableAudio) 128_000 else 0
    
    /**
     * Validate the entire configuration
     */
    fun validate(inputValidator: InputValidator): ValidationResult {
        // Validate output directory
        val directoryValidation = inputValidator.validateDirectoryPath(outputDirectory)
        if (!directoryValidation.isValid) {
            return ValidationResult(false, "Invalid output directory: ${directoryValidation.errorMessage}")
        }
        
        // Validate filename prefix
        val filenameValidation = inputValidator.validateFilename(filenamePrefix)
        if (!filenameValidation.isValid) {
            return ValidationResult(false, "Invalid filename prefix: ${filenameValidation.errorMessage}")
        }
        
        // Validate file size limits
        if (maxFileSize <= 0 || maxFileSize > 4_000_000_000L) { // 4GB max for FAT32 compatibility
            return ValidationResult(false, "Max file size must be between 1 byte and 4GB")
        }
        
        // Validate duration limits
        if (maxDuration <= 0 || maxDuration > 86400) { // 24 hours max
            return ValidationResult(false, "Max duration must be between 1 second and 24 hours")
        }
        
        // Validate storage requirements
        if (minStorageRequired < 100_000_000L) { // 100MB minimum
            return ValidationResult(false, "Minimum storage required cannot be less than 100MB")
        }
        
        return ValidationResult(true)
    }
    
    /**
     * Get sanitized output directory path
     */
    fun getSanitizedOutputDirectory(inputValidator: InputValidator): String {
        return inputValidator.sanitizeDirectoryPath(outputDirectory)
    }
    
    /**
     * Get sanitized filename prefix
     */
    fun getSanitizedFilenamePrefix(inputValidator: InputValidator): String {
        return inputValidator.sanitizeFilename(filenamePrefix)
    }
    
    /**
     * Generate output filename with timestamp
     */
    fun generateOutputFilename(): String {
        return RecordingState.generateFileName(filenamePrefix)
    }
    
    /**
     * Get complete output file path
     */
    fun getOutputFilePath(inputValidator: InputValidator): String {
        val sanitizedDir = getSanitizedOutputDirectory(inputValidator)
        val filename = generateOutputFilename()
        return "$sanitizedDir/$filename"
    }
    
    /**
     * Get estimated file size for given duration (in bytes)
     */
    fun getEstimatedFileSize(durationSeconds: Int): Long {
        val bitsPerSecond = estimatedBitrate.toLong()
        return (bitsPerSecond * durationSeconds) / 8 // Convert bits to bytes
    }
    
    /**
     * Check if this configuration is compatible with current device API level
     */
    fun isCompatibleWithApi(apiLevel: Int): Boolean {
        return when {
            videoCodec == VideoCodec.H265 && apiLevel < 21 -> false
            audioCodec == AudioCodec.HE_AAC && apiLevel < 16 -> false
            audioCodec == AudioCodec.AAC_ELD && apiLevel < 16 -> false
            outputFormat == OutputFormat.WEBM && apiLevel < 21 -> false
            else -> true
        }
    }
    
    /**
     * Get configuration summary for logging/debugging
     */
    fun getConfigSummary(): String {
        return """
            Recording Configuration:
            - Quality: ${quality.displayName} (${quality.width}x${quality.height} @ ${quality.frameRate}fps)
            - Video Codec: ${videoCodec.displayName}
            - Audio: ${if (enableAudio) "${audioCodec.displayName}" else "Disabled"}
            - Output Format: ${outputFormat.displayName}
            - Max File Size: ${RecordingState.formatFileSize(maxFileSize)}
            - Max Duration: ${RecordingState.formatDuration(maxDuration.toLong())}
            - Estimated Bitrate: ${estimatedBitrate / 1000}k
        """.trimIndent()
    }
    
    companion object {
        /**
         * Create default configuration optimized for quality and compatibility
         */
        fun createDefault(): RecordingConfig {
            return RecordingConfig(
                quality = VideoQuality.HD_1080P,
                enableAudio = true,
                videoCodec = VideoCodec.H264, // Most compatible
                audioCodec = AudioCodec.AAC,  // Most compatible
                maxFileSize = 2_000_000_000L, // 2GB
                maxDuration = 3600 // 1 hour
            )
        }
        
        /**
         * Create configuration optimized for battery life and storage
         */
        fun createLowPower(): RecordingConfig {
            return RecordingConfig(
                quality = VideoQuality.MEDIUM,
                enableAudio = true,
                videoCodec = VideoCodec.H264,
                audioCodec = AudioCodec.AAC,
                maxFileSize = 1_000_000_000L, // 1GB
                maxDuration = 1800 // 30 minutes
            )
        }
        
        /**
         * Create configuration for high-quality recording
         */
        fun createHighQuality(): RecordingConfig {
            return RecordingConfig(
                quality = VideoQuality.UHD_4K,
                enableAudio = true,
                videoCodec = VideoCodec.H264, // H265 might not be supported on all devices
                audioCodec = AudioCodec.HE_AAC,
                maxFileSize = 4_000_000_000L, // 4GB
                maxDuration = 7200 // 2 hours
            )
        }
    }
}