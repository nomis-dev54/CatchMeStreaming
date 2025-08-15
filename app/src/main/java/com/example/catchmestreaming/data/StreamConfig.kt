package com.example.catchmestreaming.data

import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.ValidationResult
import com.example.catchmestreaming.util.NetworkUtil

/**
 * Configuration data class for HTTP streaming settings.
 * Includes validation and security controls for all parameters.
 */
data class StreamConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val port: Int = 8080, // Default HTTP port for streaming
    val streamPath: String = "/stream",
    val quality: StreamQuality = StreamQuality.MEDIUM,
    val enableAudio: Boolean = true,
    val maxBitrate: Int = 2000000, // 2 Mbps default
    val keyFrameInterval: Int = 15, // 15 seconds
    val useAuthentication: Boolean = false, // HTTP streaming typically doesn't need auth
    val enableAuth: Boolean = false // Legacy property for backward compatibility
) {
    
    companion object {
        private val validator = InputValidator()
        
        /**
         * Create a default StreamConfig with sensible defaults.
         * Uses auto-detected IP and standard HTTP streaming settings.
         */
        fun createDefault(context: android.content.Context? = null): StreamConfig {
            val defaultServerUrl = context?.let { 
                NetworkUtil.getBestIpForStreaming(it) 
            } ?: NetworkUtil.getLocalIPAddress() ?: "192.168.1.100"
            
            // Ensure the server URL is clean (no protocol prefix)
            val cleanServerUrl = if (defaultServerUrl.startsWith("http://")) {
                defaultServerUrl.removePrefix("http://")
            } else {
                defaultServerUrl
            }
            
            return StreamConfig(
                serverUrl = cleanServerUrl,
                username = "admin",
                password = "Password123!",
                port = 8080,
                streamPath = "/stream",
                quality = StreamQuality.MEDIUM,
                enableAudio = true,
                maxBitrate = 2000000,
                keyFrameInterval = 15,
                useAuthentication = false
            )
        }
        
        /**
         * Create a validated StreamConfig instance.
         * @return Pair of StreamConfig and ValidationResult
         */
        fun createValidated(
            serverUrl: String,
            username: String,
            password: String,
            port: Int,
            streamPath: String,
            quality: StreamQuality,
            enableAudio: Boolean,
            maxBitrate: Int,
            keyFrameInterval: Int,
            useAuthentication: Boolean
        ): Pair<StreamConfig?, ValidationResult> {
            val config = StreamConfig(
                serverUrl = serverUrl.trim(),
                username = username.trim(),
                password = password,
                port = port,
                streamPath = streamPath.trim(),
                quality = quality,
                enableAudio = enableAudio,
                maxBitrate = maxBitrate,
                keyFrameInterval = keyFrameInterval,
                useAuthentication = useAuthentication
            )
            
            val validation = config.validate()
            return if (validation.isValid) {
                Pair(config, validation)
            } else {
                Pair(null, validation)
            }
        }
    }
    
    /**
     * Generate the complete HTTP streaming URL
     * @return HTTP URL for streaming endpoint
     */
    fun generateStreamingUrl(): String {
        return if (useAuthentication) {
            "http://$username:$password@$serverUrl:$port$streamPath"
        } else {
            "http://$serverUrl:$port$streamPath"
        }
    }
    
    /**
     * Generate a display-friendly URL without credentials
     * @return HTTP URL without embedded credentials
     */
    fun generateDisplayUrl(): String {
        return "http://$serverUrl:$port$streamPath"
    }
    
    /**
     * Create a copy with dynamic server IP detection
     */
    fun withDynamicServerIP(): StreamConfig {
        val currentIp = NetworkUtil.getLocalIPAddress() ?: serverUrl
        return copy(serverUrl = currentIp)
    }
    
    /**
     * Validate the current configuration
     */
    fun validate(): ValidationResult {
        // Create full HTTP URL for validation
        val fullUrl = if (serverUrl.startsWith("http://")) {
            serverUrl
        } else {
            "http://$serverUrl"
        }
        
        val urlValidation = validator.validateHttpUrl(fullUrl)
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
        
        if (streamPath.isBlank()) {
            return ValidationResult(false, "Stream path cannot be empty")
        }
        
        if (validator.containsMaliciousContent(streamPath)) {
            return ValidationResult(false, "Stream path contains potentially dangerous content")
        }
        
        if (maxBitrate < 100000 || maxBitrate > 10000000) {
            return ValidationResult(false, "Bitrate must be between 100 Kbps and 10 Mbps")
        }
        
        if (keyFrameInterval < 1 || keyFrameInterval > 60) {
            return ValidationResult(false, "Key frame interval must be between 1 and 60 seconds")
        }
        
        return ValidationResult(true)
    }
    
    /**
     * Convert to log-safe string (hides sensitive information)
     */
    fun toLogSafeString(): String {
        return "StreamConfig(serverUrl='$serverUrl', port=$port, streamPath='$streamPath', " +
                "quality=$quality, enableAudio=$enableAudio, maxBitrate=$maxBitrate, " +
                "keyFrameInterval=$keyFrameInterval, useAuthentication=$useAuthentication, " +
                "username='${if (username.isNotBlank()) "***" else ""}', " +
                "password='${if (password.isNotBlank()) "***" else ""}')"
    }
}