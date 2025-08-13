package com.example.catchmestreaming.security

import org.junit.Test
import org.junit.Assert.*

class InputValidatorTest {
    
    private val validator = InputValidator()
    
    @Test
    fun shouldValidateCorrectRTSPUrls() {
        // Given
        val validUrls = listOf(
            "rtsp://192.168.1.100:8554/stream",
            "rtsp://example.com:554/live",
            "rtsp://10.0.0.1:1234/video",
            "rtsp://localhost:8554/test"
        )
        
        // When & Then
        validUrls.forEach { url ->
            val result = validator.validateRTSPUrl(url)
            assertTrue("URL should be valid: $url", result.isValid)
            assertNull("No error message for valid URL: $url", result.errorMessage)
        }
    }
    
    @Test
    fun shouldRejectInvalidRTSPUrls() {
        // Given
        val invalidUrls = mapOf(
            "http://example.com" to "Must use RTSP protocol",
            "rtsp://" to "Invalid URL format",
            "rtsp://example.com:99999/stream" to "Invalid port number",
            "rtsp://example.com:-1/stream" to "Invalid port number",
            "not-a-url" to "Invalid URL format",
            "" to "URL cannot be empty",
            "rtsp://example.com/../../../etc/passwd" to "Path traversal detected",
            "rtsp://example.com/stream?param=<script>" to "Potentially malicious content detected"
        )
        
        // When & Then
        invalidUrls.forEach { (url, expectedError) ->
            val result = validator.validateRTSPUrl(url)
            assertFalse("URL should be invalid: $url", result.isValid)
            assertNotNull("Should have error message for: $url", result.errorMessage)
        }
    }
    
    @Test
    fun shouldSanitizeUsernameInput() {
        // Given
        val testCases = mapOf(
            "validuser" to "validuser",
            "  spaced  " to "spaced",
            "user<script>" to "userscript",
            "user\"with'quotes" to "userwithquotes",
            "user&amp;entity" to "userampentity",
            "user\n\r\twith\tcontrol" to "userwithcontrol",
            "user@domain.com" to "user@domain.com", // @ should be allowed
            "user_name-123" to "user_name-123" // underscores and hyphens allowed
        )
        
        // When & Then
        testCases.forEach { (input, expected) ->
            val result = validator.sanitizeUsername(input)
            assertEquals("Username sanitization failed for: $input", expected, result)
        }
    }
    
    @Test
    fun shouldValidatePasswordStrength() {
        // Given
        val validPasswords = listOf(
            "StrongPass123!",
            "AnotherGood1$",
            "Complex2023#Pass"
        )
        
        val invalidPasswords = mapOf(
            "weak" to "Password must be at least 8 characters",
            "alllowercase123" to "Password must contain uppercase letters",
            "ALLUPPERCASE123" to "Password must contain lowercase letters",
            "NoNumbers!" to "Password must contain numbers",
            "NoSpecialChars123" to "Password must contain special characters",
            "" to "Password cannot be empty"
        )
        
        // When & Then - Valid passwords
        validPasswords.forEach { password ->
            val result = validator.validatePassword(password)
            assertTrue("Password should be valid: $password", result.isValid)
            assertNull("No error for valid password: $password", result.errorMessage)
        }
        
        // When & Then - Invalid passwords
        invalidPasswords.forEach { (password, expectedError) ->
            val result = validator.validatePassword(password)
            assertFalse("Password should be invalid: $password", result.isValid)
            assertTrue("Error should contain expected message for: $password", 
                result.errorMessage?.contains(expectedError.split(" ").take(3).joinToString(" ")) == true)
        }
    }
    
    @Test
    fun shouldSanitizeGeneralInput() {
        // Given
        val testCases = mapOf(
            "normal text" to "normal text",
            "<script>alert('xss')</script>" to "scriptalert('xss')/script",
            "text\"with'quotes" to "textwithquotes",
            "text&lt;escaped&gt;" to "textltescapedgt",
            "line1\nline2\rline3\tline4" to "line1line2line3line4",
            "  leading and trailing  " to "leading and trailing"
        )
        
        // When & Then
        testCases.forEach { (input, expected) ->
            val result = validator.sanitizeInput(input)
            assertEquals("Input sanitization failed for: $input", expected, result)
        }
    }
    
    @Test
    fun shouldValidateIPAddresses() {
        // Given
        val validIPs = listOf(
            "192.168.1.1",
            "10.0.0.1",
            "127.0.0.1",
            "255.255.255.255"
        )
        
        val invalidIPs = listOf(
            "256.1.1.1",
            "192.168.1",
            "192.168.1.1.1",
            "not.an.ip.address",
            ""
        )
        
        // When & Then
        validIPs.forEach { ip ->
            val result = validator.validateIPAddress(ip)
            assertTrue("IP should be valid: $ip", result.isValid)
        }
        
        invalidIPs.forEach { ip ->
            val result = validator.validateIPAddress(ip)
            assertFalse("IP should be invalid: $ip", result.isValid)
        }
    }
    
    @Test
    fun shouldValidatePortNumbers() {
        // Given
        val validPorts = listOf(1, 80, 443, 554, 8554, 65535)
        val invalidPorts = listOf(0, -1, 65536, 100000)
        
        // When & Then
        validPorts.forEach { port ->
            val result = validator.validatePort(port)
            assertTrue("Port should be valid: $port", result.isValid)
        }
        
        invalidPorts.forEach { port ->
            val result = validator.validatePort(port)
            assertFalse("Port should be invalid: $port", result.isValid)
        }
    }
    
    @Test
    fun shouldDetectSQLInjectionAttempts() {
        // Given
        val maliciousInputs = listOf(
            "'; DROP TABLE users; --",
            "1' OR '1'='1",
            "admin'--",
            "1; DELETE FROM users",
            "UNION SELECT * FROM passwords"
        )
        
        // When & Then
        maliciousInputs.forEach { input ->
            val result = validator.sanitizeInput(input)
            assertFalse("Should not contain SQL keywords: $input", 
                result.contains(Regex("(DROP|DELETE|UNION|SELECT)", RegexOption.IGNORE_CASE)))
        }
    }
}