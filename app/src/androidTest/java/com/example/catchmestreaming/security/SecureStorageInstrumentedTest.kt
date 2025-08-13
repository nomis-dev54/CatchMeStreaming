package com.example.catchmestreaming.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class SecureStorageInstrumentedTest {
    
    private lateinit var secureStorage: SecureStorage
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        secureStorage = SecureStorage(context)
        // Clean up any existing credentials
        secureStorage.deleteCredentials()
    }
    
    @After
    fun tearDown() {
        // Clean up test credentials
        secureStorage.deleteCredentials()
    }
    
    @Test
    fun shouldStoreAndRetrieveCredentialsUsingAndroidKeystore() {
        // Given
        val testUsername = "testuser123"
        val testPassword = "SecurePass123!"
        
        // When - Store credentials
        val storeResult = secureStorage.storeCredentials(testUsername, testPassword)
        
        // Then - Storage should succeed
        assertTrue("Credentials should be stored successfully", storeResult.isSuccess)
        
        // When - Retrieve credentials
        val retrieveResult = secureStorage.retrieveCredentials()
        
        // Then - Retrieval should succeed with correct values
        assertTrue("Credentials should be retrieved successfully", retrieveResult.isSuccess)
        
        val (retrievedUsername, retrievedPassword) = retrieveResult.getOrThrow()
        assertEquals("Username should match", testUsername, retrievedUsername)
        assertEquals("Password should match", testPassword, retrievedPassword)
    }
    
    @Test
    fun shouldReturnFalseForHasStoredCredentialsWhenEmpty() {
        // Given - no credentials stored
        
        // When
        val hasCredentials = secureStorage.hasStoredCredentials()
        
        // Then
        assertFalse("Should return false when no credentials stored", hasCredentials)
    }
    
    @Test
    fun shouldReturnTrueForHasStoredCredentialsWhenPresent() {
        // Given
        val testUsername = "testuser123"
        val testPassword = "SecurePass123!"
        secureStorage.storeCredentials(testUsername, testPassword)
        
        // When
        val hasCredentials = secureStorage.hasStoredCredentials()
        
        // Then
        assertTrue("Should return true when credentials are stored", hasCredentials)
    }
    
    @Test
    fun shouldDeleteCredentialsSuccessfully() {
        // Given
        val testUsername = "testuser123"
        val testPassword = "SecurePass123!"
        secureStorage.storeCredentials(testUsername, testPassword)
        
        // Verify credentials are stored
        assertTrue("Credentials should be stored", secureStorage.hasStoredCredentials())
        
        // When - Delete credentials
        val deleteResult = secureStorage.deleteCredentials()
        
        // Then - Deletion should succeed
        assertTrue("Deletion should be successful", deleteResult.isSuccess)
        
        // And credentials should no longer exist
        assertFalse("Credentials should not exist after deletion", secureStorage.hasStoredCredentials())
        
        // And retrieval should fail
        val retrieveResult = secureStorage.retrieveCredentials()
        assertTrue("Retrieval should fail after deletion", retrieveResult.isFailure)
    }
    
    @Test
    fun shouldRejectEmptyUsername() {
        // Given
        val emptyUsername = ""
        val validPassword = "SecurePass123!"
        
        // When
        val result = secureStorage.storeCredentials(emptyUsername, validPassword)
        
        // Then
        assertTrue("Should fail with empty username", result.isFailure)
        assertEquals("Should have correct error message", 
            "Username cannot be empty", 
            result.exceptionOrNull()?.message)
    }
    
    @Test
    fun shouldRejectEmptyPassword() {
        // Given
        val validUsername = "testuser"
        val emptyPassword = ""
        
        // When
        val result = secureStorage.storeCredentials(validUsername, emptyPassword)
        
        // Then
        assertTrue("Should fail with empty password", result.isFailure)
        assertEquals("Should have correct error message", 
            "Password cannot be empty", 
            result.exceptionOrNull()?.message)
    }
    
    @Test
    fun shouldSanitizeInputDuringStorage() {
        // Given - inputs with dangerous characters
        val maliciousUsername = "user<script>alert('xss')</script>"
        val maliciousPassword = "pass\"with'quotes&entities"
        
        // When
        val storeResult = secureStorage.storeCredentials(maliciousUsername, maliciousPassword)
        
        // Then - Should succeed (inputs get sanitized)
        assertTrue("Should store sanitized credentials", storeResult.isSuccess)
        
        // When - Retrieve sanitized credentials
        val retrieveResult = secureStorage.retrieveCredentials()
        val (retrievedUsername, retrievedPassword) = retrieveResult.getOrThrow()
        
        // Then - Dangerous characters should be removed
        assertFalse("Username should not contain script tags", retrievedUsername.contains("<script>"))
        assertFalse("Username should not contain HTML entities", retrievedUsername.contains("&"))
        assertFalse("Password should not contain quotes", retrievedPassword.contains("\""))
        assertFalse("Password should not contain single quotes", retrievedPassword.contains("'"))
    }
    
    @Test
    fun shouldPersistCredentialsAcrossInstanceCreation() {
        // Given
        val testUsername = "persistent_user"
        val testPassword = "PersistentPass123!"
        
        // When - Store credentials with first instance
        val firstInstance = SecureStorage(context)
        val storeResult = firstInstance.storeCredentials(testUsername, testPassword)
        assertTrue("First storage should succeed", storeResult.isSuccess)
        
        // When - Create new instance and retrieve
        val secondInstance = SecureStorage(context)
        val retrieveResult = secondInstance.retrieveCredentials()
        
        // Then - Should retrieve same credentials
        assertTrue("Second instance should retrieve credentials", retrieveResult.isSuccess)
        
        val (retrievedUsername, retrievedPassword) = retrieveResult.getOrThrow()
        assertEquals("Username should persist", testUsername, retrievedUsername)
        assertEquals("Password should persist", testPassword, retrievedPassword)
        
        // Clean up
        secondInstance.deleteCredentials()
    }
    
    @Test
    fun shouldHandleMultipleCredentialUpdates() {
        // Given
        val originalUsername = "user1"
        val originalPassword = "pass1"
        val newUsername = "user2"
        val newPassword = "pass2"
        
        // When - Store original credentials
        var result = secureStorage.storeCredentials(originalUsername, originalPassword)
        assertTrue("First storage should succeed", result.isSuccess)
        
        // When - Update credentials
        result = secureStorage.storeCredentials(newUsername, newPassword)
        assertTrue("Update should succeed", result.isSuccess)
        
        // Then - Should retrieve updated credentials
        val retrieveResult = secureStorage.retrieveCredentials()
        assertTrue("Retrieval should succeed", retrieveResult.isSuccess)
        
        val (retrievedUsername, retrievedPassword) = retrieveResult.getOrThrow()
        assertEquals("Should have updated username", newUsername, retrievedUsername)
        assertEquals("Should have updated password", newPassword, retrievedPassword)
    }
}