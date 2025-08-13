package com.example.catchmestreaming.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class SecurePreferencesInstrumentedTest {
    
    private lateinit var securePreferences: SecurePreferences
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        securePreferences = SecurePreferences(context)
        // Clean up any existing preferences
        securePreferences.clear()
    }
    
    @After
    fun tearDown() {
        securePreferences.clear()
    }
    
    @Test
    fun shouldStoreAndRetrieveStringValues() {
        // Given
        val key = "test_string_key"
        val value = "test_string_value"
        
        // When
        securePreferences.putString(key, value)
        val retrieved = securePreferences.getString(key, null)
        
        // Then
        assertEquals("Should retrieve stored string", value, retrieved)
    }
    
    @Test
    fun shouldStoreAndRetrieveIntValues() {
        // Given
        val key = "test_int_key"
        val value = 12345
        
        // When
        securePreferences.putInt(key, value)
        val retrieved = securePreferences.getInt(key, -1)
        
        // Then
        assertEquals("Should retrieve stored int", value, retrieved)
    }
    
    @Test
    fun shouldStoreAndRetrieveBooleanValues() {
        // Given
        val key = "test_boolean_key"
        val value = true
        
        // When
        securePreferences.putBoolean(key, value)
        val retrieved = securePreferences.getBoolean(key, false)
        
        // Then
        assertEquals("Should retrieve stored boolean", value, retrieved)
    }
    
    @Test
    fun shouldStoreAndRetrieveFloatValues() {
        // Given
        val key = "test_float_key"
        val value = 3.14159f
        
        // When
        securePreferences.putFloat(key, value)
        val retrieved = securePreferences.getFloat(key, 0f)
        
        // Then
        assertEquals("Should retrieve stored float", value, retrieved, 0.00001f)
    }
    
    @Test
    fun shouldStoreAndRetrieveLongValues() {
        // Given
        val key = "test_long_key"
        val value = 9876543210L
        
        // When
        securePreferences.putLong(key, value)
        val retrieved = securePreferences.getLong(key, 0L)
        
        // Then
        assertEquals("Should retrieve stored long", value, retrieved)
    }
    
    @Test
    fun shouldReturnDefaultWhenKeyNotExists() {
        // Given
        val nonExistentKey = "does_not_exist"
        val defaultValue = "default_value"
        
        // When
        val retrieved = securePreferences.getString(nonExistentKey, defaultValue)
        
        // Then
        assertEquals("Should return default value", defaultValue, retrieved)
    }
    
    @Test
    fun shouldRemoveKeysSuccessfully() {
        // Given
        val key = "removable_key"
        val value = "removable_value"
        securePreferences.putString(key, value)
        
        // Verify it exists
        assertEquals("Value should exist", value, securePreferences.getString(key, null))
        
        // When
        securePreferences.remove(key)
        
        // Then
        assertNull("Value should be removed", securePreferences.getString(key, null))
    }
    
    @Test
    fun shouldCheckKeyExistence() {
        // Given
        val key = "existence_key"
        val value = "some_value"
        
        // When - Key doesn't exist
        assertFalse("Should not contain non-existent key", securePreferences.contains(key))
        
        // When - Store key
        securePreferences.putString(key, value)
        
        // Then
        assertTrue("Should contain existing key", securePreferences.contains(key))
        
        // When - Remove key
        securePreferences.remove(key)
        
        // Then
        assertFalse("Should not contain removed key", securePreferences.contains(key))
    }
    
    @Test
    fun shouldClearAllPreferences() {
        // Given
        securePreferences.putString("key1", "value1")
        securePreferences.putInt("key2", 42)
        securePreferences.putBoolean("key3", true)
        
        // Verify they exist
        assertEquals("String should exist", "value1", securePreferences.getString("key1", null))
        assertEquals("Int should exist", 42, securePreferences.getInt("key2", 0))
        assertTrue("Boolean should exist", securePreferences.getBoolean("key3", false))
        
        // When
        securePreferences.clear()
        
        // Then
        assertNull("String should be cleared", securePreferences.getString("key1", null))
        assertEquals("Int should be cleared", 0, securePreferences.getInt("key2", 0))
        assertFalse("Boolean should be cleared", securePreferences.getBoolean("key3", false))
    }
    
    @Test
    fun shouldSanitizeKeys() {
        // Given - key with dangerous characters
        val maliciousKey = "key<script>alert('xss')</script>"
        val value = "test_value"
        
        // When
        securePreferences.putString(maliciousKey, value)
        val retrieved = securePreferences.getString(maliciousKey, null)
        
        // Then - Should still work (key gets sanitized internally)
        assertEquals("Should store and retrieve with sanitized key", value, retrieved)
    }
    
    @Test
    fun shouldSanitizeStringValues() {
        // Given
        val key = "sanitize_test"
        val maliciousValue = "value<script>alert('xss')</script>"
        
        // When
        securePreferences.putString(key, maliciousValue)
        val retrieved = securePreferences.getString(key, null)
        
        // Then - Dangerous content should be sanitized
        assertNotNull("Should retrieve sanitized value", retrieved)
        assertFalse("Should not contain script tags", retrieved!!.contains("<script>"))
    }
    
    @Test
    fun shouldHandleVideoQualityEnums() {
        // Given
        val quality720p = VideoQuality.HD_720P
        val quality1080p = VideoQuality.FHD_1080P
        
        // When - Store 720p
        securePreferences.putVideoQuality(quality720p)
        var retrieved = securePreferences.getVideoQuality()
        
        // Then
        assertEquals("Should retrieve 720p quality", quality720p, retrieved)
        
        // When - Store 1080p
        securePreferences.putVideoQuality(quality1080p)
        retrieved = securePreferences.getVideoQuality()
        
        // Then
        assertEquals("Should retrieve 1080p quality", quality1080p, retrieved)
    }
    
    @Test
    fun shouldValidateStreamingPorts() {
        // Given
        val validPort = 8554
        val invalidPort = 99999
        
        // When - Valid port
        securePreferences.putStreamingPort(validPort)
        val retrievedValidPort = securePreferences.getStreamingPort()
        
        // Then
        assertEquals("Should store valid port", validPort, retrievedValidPort)
        
        // When - Invalid port (should throw exception)
        var exceptionThrown = false
        try {
            securePreferences.putStreamingPort(invalidPort)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
            assertTrue("Should contain port validation error", 
                e.message?.contains("Invalid port") == true)
        }
        
        // Then
        assertTrue("Should throw exception for invalid port", exceptionThrown)
    }
    
    @Test
    fun shouldValidateIPAddresses() {
        // Given
        val validIP = "192.168.1.100"
        val invalidIP = "999.999.999.999"
        
        // When - Valid IP
        securePreferences.putServerIP(validIP)
        val retrievedValidIP = securePreferences.getServerIP()
        
        // Then
        assertEquals("Should store valid IP", validIP, retrievedValidIP)
        
        // When - Invalid IP (should throw exception)
        var exceptionThrown = false
        try {
            securePreferences.putServerIP(invalidIP)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
            assertTrue("Should contain IP validation error", 
                e.message?.contains("Invalid IP address") == true)
        }
        
        // Then
        assertTrue("Should throw exception for invalid IP", exceptionThrown)
    }
    
    @Test
    fun shouldHandleAppSettingsCorrectly() {
        // Given
        val autoStart = true
        val keepScreenOn = false
        
        // When
        securePreferences.putAutoStartStreaming(autoStart)
        securePreferences.putKeepScreenOn(keepScreenOn)
        
        // Then
        assertEquals("Should store auto start setting", autoStart, securePreferences.getAutoStartStreaming())
        assertEquals("Should store keep screen on setting", keepScreenOn, securePreferences.getKeepScreenOn())
    }
}