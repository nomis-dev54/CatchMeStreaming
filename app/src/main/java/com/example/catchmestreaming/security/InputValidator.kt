package com.example.catchmestreaming.security

import java.net.URI
import java.util.regex.Pattern

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

class InputValidator {
    
    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_INPUT_LENGTH = 1000
        
        // Regex patterns for validation
        private val IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        
        private val MALICIOUS_PATTERNS = listOf(
            Pattern.compile("(DROP|DELETE|UNION|SELECT|INSERT|UPDATE|ALTER|CREATE)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\.\\./|\\.\\\\)", Pattern.CASE_INSENSITIVE), // Path traversal
            Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE), // XSS
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onload|onerror|onclick", Pattern.CASE_INSENSITIVE)
        )
        
        private val DANGEROUS_CHARS = listOf('<', '>', '"', '&')
        private val CONTROL_CHARS = listOf('\r', '\n', '\t')
    }
    
    fun validateHttpUrl(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult(false, "URL cannot be empty")
        }
        
        if (url.length > MAX_INPUT_LENGTH) {
            return ValidationResult(false, "URL too long")
        }
        
        // Check for malicious patterns
        if (containsMaliciousContent(url)) {
            return ValidationResult(false, "Potentially malicious content detected")
        }
        
        return try {
            val uri = URI(url)
            
            // Must be HTTP protocol (or allow no scheme for IP addresses)
            if (uri.scheme != null && uri.scheme != "http" && uri.scheme != "https") {
                return ValidationResult(false, "Must use HTTP protocol")
            }
            
            // Validate host
            val host = uri.host ?: return ValidationResult(false, "Invalid host")
            if (host.isBlank()) {
                return ValidationResult(false, "Host cannot be empty")
            }
            
            // Validate port
            val port = uri.port
            if (port != -1) {
                val portValidation = validatePort(port)
                if (!portValidation.isValid) {
                    return portValidation
                }
            }
            
            // Check for path traversal
            val path = uri.path ?: ""
            if (path.contains("../") || path.contains("..\\")) {
                return ValidationResult(false, "Path traversal detected")
            }
            
            ValidationResult(true)
        } catch (e: Exception) {
            ValidationResult(false, "Invalid URL format")
        }
    }
    
    fun sanitizeUsername(username: String): String {
        return username.trim()
            .take(MAX_INPUT_LENGTH)
            .let { sanitizeInput(it) }
            .filter { char ->
                char.isLetterOrDigit() || char in listOf('@', '.', '_', '-')
            }
    }
    
    fun validatePassword(password: String): ValidationResult {
        if (password.isBlank()) {
            return ValidationResult(false, "Password cannot be empty")
        }
        
        if (password.length < MIN_PASSWORD_LENGTH) {
            return ValidationResult(false, "Password must be at least $MIN_PASSWORD_LENGTH characters")
        }
        
        if (password.length > MAX_INPUT_LENGTH) {
            return ValidationResult(false, "Password too long")
        }
        
        if (!password.any { it.isLowerCase() }) {
            return ValidationResult(false, "Password must contain lowercase letters")
        }
        
        if (!password.any { it.isUpperCase() }) {
            return ValidationResult(false, "Password must contain uppercase letters")
        }
        
        if (!password.any { it.isDigit() }) {
            return ValidationResult(false, "Password must contain numbers")
        }
        
        val specialChars = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        if (!password.any { it in specialChars }) {
            return ValidationResult(false, "Password must contain special characters")
        }
        
        return ValidationResult(true)
    }
    
    fun sanitizeInput(input: String): String {
        var sanitized = input.trim()
            .take(MAX_INPUT_LENGTH)
            .filterNot { it in DANGEROUS_CHARS }
            .filterNot { it in CONTROL_CHARS }
            .replace(Regex("&[a-zA-Z0-9#]+;"), "") // Remove HTML entities
        
        // Remove SQL keywords
        sanitized = sanitized.replace(Regex("(DROP|DELETE|UNION|SELECT|INSERT|UPDATE|ALTER|CREATE)", RegexOption.IGNORE_CASE), "")
        
        return sanitized
    }
    
    fun validateIPAddress(ip: String): ValidationResult {
        if (ip.isBlank()) {
            return ValidationResult(false, "IP address cannot be empty")
        }
        
        return if (IP_PATTERN.matcher(ip).matches()) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Invalid IP address format")
        }
    }
    
    fun validatePort(port: Int): ValidationResult {
        return if (port in 1..65535) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Invalid port number (must be 1-65535)")
        }
    }
    
    fun validateNetworkConfig(host: String, port: Int): ValidationResult {
        // First validate IP address
        val ipValidation = validateIPAddress(host)
        if (!ipValidation.isValid) {
            return ipValidation
        }
        
        // Then validate port
        val portValidation = validatePort(port)
        if (!portValidation.isValid) {
            return portValidation
        }
        
        return ValidationResult(true)
    }
    
    fun containsMaliciousContent(input: String): Boolean {
        return MALICIOUS_PATTERNS.any { pattern ->
            pattern.matcher(input).find()
        }
    }
    
    fun validateDirectoryPath(path: String): ValidationResult {
        if (path.isBlank()) {
            return ValidationResult(false, "Directory path cannot be empty")
        }
        
        if (path.length > MAX_INPUT_LENGTH) {
            return ValidationResult(false, "Directory path too long")
        }
        
        // Check for malicious patterns
        if (containsMaliciousContent(path)) {
            return ValidationResult(false, "Potentially malicious content detected")
        }
        
        // Check for path traversal
        if (path.contains("../") || path.contains("..\\")) {
            return ValidationResult(false, "Path traversal detected")
        }
        
        // Validate against dangerous characters for file paths
        val dangerousPathChars = listOf('<', '>', '"', '|', '?', '*', '\u0000')
        if (path.any { it in dangerousPathChars }) {
            return ValidationResult(false, "Invalid characters in path")
        }
        
        return ValidationResult(true)
    }
    
    fun sanitizeDirectoryPath(path: String): String {
        return path.trim()
            .take(MAX_INPUT_LENGTH)
            .replace(Regex("[<>\"\\|\\?\\*\\u0000]"), "")
            .replace(Regex("\\.\\./|\\.\\.\\\\/"), "")
            .let { sanitizeInput(it) }
    }
    
    fun validateFilename(filename: String): ValidationResult {
        if (filename.isBlank()) {
            return ValidationResult(false, "Filename cannot be empty")
        }
        
        if (filename.length > 255) { // Max filename length on most filesystems
            return ValidationResult(false, "Filename too long (max 255 characters)")
        }
        
        // Check for malicious patterns
        if (containsMaliciousContent(filename)) {
            return ValidationResult(false, "Potentially malicious content detected")
        }
        
        // Windows reserved names
        val reservedNames = listOf("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", 
            "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", 
            "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9")
        if (filename.uppercase() in reservedNames) {
            return ValidationResult(false, "Reserved filename")
        }
        
        // Invalid filename characters
        val invalidChars = listOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
        if (filename.any { it in invalidChars }) {
            return ValidationResult(false, "Invalid characters in filename")
        }
        
        return ValidationResult(true)
    }
    
    fun sanitizeFilename(filename: String): String {
        return filename.trim()
            .take(255)
            .replace(Regex("[<>:\"/\\\\|\\?\\*]"), "_")
            .let { sanitizeInput(it) }
            .let { if (it.isBlank()) "file" else it }
    }
    
    fun sanitizeForLogging(input: String): String {
        // For secure logging - mask sensitive information
        return input.take(20)
            .map { char ->
                when {
                    char.isLetterOrDigit() -> char
                    char in listOf('.', ':', '/', '-', '_') -> char
                    else -> '*'
                }
            }
            .joinToString("")
            .let { if (input.length > 20) "$it..." else it }
    }
    
    // Legacy method names for backward compatibility with tests
    fun validateRTSPUrl(url: String): ValidationResult {
        return validateHttpUrl(url)
    }
    
    fun validateStreamingUrl(url: String): ValidationResult {
        return validateHttpUrl(url)
    }
    
    fun validateServerUrl(serverUrl: String): ValidationResult {
        if (serverUrl.isBlank()) {
            return ValidationResult(false, "Server URL cannot be empty")
        }
        
        if (serverUrl.length > MAX_INPUT_LENGTH) {
            return ValidationResult(false, "Server URL too long")
        }
        
        // Check for dangerous characters
        if (serverUrl.any { it in DANGEROUS_CHARS }) {
            return ValidationResult(false, "Invalid characters in server URL")
        }
        
        // Validate IP address format
        if (!IP_PATTERN.matcher(serverUrl).matches()) {
            // Allow hostnames too - basic hostname validation
            val hostnamePattern = Pattern.compile(
                "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?))*$"
            )
            if (!hostnamePattern.matcher(serverUrl).matches()) {
                return ValidationResult(false, "Invalid server URL format")
            }
        }
        
        return ValidationResult(true)
    }
    
    fun validateStreamPath(streamPath: String): ValidationResult {
        if (streamPath.isBlank()) {
            return ValidationResult(false, "Stream path cannot be empty")
        }
        
        if (streamPath.length > MAX_INPUT_LENGTH) {
            return ValidationResult(false, "Stream path too long")
        }
        
        // Check for path traversal
        if (streamPath.contains("../") || streamPath.contains("..\\")) {
            return ValidationResult(false, "Path traversal not allowed")
        }
        
        // Check for dangerous characters
        if (streamPath.any { it in DANGEROUS_CHARS }) {
            return ValidationResult(false, "Invalid characters in stream path")
        }
        
        // Must start with /
        if (!streamPath.startsWith("/")) {
            return ValidationResult(false, "Stream path must start with /")
        }
        
        return ValidationResult(true)
    }
}