package com.example.catchmestreaming.security

import android.content.Context
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SecureStorageTest {
    
    private lateinit var secureStorage: SecureStorage
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        secureStorage = SecureStorage(context)
    }
    
    @After
    fun tearDown() {
        // Clean up test credentials
        secureStorage.deleteCredentials()
    }
    
    @Test
    fun shouldEncryptCredentialsWhenStored() {
        // Given
        val username = "testuser"
        val password = "testpassword123"
        
        // When
        val result = secureStorage.storeCredentials(username, password)
        
        // Then
        assertTrue("Credentials should be stored successfully", result.isSuccess)
    }
    
    @Test
    fun shouldRetrieveStoredCredentials() {
        // Given
        val username = "testuser"
        val password = "testpassword123"
        secureStorage.storeCredentials(username, password)
        
        // When
        val result = secureStorage.retrieveCredentials()
        
        // Then
        assertTrue("Credentials should be retrieved successfully", result.isSuccess)
        result.getOrNull()?.let { (retrievedUsername, retrievedPassword) ->
            assertEquals("Username should match", username, retrievedUsername)
            assertEquals("Password should match", password, retrievedPassword)
        } ?: fail("Retrieved credentials should not be null")
    }
    
    @Test
    fun shouldReturnFailureWhenCredentialsNotFound() {
        // Given - no credentials stored
        
        // When
        val result = secureStorage.retrieveCredentials()
        
        // Then
        assertTrue("Should fail when no credentials stored", result.isFailure)
    }
    
    @Test
    fun shouldDeleteCredentialsSuccessfully() {
        // Given
        val username = "testuser"
        val password = "testpassword123"
        secureStorage.storeCredentials(username, password)
        
        // When
        val deleteResult = secureStorage.deleteCredentials()
        val retrieveResult = secureStorage.retrieveCredentials()
        
        // Then
        assertTrue("Deletion should be successful", deleteResult.isSuccess)
        assertTrue("Credentials should not be retrievable after deletion", retrieveResult.isFailure)
    }
    
    @Test
    fun shouldValidateUsernameInput() {
        // Given
        val validUsername = "validuser"
        val emptyUsername = ""
        val password = "testpassword123"
        
        // When & Then
        val validResult = secureStorage.storeCredentials(validUsername, password)
        assertTrue("Valid username should be accepted", validResult.isSuccess)
        
        val emptyResult = secureStorage.storeCredentials(emptyUsername, password)
        assertTrue("Empty username should be rejected", emptyResult.isFailure)
    }
    
    @Test
    fun shouldValidatePasswordInput() {
        // Given
        val username = "testuser"
        val validPassword = "validpassword123"
        val emptyPassword = ""
        
        // When & Then
        val validResult = secureStorage.storeCredentials(username, validPassword)
        assertTrue("Valid password should be accepted", validResult.isSuccess)
        
        val emptyResult = secureStorage.storeCredentials(username, emptyPassword)
        assertTrue("Empty password should be rejected", emptyResult.isFailure)
    }
    
    @Test
    fun shouldHandleMultipleStoreOperations() {
        // Given
        val username1 = "user1"
        val password1 = "password1"
        val username2 = "user2"
        val password2 = "password2"
        
        // When
        secureStorage.storeCredentials(username1, password1)
        val result = secureStorage.storeCredentials(username2, password2)
        val retrieved = secureStorage.retrieveCredentials()
        
        // Then
        assertTrue("Second store operation should succeed", result.isSuccess)
        assertTrue("Should retrieve latest credentials", retrieved.isSuccess)
        retrieved.getOrNull()?.let { (username, password) ->
            assertEquals("Should have latest username", username2, username)
            assertEquals("Should have latest password", password2, password)
        } ?: fail("Retrieved credentials should not be null")
    }
}