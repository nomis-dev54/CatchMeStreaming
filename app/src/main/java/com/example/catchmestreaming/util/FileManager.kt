package com.example.catchmestreaming.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.example.catchmestreaming.data.RecordingConfig
import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * File management utility for MP4 recordings with security-first design.
 * Handles file operations, metadata extraction, and storage management.
 */
class FileManager(
    private val context: Context,
    private val inputValidator: InputValidator = InputValidator()
) {
    
    companion object {
        private const val TAG = "FileManager"
        private const val DEFAULT_RECORDINGS_DIR = "CatchMeStreaming"
        private const val MIN_FREE_SPACE_BYTES = 100_000_000L // 100MB minimum
        private const val MAX_FILENAME_LENGTH = 255
        private const val BYTES_PER_KB = 1024L
        private const val BYTES_PER_MB = BYTES_PER_KB * 1024L
        private const val BYTES_PER_GB = BYTES_PER_MB * 1024L
        
        /**
         * Format file size in human-readable format
         */
        fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= BYTES_PER_GB -> String.format("%.1f GB", bytes.toDouble() / BYTES_PER_GB)
                bytes >= BYTES_PER_MB -> String.format("%.1f MB", bytes.toDouble() / BYTES_PER_MB)
                bytes >= BYTES_PER_KB -> String.format("%.1f KB", bytes.toDouble() / BYTES_PER_KB)
                else -> "$bytes B"
            }
        }
        
        /**
         * Format duration in milliseconds to human-readable format
         */
        fun formatDuration(durationMs: Long): String {
            val seconds = durationMs / 1000
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            
            return when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
                else -> String.format("%d:%02d", minutes, secs)
            }
        }
    }
    
    /**
     * Detailed information about a recording file
     */
    data class RecordingFileInfo(
        val fileName: String,
        val filePath: String,
        val fileSize: Long,
        val duration: Long, // in milliseconds
        val resolution: String, // e.g., "1920x1080"
        val bitrate: Int,
        val frameRate: Int,
        val hasAudio: Boolean,
        val createdAt: Long,
        val lastModified: Long,
        val mimeType: String
    )
    
    /**
     * Storage information
     */
    data class StorageInfo(
        val totalSpace: Long,
        val freeSpace: Long,
        val usedSpace: Long,
        val recordingsSize: Long
    ) {
        val freeSpaceFormatted: String get() = FileManager.formatFileSize(freeSpace)
        val totalSpaceFormatted: String get() = FileManager.formatFileSize(totalSpace)
        val usedSpaceFormatted: String get() = FileManager.formatFileSize(usedSpace)
        val recordingsSizeFormatted: String get() = FileManager.formatFileSize(recordingsSize)
        val freeSpacePercentage: Double get() = (freeSpace.toDouble() / totalSpace.toDouble()) * 100
    }
    
    /**
     * Get the primary recordings directory
     */
    fun getRecordingsDirectory(): File {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File(moviesDir, DEFAULT_RECORDINGS_DIR)
    }
    
    /**
     * Get recording directory for specific configuration
     */
    fun getRecordingDirectory(config: RecordingConfig): File {
        val baseDir = getRecordingsDirectory()
        val sanitizedPath = inputValidator.sanitizeDirectoryPath(config.outputDirectory)
        return if (sanitizedPath.isBlank()) {
            baseDir
        } else {
            File(baseDir, sanitizedPath)
        }
    }
    
    /**
     * Ensure recording directory exists and is writable
     */
    suspend fun ensureRecordingDirectoryExists(config: RecordingConfig): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val directory = getRecordingDirectory(config)
                
                // Validate directory path for security
                val validation = inputValidator.validateDirectoryPath(directory.absolutePath)
                if (!validation.isValid) {
                    Logger.e(TAG, "Invalid directory path: ${validation.errorMessage}")
                    return@withContext Result.failure(
                        SecurityException("Invalid directory path: ${validation.errorMessage}")
                    )
                }
                
                // Create directory if it doesn't exist
                if (!directory.exists()) {
                    val created = directory.mkdirs()
                    if (!created) {
                        Logger.e(TAG, "Failed to create recording directory")
                        return@withContext Result.failure(
                            RuntimeException("Failed to create recording directory")
                        )
                    }
                }
                
                // Check if directory is writable
                if (!directory.canWrite()) {
                    Logger.e(TAG, "Recording directory is not writable")
                    return@withContext Result.failure(
                        RuntimeException("Recording directory is not writable")
                    )
                }
                
                Logger.d(TAG, "Recording directory ready: ${inputValidator.sanitizeForLogging(directory.absolutePath)}")
                Result.success(directory)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to ensure recording directory exists", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Generate unique filename with timestamp
     */
    fun generateUniqueFilename(prefix: String = "recording"): String {
        val sanitizedPrefix = inputValidator.sanitizeFilename(prefix)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val randomSuffix = (1000..9999).random() // Add randomness to avoid conflicts
        return "${sanitizedPrefix}_${timestamp}_${randomSuffix}.mp4"
    }
    
    /**
     * Get complete file path for a new recording
     */
    suspend fun generateRecordingFilePath(config: RecordingConfig): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val directoryResult = ensureRecordingDirectoryExists(config)
                if (directoryResult.isFailure) {
                    return@withContext directoryResult.map { "" }
                }
                
                val directory = directoryResult.getOrThrow()
                val filename = generateUniqueFilename(config.filenamePrefix)
                val filePath = File(directory, filename).absolutePath
                
                Logger.d(TAG, "Generated recording file path: ${inputValidator.sanitizeForLogging(filePath)}")
                Result.success(filePath)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to generate recording file path", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Extract detailed metadata from MP4 file
     */
    suspend fun extractFileMetadata(filePath: String): Result<RecordingFileInfo> {
        return withContext(Dispatchers.IO) {
            var retriever: MediaMetadataRetriever? = null
            try {
                // Validate file path for security
                val validation = inputValidator.validateDirectoryPath(filePath)
                if (!validation.isValid) {
                    return@withContext Result.failure(
                        SecurityException("Invalid file path: ${validation.errorMessage}")
                    )
                }
                
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("File does not exist: $filePath")
                    )
                }
                
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                
                // Extract metadata
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toIntOrNull() ?: 0
                val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)?.toBoolean() ?: false
                val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"
                
                val fileInfo = RecordingFileInfo(
                    fileName = file.name,
                    filePath = filePath,
                    fileSize = file.length(),
                    duration = duration,
                    resolution = if (width > 0 && height > 0) "${width}x${height}" else "Unknown",
                    bitrate = bitrate,
                    frameRate = frameRate,
                    hasAudio = hasAudio,
                    createdAt = file.lastModified(), // Approximation
                    lastModified = file.lastModified(),
                    mimeType = mimeType
                )
                
                Logger.d(TAG, "Extracted metadata for: ${inputValidator.sanitizeForLogging(file.name)}")
                Result.success(fileInfo)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to extract file metadata", e)
                Result.failure(e)
            } finally {
                try {
                    retriever?.release()
                } catch (e: Exception) {
                    Logger.w(TAG, "Error releasing MediaMetadataRetriever", e)
                }
            }
        }
    }
    
    /**
     * Get list of all recording files with metadata
     */
    suspend fun getAllRecordings(config: RecordingConfig): Result<List<RecordingFileInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val directory = getRecordingDirectory(config)
                if (!directory.exists() || !directory.isDirectory) {
                    Logger.d(TAG, "Recording directory does not exist")
                    return@withContext Result.success(emptyList())
                }
                
                val recordings = mutableListOf<RecordingFileInfo>()
                
                directory.listFiles { file ->
                    file.isFile && file.name.lowercase().endsWith(".mp4")
                }?.forEach { file ->
                    val metadataResult = extractFileMetadata(file.absolutePath)
                    if (metadataResult.isSuccess) {
                        recordings.add(metadataResult.getOrThrow())
                    } else {
                        Logger.w(TAG, "Failed to extract metadata for ${file.name}")
                    }
                }
                
                // Sort by creation time (newest first)
                recordings.sortByDescending { it.createdAt }
                
                Logger.d(TAG, "Found ${recordings.size} recordings")
                Result.success(recordings)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to get recordings list", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Delete a recording file securely
     */
    suspend fun deleteRecording(filePath: String, config: RecordingConfig): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Validate file path for security
                val validation = inputValidator.validateDirectoryPath(filePath)
                if (!validation.isValid) {
                    return@withContext Result.failure(
                        SecurityException("Invalid file path: ${validation.errorMessage}")
                    )
                }
                
                val file = File(filePath)
                if (!file.exists()) {
                    Logger.w(TAG, "File does not exist: ${inputValidator.sanitizeForLogging(filePath)}")
                    return@withContext Result.success(Unit)
                }
                
                // Ensure file is within our recordings directory
                val recordingsDir = getRecordingDirectory(config)
                val canonicalFilePath = file.canonicalPath
                val canonicalDirPath = recordingsDir.canonicalPath
                
                if (!canonicalFilePath.startsWith(canonicalDirPath)) {
                    Logger.e(TAG, "Attempted to delete file outside recordings directory")
                    return@withContext Result.failure(
                        SecurityException("Cannot delete files outside recordings directory")
                    )
                }
                
                // Delete the file
                val deleted = file.delete()
                if (deleted) {
                    Logger.i(TAG, "Recording deleted: ${inputValidator.sanitizeForLogging(file.name)}")
                    Result.success(Unit)
                } else {
                    Logger.e(TAG, "Failed to delete file: ${inputValidator.sanitizeForLogging(file.name)}")
                    Result.failure(RuntimeException("Failed to delete file"))
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error deleting recording", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get storage information for recordings
     */
    suspend fun getStorageInfo(config: RecordingConfig): Result<StorageInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val recordingsDir = getRecordingDirectory(config)
                val stat = StatFs(recordingsDir.absolutePath)
                
                val totalSpace = stat.blockSizeLong * stat.blockCountLong
                val freeSpace = stat.blockSizeLong * stat.availableBlocksLong
                val usedSpace = totalSpace - freeSpace
                
                // Calculate size of all recordings
                var recordingsSize = 0L
                if (recordingsDir.exists()) {
                    recordingsDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            recordingsSize += file.length()
                        }
                    }
                }
                
                val storageInfo = StorageInfo(
                    totalSpace = totalSpace,
                    freeSpace = freeSpace,
                    usedSpace = usedSpace,
                    recordingsSize = recordingsSize
                )
                
                Logger.d(TAG, "Storage info - Free: ${storageInfo.freeSpaceFormatted}, " +
                        "Recordings: ${storageInfo.recordingsSizeFormatted}")
                
                Result.success(storageInfo)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to get storage info", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Check if there's enough space for recording
     */
    suspend fun checkSpaceForRecording(
        config: RecordingConfig,
        estimatedDurationSeconds: Int
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val storageInfoResult = getStorageInfo(config)
                if (storageInfoResult.isFailure) {
                    return@withContext storageInfoResult.map { false }
                }
                
                val storageInfo = storageInfoResult.getOrThrow()
                val estimatedSize = config.getEstimatedFileSize(estimatedDurationSeconds)
                val requiredSpace = estimatedSize + MIN_FREE_SPACE_BYTES
                
                val hasSpace = storageInfo.freeSpace >= requiredSpace
                
                Logger.d(TAG, "Space check - Required: ${FileManager.formatFileSize(requiredSpace)}, " +
                        "Available: ${storageInfo.freeSpaceFormatted}, Result: $hasSpace")
                
                Result.success(hasSpace)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to check storage space", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Clean up old recordings if storage is low
     */
    suspend fun cleanupOldRecordings(
        config: RecordingConfig,
        maxRecordings: Int = 50,
        maxTotalSize: Long = 5 * BYTES_PER_GB
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val recordingsResult = getAllRecordings(config)
                if (recordingsResult.isFailure) {
                    return@withContext recordingsResult.map { 0 }
                }
                
                val recordings = recordingsResult.getOrThrow().toMutableList()
                var deletedCount = 0
                
                // Sort by creation time (oldest first)
                recordings.sortBy { it.createdAt }
                
                var totalSize = recordings.sumOf { it.fileSize }
                
                // Delete oldest recordings if we exceed limits
                while ((recordings.size > maxRecordings || totalSize > maxTotalSize) && recordings.isNotEmpty()) {
                    val oldestRecording = recordings.removeFirst()
                    val deleteResult = deleteRecording(oldestRecording.filePath, config)
                    
                    if (deleteResult.isSuccess) {
                        totalSize -= oldestRecording.fileSize
                        deletedCount++
                        Logger.d(TAG, "Cleaned up old recording: ${oldestRecording.fileName}")
                    } else {
                        Logger.w(TAG, "Failed to delete old recording: ${oldestRecording.fileName}")
                    }
                }
                
                Logger.i(TAG, "Cleanup completed, deleted $deletedCount recordings")
                Result.success(deletedCount)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to cleanup old recordings", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Validate file path security
     */
    fun validateFilePath(filePath: String): ValidationResult {
        return inputValidator.validateDirectoryPath(filePath)
    }
}