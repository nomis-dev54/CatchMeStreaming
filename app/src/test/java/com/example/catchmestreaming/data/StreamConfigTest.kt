package com.example.catchmestreaming.data

import org.junit.Test
import org.junit.Assert.*

/**
 * Test for StreamConfig default configuration functionality
 */
class StreamConfigTest {

    @Test
    fun createDefault_shouldReturnValidConfig() {
        // When creating a default config without context
        val defaultConfig = StreamConfig.createDefault()
        
        // Then it should have sensible defaults
        assertEquals(8080, defaultConfig.port)
        assertEquals("/stream", defaultConfig.streamPath)
        assertEquals("admin", defaultConfig.username)
        assertEquals("Password123!", defaultConfig.password)
        assertEquals(StreamQuality.MEDIUM, defaultConfig.quality)
        assertTrue(defaultConfig.enableAudio)
        assertEquals(2000000, defaultConfig.maxBitrate)
        assertEquals(15, defaultConfig.keyFrameInterval)
        assertTrue(defaultConfig.useAuthentication)
        assertNotNull(defaultConfig.serverUrl)
        assertTrue(defaultConfig.serverUrl.isNotEmpty())
    }

    @Test
    fun createDefault_shouldPassValidation() {
        // When creating a default config
        val defaultConfig = StreamConfig.createDefault()
        
        // Then it should pass validation
        val validationResult = defaultConfig.validate()
        if (!validationResult.isValid) {
            fail("Default config validation failed: ${validationResult.errorMessage}")
        }
        assertTrue("Default config should be valid", validationResult.isValid)
    }

    @Test
    fun createDefault_shouldGenerateValidStreamingUrl() {
        // When creating a default config
        val defaultConfig = StreamConfig.createDefault()
        
        // Then it should generate a valid HTTP streaming URL
        val streamingUrl = defaultConfig.generateStreamingUrl()
        assertTrue("Streaming URL should start with http://", streamingUrl.startsWith("http://"))
        assertTrue("Streaming URL should contain username", streamingUrl.contains("admin"))
        assertTrue("Streaming URL should contain port", streamingUrl.contains(":8080"))
        assertTrue("Streaming URL should contain stream path", streamingUrl.contains("/stream"))
    }

    @Test
    fun createDefault_shouldGenerateDisplayUrl() {
        // When creating a default config
        val defaultConfig = StreamConfig.createDefault()
        
        // Then it should generate a display URL without credentials
        val displayUrl = defaultConfig.generateDisplayUrl()
        assertTrue("Display URL should start with http://", displayUrl.startsWith("http://"))
        assertFalse("Display URL should not contain credentials", displayUrl.contains("admin"))
        assertFalse("Display URL should not contain password", displayUrl.contains("Password123!"))
        assertTrue("Display URL should contain port", displayUrl.contains(":8080"))
        assertTrue("Display URL should contain stream path", displayUrl.contains("/stream"))
    }

    @Test
    fun createDefault_withDynamicIP_shouldUpdateServerUrl() {
        // Given a default config
        val defaultConfig = StreamConfig.createDefault()
        val originalServerUrl = defaultConfig.serverUrl
        
        // When creating a config with dynamic IP
        val dynamicConfig = defaultConfig.withDynamicServerIP()
        
        // Then the server URL should be updated (or remain the same if no network available)
        assertNotNull(dynamicConfig.serverUrl)
        assertTrue(dynamicConfig.serverUrl.isNotEmpty())
        // Other properties should remain the same
        assertEquals(defaultConfig.port, dynamicConfig.port)
        assertEquals(defaultConfig.username, dynamicConfig.username)
        assertEquals(defaultConfig.quality, dynamicConfig.quality)
    }

    @Test
    fun createDefault_toLogSafeString_shouldHideSensitiveData() {
        // When creating a default config
        val defaultConfig = StreamConfig.createDefault()
        
        // Then the log safe string should hide sensitive data
        val logSafeString = defaultConfig.toLogSafeString()
        assertFalse("Log safe string should not contain actual password", 
                   logSafeString.contains("Password123!"))
        assertTrue("Log safe string should mask password", 
                  logSafeString.contains("password='***'"))
        assertTrue("Log safe string should contain other config info", 
                  logSafeString.contains("port=8080"))
    }
}