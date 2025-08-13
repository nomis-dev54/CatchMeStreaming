package com.example.catchmestreaming.data

import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.ValidationResult
import com.example.catchmestreaming.util.NetworkUtil

/**
 * Configuration data class for RTSP streaming settings.
 * Includes validation and security controls for all parameters.
 */
data class RTSPConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val port: Int = 554, // Default RTSP port
    val streamPath: String = "/live",
    val quality: StreamQuality = StreamQuality.MEDIUM,
    val enableAudio: Boolean = true,
    val maxBitrate: Int = 2000000, // 2 Mbps default
    val keyFrameInterval: Int = 15, // 15 seconds
    val useAuthentication: Boolean = true
) {
    
    companion object {
        private val validator = InputValidator()
        
        /**
         * Create a validated RTSPConfig instance.
         * @return Pair of RTSPConfig and ValidationResult
         */
        fun create(
            serverUrl: String,
            username: String = "",
            password: String = "",
            port: Int = 554,
            streamPath: String = "/live",
            quality: StreamQuality = StreamQuality.MEDIUM,
            enableAudio: Boolean = true,
            maxBitrate: Int = 2000000,
            keyFrameInterval: Int = 15,
            useAuthentication: Boolean = true
        ): Pair<RTSPConfig?, ValidationResult> {
            
            // Validate server URL
            val urlValidation = validator.validateRTSPUrl(serverUrl)
            if (!urlValidation.isValid) {
                return null to urlValidation
            }
            
            // Validate credentials if authentication is enabled
            if (useAuthentication) {
                if (username.isBlank()) {
                    return null to ValidationResult(false, "Username required when authentication is enabled")
                }
                
                val passwordValidation = validator.validatePassword(password)
                if (!passwordValidation.isValid) {
                    return null to passwordValidation
                }
            }
            
            // Validate port
            val portValidation = validator.validatePort(port)
            if (!portValidation.isValid) {
                return null to portValidation
            }
            
            // Validate stream path
            if (streamPath.isBlank()) {
                return null to ValidationResult(false, "Stream path cannot be empty")
            }
            
            if (validator.containsMaliciousContent(streamPath)) {
                return null to ValidationResult(false, "Stream path contains invalid characters")
            }
            
            // Validate bitrate
            if (maxBitrate < 100000 || maxBitrate > 10000000) { // 100 Kbps to 10 Mbps
                return null to ValidationResult(false, "Bitrate must be between 100 Kbps and 10 Mbps")
            }
            
            // Validate key frame interval
            if (keyFrameInterval < 1 || keyFrameInterval > 60) {
                return null to ValidationResult(false, "Key frame interval must be between 1 and 60 seconds")
            }
            
            val config = RTSPConfig(
                serverUrl = serverUrl,
                username = validator.sanitizeUsername(username),
                password = password, // Don't sanitize password - let user handle complexity
                port = port,
                streamPath = streamPath.trim(),
                quality = quality,
                enableAudio = enableAudio,
                maxBitrate = maxBitrate,
                keyFrameInterval = keyFrameInterval,
                useAuthentication = useAuthentication
            )
            
            return config to ValidationResult(true)
        }
    }
    
    /**
     * Generate the complete RTSP URL with authentication if enabled
     */
    fun generateRTSPUrl(): String {
        val baseUrl = if (serverUrl.startsWith("rtsp://")) {
            serverUrl
        } else {
            "rtsp://$serverUrl"
        }
        
        return if (useAuthentication && username.isNotBlank()) {
            val urlParts = baseUrl.split("://")
            "${urlParts[0]}://$username:$password@${urlParts[1]}:$port$streamPath"
        } else {
            "$baseUrl:$port$streamPath"
        }
    }
    
    /**
     * Generate RTSP URL for display (without credentials)
     */
    fun generateDisplayUrl(): String {
        val baseUrl = if (serverUrl.startsWith("rtsp://")) {
            serverUrl
        } else {
            "rtsp://$serverUrl"
        }
        
        return "$baseUrl:$port$streamPath"
    }
    
    /**
     * Create a copy with dynamic IP detection
     */
    fun withDynamicServerIP(): RTSPConfig {
        val dynamicIP = NetworkUtil.getLocalIPAddress() ?: serverUrl
        return copy(serverUrl = dynamicIP)
    }
    
    /**
     * Validate the current configuration
     */
    fun validate(): ValidationResult {
        val urlValidation = validator.validateRTSPUrl(serverUrl)
        if (!urlValidation.isValid) {
            return urlValidation
        }
        
        if (useAuthentication) {
            if (username.isBlank()) {
                return ValidationResult(false, "Username required when authentication is enabled")
            }
            
            val passwordValidation = validator.validatePassword(password)
            if (!passwordValidation.isValid) {
                return passwordValidation
            }
        }
        
        val portValidation = validator.validatePort(port)
        if (!portValidation.isValid) {
            return portValidation
        }
        
        return ValidationResult(true)
    }
    
    /**
     * Create a sanitized copy for logging (removes sensitive data)
     */
    fun toLogSafeString(): String {
        return "RTSPConfig(" +
                "serverUrl='${validator.sanitizeForLogging(serverUrl)}', " +
                "username='${validator.sanitizeForLogging(username)}', " +
                "password='***', " +
                "port=$port, " +
                "streamPath='$streamPath', " +
                "quality=$quality, " +
                "enableAudio=$enableAudio, " +
                "maxBitrate=$maxBitrate, " +
                "keyFrameInterval=$keyFrameInterval, " +
                "useAuthentication=$useAuthentication" +
                ")"
    }
}

/**
 * Enum for streaming quality presets
 */
enum class StreamQuality(
    val displayName: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int
) {
    LOW("Low (480p)", 640, 480, 15, 500000),
    MEDIUM("Medium (720p)", 1280, 720, 30, 2000000),
    HIGH("High (1080p)", 1920, 1080, 30, 4000000),
    ULTRA("Ultra (4K)", 3840, 2160, 30, 8000000);
    
    override fun toString(): String = displayName
}