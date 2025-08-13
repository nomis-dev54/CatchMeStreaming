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
    
    fun validateRTSPUrl(url: String): ValidationResult {
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
            
            // Must be RTSP protocol
            if (uri.scheme != "rtsp") {
                return ValidationResult(false, "Must use RTSP protocol")
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
}