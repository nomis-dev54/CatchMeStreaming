package com.example.catchmestreaming.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecureStorage(private val context: Context) {
    
    companion object {
        private const val KEY_ALIAS = "catchme_streaming_key"
        private const val PREFERENCES_FILE = "catchme_secure_prefs"
        private const val USERNAME_KEY = "rtsp_username"
        private const val PASSWORD_KEY = "rtsp_password"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
    
    private val encryptedPreferences by lazy {
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
            throw SecurityException("Failed to initialize secure storage", e)
        }
    }
    
    fun storeCredentials(username: String, password: String): Result<Unit> {
        return try {
            // Validate inputs
            if (username.isBlank()) {
                return Result.failure(IllegalArgumentException("Username cannot be empty"))
            }
            if (password.isBlank()) {
                return Result.failure(IllegalArgumentException("Password cannot be empty"))
            }
            
            // Sanitize inputs
            val sanitizedUsername = sanitizeInput(username)
            val sanitizedPassword = sanitizeInput(password)
            
            // Store encrypted credentials
            encryptedPreferences.edit()
                .putString(USERNAME_KEY, sanitizedUsername)
                .putString(PASSWORD_KEY, sanitizedPassword)
                .apply()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SecurityException("Failed to store credentials", e))
        }
    }
    
    fun retrieveCredentials(): Result<Pair<String, String>> {
        return try {
            val username = encryptedPreferences.getString(USERNAME_KEY, null)
            val password = encryptedPreferences.getString(PASSWORD_KEY, null)
            
            if (username != null && password != null) {
                Result.success(Pair(username, password))
            } else {
                Result.failure(IllegalStateException("No credentials found"))
            }
        } catch (e: Exception) {
            Result.failure(SecurityException("Failed to retrieve credentials", e))
        }
    }
    
    fun deleteCredentials(): Result<Unit> {
        return try {
            encryptedPreferences.edit()
                .remove(USERNAME_KEY)
                .remove(PASSWORD_KEY)
                .apply()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SecurityException("Failed to delete credentials", e))
        }
    }
    
    fun hasStoredCredentials(): Boolean {
        return try {
            encryptedPreferences.contains(USERNAME_KEY) && 
            encryptedPreferences.contains(PASSWORD_KEY)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun sanitizeInput(input: String): String {
        // Remove potentially dangerous characters and trim whitespace
        return input.trim()
            .replace(Regex("[<>\"'&]"), "") // Remove HTML/XML special characters
            .replace(Regex("[\r\n\t]"), "") // Remove control characters
    }
    
    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } else {
            generateSecretKey()
        }
    }
}