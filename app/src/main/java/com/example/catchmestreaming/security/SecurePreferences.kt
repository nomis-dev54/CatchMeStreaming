package com.example.catchmestreaming.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {
    
    companion object {
        private const val PREFERENCES_FILE = "catchme_settings_prefs"
        const val MAX_KEY_LENGTH = 255
        const val MAX_VALUE_LENGTH = 10000
    }
    
    private val inputValidator = InputValidator()
    
    private val encryptedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                PREFERENCES_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            throw SecurityException("Failed to initialize secure preferences", e)
        }
    }
    
    fun putString(key: String, value: String?) {
        val sanitizedKey = sanitizeKey(key)
        val sanitizedValue = value?.let { sanitizeValue(it) }
        
        encryptedPreferences.edit()
            .putString(sanitizedKey, sanitizedValue)
            .apply()
    }
    
    fun getString(key: String, defaultValue: String?): String? {
        val sanitizedKey = sanitizeKey(key)
        return try {
            encryptedPreferences.getString(sanitizedKey, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }
    
    fun putInt(key: String, value: Int) {
        val sanitizedKey = sanitizeKey(key)
        encryptedPreferences.edit()
            .putInt(sanitizedKey, value)
            .apply()
    }
    
    fun getInt(key: String, defaultValue: Int): Int {
        val sanitizedKey = sanitizeKey(key)
        return try {
            encryptedPreferences.getInt(sanitizedKey, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }
    
    fun putBoolean(key: String, value: Boolean) {
        val sanitizedKey = sanitizeKey(key)
        encryptedPreferences.edit()
            .putBoolean(sanitizedKey, value)
            .apply()
    }
    
    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val sanitizedKey = sanitizeKey(key)
        return try {
            encryptedPreferences.getBoolean(sanitizedKey, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }
    
    fun putFloat(key: String, value: Float) {
        val sanitizedKey = sanitizeKey(key)
        encryptedPreferences.edit()
            .putFloat(sanitizedKey, value)
            .apply()
    }
    
    fun getFloat(key: String, defaultValue: Float): Float {
        val sanitizedKey = sanitizeKey(key)
        return try {
            encryptedPreferences.getFloat(sanitizedKey, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }
    
    fun putLong(key: String, value: Long) {
        val sanitizedKey = sanitizeKey(key)
        encryptedPreferences.edit()
            .putLong(sanitizedKey, value)
            .apply()
    }
    
    fun getLong(key: String, defaultValue: Long): Long {
        val sanitizedKey = sanitizeKey(key)
        return try {
            encryptedPreferences.getLong(sanitizedKey, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }
    
    fun putStringSet(key: String, values: Set<String>?) {
        val sanitizedKey = sanitizeKey(key)
        val sanitizedValues = values?.map { sanitizeValue(it) }?.toSet()
        
        encryptedPreferences.edit()
            .putStringSet(sanitizedKey, sanitizedValues)
            .apply()
    }
    
    fun getStringSet(key: String, defaultValues: Set<String>?): Set<String>? {
        val sanitizedKey = sanitizeKey(key)
        return try {
            encryptedPreferences.getStringSet(sanitizedKey, defaultValues)
        } catch (e: Exception) {
            defaultValues
        }
    }
    
    fun remove(key: String) {
        val sanitizedKey = sanitizeKey(key)
        encryptedPreferences.edit()
            .remove(sanitizedKey)
            .apply()
    }
    
    fun clear() {
        encryptedPreferences.edit()
            .clear()
            .apply()
    }
    
    fun contains(key: String): Boolean {
        val sanitizedKey = sanitizeKey(key)
        return try {
            encryptedPreferences.contains(sanitizedKey)
        } catch (e: Exception) {
            false
        }
    }
    
    fun getAll(): Map<String, *> {
        return try {
            encryptedPreferences.all
        } catch (e: Exception) {
            emptyMap<String, Any>()
        }
    }
    
    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        encryptedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }
    
    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        encryptedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
    
    private fun sanitizeKey(key: String): String {
        if (key.isBlank()) {
            throw IllegalArgumentException("Key cannot be empty")
        }
        
        val sanitized = inputValidator.sanitizeInput(key)
            .take(MAX_KEY_LENGTH)
            .filter { char ->
                char.isLetterOrDigit() || char in listOf('_', '-', '.')
            }
        
        if (sanitized.isBlank()) {
            throw IllegalArgumentException("Key contains only invalid characters")
        }
        
        return sanitized
    }
    
    private fun sanitizeValue(value: String): String {
        return inputValidator.sanitizeInput(value)
            .take(MAX_VALUE_LENGTH)
    }
    
    // Settings-specific helper methods
    fun putVideoQuality(quality: VideoQuality) {
        putString("video_quality", quality.name)
    }
    
    fun getVideoQuality(defaultQuality: VideoQuality = VideoQuality.HD_720P): VideoQuality {
        val qualityName = getString("video_quality", defaultQuality.name)
        return try {
            VideoQuality.valueOf(qualityName ?: defaultQuality.name)
        } catch (e: IllegalArgumentException) {
            defaultQuality
        }
    }
    
    fun putStreamingPort(port: Int) {
        val validation = inputValidator.validatePort(port)
        if (!validation.isValid) {
            throw IllegalArgumentException("Invalid port: ${validation.errorMessage}")
        }
        putInt("streaming_port", port)
    }
    
    fun getStreamingPort(defaultPort: Int = 8554): Int {
        val port = getInt("streaming_port", defaultPort)
        val validation = inputValidator.validatePort(port)
        return if (validation.isValid) port else defaultPort
    }
    
    fun putServerIP(ip: String) {
        val validation = inputValidator.validateIPAddress(ip)
        if (!validation.isValid) {
            throw IllegalArgumentException("Invalid IP address: ${validation.errorMessage}")
        }
        putString("server_ip", ip)
    }
    
    fun getServerIP(defaultIP: String = "192.168.1.100"): String {
        val ip = getString("server_ip", defaultIP) ?: defaultIP
        val validation = inputValidator.validateIPAddress(ip)
        return if (validation.isValid) ip else defaultIP
    }
    
    fun putAutoStartStreaming(autoStart: Boolean) {
        putBoolean("auto_start_streaming", autoStart)
    }
    
    fun getAutoStartStreaming(defaultValue: Boolean = false): Boolean {
        return getBoolean("auto_start_streaming", defaultValue)
    }
    
    fun putKeepScreenOn(keepOn: Boolean) {
        putBoolean("keep_screen_on", keepOn)
    }
    
    fun getKeepScreenOn(defaultValue: Boolean = true): Boolean {
        return getBoolean("keep_screen_on", defaultValue)
    }
}

enum class VideoQuality(val displayName: String, val width: Int, val height: Int) {
    SD_480P("480p", 854, 480),
    HD_720P("720p", 1280, 720),
    FHD_1080P("1080p", 1920, 1080)
}