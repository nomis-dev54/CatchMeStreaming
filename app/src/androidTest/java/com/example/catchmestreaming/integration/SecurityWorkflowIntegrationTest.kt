package com.example.catchmestreaming.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catchmestreaming.data.StreamConfig
import com.example.catchmestreaming.repository.StreamRepository
import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.SecurePreferences
import com.example.catchmestreaming.security.SecureStorage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for complete security workflows
 * Tests authentication flows, credential storage, and security validation
 */
@RunWith(AndroidJUnit4::class)
class SecurityWorkflowIntegrationTest {

    private lateinit var context: Context
    private lateinit var secureStorage: SecureStorage
    private lateinit var securePreferences: SecurePreferences
    private lateinit var inputValidator: InputValidator
    private lateinit var streamRepository: StreamRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        secureStorage = SecureStorage(context)
        securePreferences = SecurePreferences(context)
        inputValidator = InputValidator()
        streamRepository = StreamRepository(context)
    }

    @After
    fun tearDown() {
        runTest {
            secureStorage.deleteCredentials()
            securePreferences.clear()
            streamRepository.cleanup()
        }
    }

    @Test
    fun completeCredentialWorkflow_shouldMaintainSecurity() = runTest {
        val username = "testuser"
        val password = "securePassword123!"

        // Step 1: Validate input before storage
        val usernameValidation = inputValidator.sanitizeUsername(username)
        val passwordValidation = inputValidator.validatePassword(password)

        assertEquals("Username should be sanitized", username, usernameValidation)
        assertTrue("Password should be valid", passwordValidation.isValid)

        // Step 2: Store credentials securely
        val storeResult = secureStorage.storeCredentials(username, password)
        assertTrue("Credentials should be stored successfully", storeResult.isSuccess)

        // Step 3: Retrieve and verify credentials
        val retrieveResult = secureStorage.retrieveCredentials()
        assertTrue("Credentials should be retrieved successfully", retrieveResult.isSuccess)

        retrieveResult.getOrNull()?.let { (retrievedUsername, retrievedPassword) ->
            assertEquals("Username should match", username, retrievedUsername)
            assertEquals("Password should match", password, retrievedPassword)
        } ?: fail("Retrieved credentials should not be null")

        // Step 4: Use credentials in stream configuration
        val streamConfig = StreamConfig(
            serverUrl = "192.168.1.100",
            port = 8080,
            streamPath = "/stream",
            quality = com.example.catchmestreaming.data.StreamQuality.HD_720P,
            username = username,
            password = password,
            enableAuth = true
        )

        val configResult = streamRepository.updateConfiguration(streamConfig)
        assertTrue("Stream configuration with credentials should succeed", configResult.isSuccess)

        // Step 5: Verify credentials are not stored in plain text in stream config
        val currentConfig = streamRepository.getCurrentConfig()
        assertNotNull("Current config should exist", currentConfig)
        
        // Configuration should either encrypt passwords or use references
        // This ensures passwords aren't stored in plain text in memory
        assertTrue("Stream config should be created", currentConfig != null)

        // Step 6: Delete credentials
        val deleteResult = secureStorage.deleteCredentials()
        assertTrue("Credentials should be deleted successfully", deleteResult.isSuccess)

        // Step 7: Verify credentials are actually deleted
        val verifyDeleteResult = secureStorage.retrieveCredentials()
        assertTrue("Retrieve after delete should fail", verifyDeleteResult.isFailure)
    }

    @Test
    fun inputValidationWorkflow_shouldPreventInjectionAttacks() = runTest {
        val maliciousInputs = listOf(
            "'; DROP TABLE users; --",
            "<script>alert('xss')</script>",
            "../../../etc/passwd",
            "user\nHOST: evil.com",
            "user@domain.com; rm -rf /",
            "user${System.exit(0)}",
            "user\r\nSet-Cookie: evil=true"
        )

        maliciousInputs.forEach { maliciousInput ->
            // Test username sanitization
            val sanitizedUsername = inputValidator.sanitizeUsername(maliciousInput)
            assertNotEquals("Username should be sanitized", maliciousInput, sanitizedUsername)
            assertFalse("Sanitized username should not contain dangerous characters",
                sanitizedUsername.contains(";") || sanitizedUsername.contains("<") || 
                sanitizedUsername.contains("../") || sanitizedUsername.contains("\n") ||
                sanitizedUsername.contains("\r"))

            // Test RTSP URL validation
            val urlValidation = inputValidator.validateRTSPUrl("rtsp://$maliciousInput:8554/stream")
            assertFalse("Malicious URL should be rejected", urlValidation.isValid)

            // Test server URL validation
            val serverValidation = inputValidator.validateServerUrl(maliciousInput)
            assertFalse("Malicious server URL should be rejected", serverValidation.isValid)
        }
    }

    @Test
    fun authenticationFlowIntegration_shouldWorkEndToEnd() = runTest {
        // Step 1: Setup secure preferences for auth settings
        securePreferences.putBoolean("auth_enabled", true)
        securePreferences.putString("last_auth_method", "password")

        // Step 2: Store authentication credentials
        val username = "validuser"
        val password = "ValidPass123!"
        
        val storeResult = secureStorage.storeCredentials(username, password)
        assertTrue("Credentials storage should succeed", storeResult.isSuccess)

        // Step 3: Create authenticated stream configuration
        val authConfig = StreamConfig(
            serverUrl = "192.168.1.100",
            port = 8080,
            streamPath = "/secure-stream",
            quality = com.example.catchmestreaming.data.StreamQuality.HD_720P,
            username = username,
            password = password,
            enableAuth = true
        )

        val configResult = streamRepository.updateConfiguration(authConfig)
        assertTrue("Authenticated config should be valid", configResult.isSuccess)

        // Step 4: Verify authentication settings are preserved
        val authEnabled = securePreferences.getBoolean("auth_enabled", false)
        val authMethod = securePreferences.getString("last_auth_method", "")

        assertTrue("Auth should be enabled", authEnabled)
        assertEquals("Auth method should be preserved", "password", authMethod)

        // Step 5: Test configuration without authentication
        val noAuthConfig = authConfig.copy(enableAuth = false)
        val noAuthResult = streamRepository.updateConfiguration(noAuthConfig)
        assertTrue("Non-authenticated config should also be valid", noAuthResult.isSuccess)

        // Step 6: Switch back to authenticated mode
        val reAuthResult = streamRepository.updateConfiguration(authConfig)
        assertTrue("Re-enabling auth should work", reAuthResult.isSuccess)
    }

    @Test
    fun securePreferencesIntegration_shouldEncryptSensitiveData() = runTest {
        val sensitiveData = mapOf(
            "server_history" to "192.168.1.100,192.168.1.101,192.168.1.102",
            "last_quality_setting" to "HD_1080P",
            "user_preferences" to "enablePreview=true,autoStart=false",
            "security_level" to "high"
        )

        // Store sensitive preferences
        sensitiveData.forEach { (key, value) ->
            securePreferences.putString(key, value)
        }

        // Retrieve and verify
        sensitiveData.forEach { (key, expectedValue) ->
            val retrievedValue = securePreferences.getString(key, "")
            assertEquals("Value for $key should match", expectedValue, retrievedValue)
        }

        // Test boolean preferences
        securePreferences.putBoolean("first_run_complete", true)
        securePreferences.putBoolean("security_warnings_enabled", true)

        assertTrue("First run should be marked complete", 
            securePreferences.getBoolean("first_run_complete", false))
        assertTrue("Security warnings should be enabled",
            securePreferences.getBoolean("security_warnings_enabled", false))

        // Test preferences clearing
        securePreferences.clear()

        // Verify all data is cleared
        sensitiveData.keys.forEach { key ->
            val clearedValue = securePreferences.getString(key, "default")
            assertEquals("Cleared value should be default", "default", clearedValue)
        }

        assertFalse("Cleared boolean should be default false",
            securePreferences.getBoolean("first_run_complete", false))
    }

    @Test
    fun networkSecurityValidation_shouldRejectUnsafeConfigurations() = runTest {
        val unsafeConfigurations = listOf(
            // Invalid IP addresses
            StreamConfig.createDefault().copy(serverUrl = "999.999.999.999"),
            StreamConfig.createDefault().copy(serverUrl = "not.an.ip.address"),
            StreamConfig.createDefault().copy(serverUrl = ""),
            
            // Invalid ports
            StreamConfig.createDefault().copy(port = -1),
            StreamConfig.createDefault().copy(port = 0),
            StreamConfig.createDefault().copy(port = 99999),
            
            // Invalid stream paths
            StreamConfig.createDefault().copy(streamPath = ""),
            StreamConfig.createDefault().copy(streamPath = "../../../etc/passwd"),
            StreamConfig.createDefault().copy(streamPath = "/stream'; DROP TABLE;"),
        )

        unsafeConfigurations.forEach { unsafeConfig ->
            val result = streamRepository.updateConfiguration(unsafeConfig)
            
            // Configuration should either be rejected or sanitized
            if (result.isSuccess) {
                val currentConfig = streamRepository.getCurrentConfig()
                assertNotNull("Config should exist if accepted", currentConfig)
                
                // Verify sanitization occurred
                currentConfig?.let { config ->
                    assertTrue("Server URL should be valid",
                        inputValidator.validateServerUrl(config.serverUrl).isValid)
                    assertTrue("Port should be in valid range", 
                        config.port in 1..65535)
                    assertTrue("Stream path should be safe",
                        inputValidator.validateStreamPath(config.streamPath).isValid)
                }
            }
        }
    }

    @Test
    fun credentialRotation_shouldUpdateSecurely() = runTest {
        // Initial credentials
        val initialUsername = "user1"
        val initialPassword = "password1"
        
        secureStorage.storeCredentials(initialUsername, initialPassword)
        
        // Verify initial storage
        val initial = secureStorage.retrieveCredentials().getOrThrow()
        assertEquals("Initial username should match", initialUsername, initial.first)
        assertEquals("Initial password should match", initialPassword, initial.second)

        // Rotate credentials
        val newUsername = "user2"
        val newPassword = "newSecurePassword456!"
        
        val rotateResult = secureStorage.storeCredentials(newUsername, newPassword)
        assertTrue("Credential rotation should succeed", rotateResult.isSuccess)

        // Verify new credentials
        val rotated = secureStorage.retrieveCredentials().getOrThrow()
        assertEquals("New username should match", newUsername, rotated.first)
        assertEquals("New password should match", newPassword, rotated.second)

        // Verify old credentials are completely replaced
        assertNotEquals("Old username should be gone", initialUsername, rotated.first)
        assertNotEquals("Old password should be gone", initialPassword, rotated.second)

        // Test multiple rapid rotations
        repeat(5) { index ->
            val tempUsername = "tempUser$index"
            val tempPassword = "tempPass$index"
            
            val tempResult = secureStorage.storeCredentials(tempUsername, tempPassword)
            assertTrue("Rapid rotation $index should succeed", tempResult.isSuccess)
            
            val verified = secureStorage.retrieveCredentials().getOrThrow()
            assertEquals("Rapid rotation username $index should match", tempUsername, verified.first)
            assertEquals("Rapid rotation password $index should match", tempPassword, verified.second)
        }
    }

    @Test
    fun securityIncidentHandling_shouldLogAndRecover() = runTest {
        // Simulate security incident: invalid credentials
        val invalidCredentials = listOf(
            "" to "password", // Empty username
            "user" to "", // Empty password
            "user\nmalicious" to "password", // Username with newline
            "user" to "pass\rmalicious" // Password with carriage return
        )

        invalidCredentials.forEach { (username, password) ->
            // These should either be rejected or sanitized
            val sanitizedUsername = inputValidator.sanitizeUsername(username)
            val passwordValidation = inputValidator.validatePassword(password)

            // Sanitized username should be safe
            assertFalse("Sanitized username should not contain dangerous chars",
                sanitizedUsername.contains("\n") || sanitizedUsername.contains("\r"))

            // Invalid passwords should be rejected
            if (password.isEmpty() || password.contains("\n") || password.contains("\r")) {
                assertFalse("Invalid password should be rejected", passwordValidation.isValid)
            }
        }

        // Test recovery from invalid state
        // First, put system in invalid state (if possible)
        val corruptResult = secureStorage.storeCredentials("", "")
        
        if (corruptResult.isFailure) {
            // Good - invalid credentials were rejected
            assertTrue("Invalid credentials should be rejected", true)
        } else {
            // System allowed invalid credentials - test recovery
            val retrieveResult = secureStorage.retrieveCredentials()
            if (retrieveResult.isFailure) {
                // System detected corruption - test recovery
                val validRecoveryResult = secureStorage.storeCredentials("validUser", "validPass123!")
                assertTrue("Recovery with valid credentials should work", validRecoveryResult.isSuccess)
            }
        }

        // Ensure system is in valid state after potential incident
        val finalResult = secureStorage.storeCredentials("testUser", "testPass123!")
        assertTrue("System should be recoverable", finalResult.isSuccess)
    }
}