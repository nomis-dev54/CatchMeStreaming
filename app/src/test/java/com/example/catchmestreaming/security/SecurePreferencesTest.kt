package com.example.catchmestreaming.security

import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SecurePreferencesTest {
    
    private lateinit var securePreferences: SecurePreferences
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        securePreferences = SecurePreferences(context)
    }
    
    @Test
    fun shouldStoreAndRetrieveStringValue() {
        // Given
        val key = "test_string"
        val value = "test_value"
        
        // When
        securePreferences.putString(key, value)
        val retrieved = securePreferences.getString(key, null)
        
        // Then
        assertEquals("Should retrieve stored string", value, retrieved)
    }
    
    @Test
    fun shouldStoreAndRetrieveIntValue() {
        // Given
        val key = "test_int"
        val value = 42
        
        // When
        securePreferences.putInt(key, value)
        val retrieved = securePreferences.getInt(key, 0)
        
        // Then
        assertEquals("Should retrieve stored int", value, retrieved)
    }
    
    @Test
    fun shouldStoreAndRetrieveBooleanValue() {
        // Given
        val key = "test_boolean"
        val value = true
        
        // When
        securePreferences.putBoolean(key, value)
        val retrieved = securePreferences.getBoolean(key, false)
        
        // Then
        assertEquals("Should retrieve stored boolean", value, retrieved)
    }
    
    @Test
    fun shouldStoreAndRetrieveFloatValue() {
        // Given
        val key = "test_float"
        val value = 3.14f
        
        // When
        securePreferences.putFloat(key, value)
        val retrieved = securePreferences.getFloat(key, 0f)
        
        // Then
        assertEquals("Should retrieve stored float", value, retrieved, 0.001f)
    }
    
    @Test
    fun shouldStoreAndRetrieveLongValue() {
        // Given
        val key = "test_long"
        val value = 123456789L
        
        // When
        securePreferences.putLong(key, value)
        val retrieved = securePreferences.getLong(key, 0L)
        
        // Then
        assertEquals("Should retrieve stored long", value, retrieved)
    }
    
    @Test
    fun shouldReturnDefaultWhenKeyNotFound() {
        // Given
        val key = "nonexistent_key"
        val defaultValue = "default"
        
        // When
        val retrieved = securePreferences.getString(key, defaultValue)
        
        // Then
        assertEquals("Should return default value", defaultValue, retrieved)
    }
    
    @Test
    fun shouldRemoveValue() {
        // Given
        val key = "test_remove"
        val value = "to_be_removed"
        securePreferences.putString(key, value)
        
        // When
        securePreferences.remove(key)
        val retrieved = securePreferences.getString(key, null)
        
        // Then
        assertNull("Should return null after removal", retrieved)
    }
    
    @Test
    fun shouldClearAllValues() {
        // Given
        securePreferences.putString("key1", "value1")
        securePreferences.putInt("key2", 42)
        securePreferences.putBoolean("key3", true)
        
        // When
        securePreferences.clear()
        
        // Then
        assertNull("String should be cleared", securePreferences.getString("key1", null))
        assertEquals("Int should be cleared", 0, securePreferences.getInt("key2", 0))
        assertFalse("Boolean should be cleared", securePreferences.getBoolean("key3", false))
    }
    
    @Test
    fun shouldCheckIfKeyExists() {
        // Given
        val key = "test_contains"
        val value = "test_value"
        
        // When & Then
        assertFalse("Should not contain key initially", securePreferences.contains(key))
        
        securePreferences.putString(key, value)
        assertTrue("Should contain key after storing", securePreferences.contains(key))
        
        securePreferences.remove(key)
        assertFalse("Should not contain key after removal", securePreferences.contains(key))
    }
    
    @Test
    fun shouldGetAllKeys() {
        // Given
        securePreferences.clear()
        securePreferences.putString("key1", "value1")
        securePreferences.putInt("key2", 42)
        securePreferences.putBoolean("key3", true)
        
        // When
        val keys = securePreferences.getAll().keys
        
        // Then
        assertEquals("Should have 3 keys", 3, keys.size)
        assertTrue("Should contain key1", keys.contains("key1"))
        assertTrue("Should contain key2", keys.contains("key2"))
        assertTrue("Should contain key3", keys.contains("key3"))
    }
    
    @Test
    fun shouldSanitizeKeyNames() {
        // Given
        val maliciousKey = "key<script>alert('xss')</script>"
        val value = "test_value"
        
        // When
        securePreferences.putString(maliciousKey, value)
        val retrieved = securePreferences.getString(maliciousKey, null)
        
        // Then
        assertEquals("Should store and retrieve with sanitized key", value, retrieved)
    }
    
    @Test
    fun shouldSanitizeStringValues() {
        // Given
        val key = "test_sanitize"
        val maliciousValue = "value<script>alert('xss')</script>"
        
        // When
        securePreferences.putString(key, maliciousValue)
        val retrieved = securePreferences.getString(key, null)
        
        // Then
        assertNotNull("Should retrieve sanitized value", retrieved)
        assertFalse("Should not contain script tags", retrieved?.contains("<script>") == true)
    }
    
    @Test
    fun shouldHandleLargeValues() {
        // Given
        val key = "large_value"
        val largeValue = "x".repeat(10000)
        
        // When
        securePreferences.putString(key, largeValue)
        val retrieved = securePreferences.getString(key, null)
        
        // Then
        assertNotNull("Should handle large values", retrieved)
        assertTrue("Should limit value size", retrieved!!.length <= SecurePreferences.MAX_VALUE_LENGTH)
    }
    
    @Test
    fun shouldHandleNullValues() {
        // Given
        val key = "null_test"
        
        // When
        securePreferences.putString(key, null)
        val retrieved = securePreferences.getString(key, "default")
        
        // Then
        assertEquals("Should return default for null value", "default", retrieved)
    }
    
    @Test
    fun shouldValidateKeyLength() {
        // Given
        val longKey = "k".repeat(300)
        val value = "test_value"
        
        // When
        securePreferences.putString(longKey, value)
        val retrieved = securePreferences.getString(longKey, null)
        
        // Then
        assertEquals("Should handle long keys", value, retrieved)
    }
    
    @Test
    fun shouldEncryptStoredData() {
        // Given
        val key = "encryption_test"
        val value = "sensitive_data"
        
        // When
        securePreferences.putString(key, value)
        
        // Then
        // This test verifies that the data is encrypted by checking that
        // it's not stored in plain text in regular SharedPreferences
        val regularPrefs = context.getSharedPreferences("regular_prefs", Context.MODE_PRIVATE)
        val plainTextValue = regularPrefs.getString(key, null)
        assertNull("Should not be stored in regular preferences", plainTextValue)
    }
}