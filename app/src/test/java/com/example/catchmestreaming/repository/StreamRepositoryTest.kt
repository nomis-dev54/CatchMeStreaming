package com.example.catchmestreaming.repository

import android.content.Context
import com.example.catchmestreaming.data.*
import com.example.catchmestreaming.security.SecureStorage
import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.ValidationResult
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class StreamRepositoryTest {

    private lateinit var context: Context
    private lateinit var streamRepository: StreamRepository
    
    @MockK
    private lateinit var mockSecureStorage: SecureStorage
    
    @MockK
    private lateinit var mockInputValidator: InputValidator

    private val validStreamConfig = StreamConfig(
        serverUrl = "192.168.1.100",
        username = "testuser",
        password = "TestPass123!",
        port = 8080,
        streamPath = "/stream",
        quality = StreamQuality.MEDIUM,
        enableAudio = true,
        useAuthentication = true
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        context = RuntimeEnvironment.getApplication()
        
        // Setup default mock behaviors
        every { mockInputValidator.validateStreamingUrl(any()) } returns ValidationResult(true)
        every { mockInputValidator.validatePassword(any()) } returns ValidationResult(true)
        every { mockInputValidator.validatePort(any()) } returns ValidationResult(true)
        every { mockInputValidator.sanitizeUsername(any()) } returns "testuser"
        every { mockInputValidator.containsMaliciousContent(any()) } returns false
        every { mockSecureStorage.storeCredentials(any(), any()) } returns Result.success(Unit)
        every { mockSecureStorage.retrieveCredentials() } returns Result.success("testuser" to "TestPass123!")
        
        streamRepository = StreamRepository(context, mockSecureStorage, mockInputValidator)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `repository should initialize with idle state`() = runTest {
        // When
        val initialState = streamRepository.streamState.first()

        // Then
        assertTrue("Should be in idle state", initialState.isIdle)
        assertFalse("Should not be active", initialState.isActive)
        assertTrue("Should be able to start", initialState.canStart)
        assertFalse("Should not be able to stop", initialState.canStop)
    }

    @Test
    fun `repository should initialize with no configuration`() = runTest {
        // When
        val config = streamRepository.getCurrentConfig()

        // Then
        assertNull("Should have no initial configuration", config)
    }

    // ========== Configuration Tests ==========

    @Test
    fun `should accept valid streaming configuration`() = runTest {
        // When
        val result = streamRepository.updateConfiguration(validStreamConfig)

        // Then
        assertTrue("Should accept valid configuration", result.isSuccess)
        assertEquals("Should store configuration", validStreamConfig, streamRepository.getCurrentConfig())
    }

    @Test
    fun `should reject configuration with invalid URL`() = runTest {
        // Given
        every { mockInputValidator.validateStreamingUrl(any()) } returns ValidationResult(false, "Invalid URL")
        val invalidConfig = validStreamConfig.copy(serverUrl = "invalid-url")

        // When
        val result = streamRepository.updateConfiguration(invalidConfig)

        // Then
        assertTrue("Should reject invalid configuration", result.isFailure)
        assertNull("Should not store invalid configuration", streamRepository.getCurrentConfig())
    }

    @Test
    fun `should reject configuration with weak password when authentication enabled`() = runTest {
        // Given
        every { mockInputValidator.validatePassword(any()) } returns ValidationResult(false, "Weak password")
        val weakPasswordConfig = validStreamConfig.copy(password = "weak")

        // When
        val result = streamRepository.updateConfiguration(weakPasswordConfig)

        // Then
        assertTrue("Should reject weak password", result.isFailure)
        assertNull("Should not store configuration with weak password", streamRepository.getCurrentConfig())
    }

    @Test
    fun `should accept configuration without authentication`() = runTest {
        // Given
        val noAuthConfig = validStreamConfig.copy(
            useAuthentication = false,
            username = "",
            password = ""
        )

        // When
        val result = streamRepository.updateConfiguration(noAuthConfig)

        // Then
        assertTrue("Should accept configuration without authentication", result.isSuccess)
        assertEquals("Should store configuration", noAuthConfig, streamRepository.getCurrentConfig())
    }

    @Test
    fun `should store credentials securely when authentication enabled`() = runTest {
        // When
        streamRepository.updateConfiguration(validStreamConfig)

        // Then
        verify { mockSecureStorage.storeCredentials("testuser", "TestPass123!") }
    }

    @Test
    fun `should not store credentials when authentication disabled`() = runTest {
        // Given
        val noAuthConfig = validStreamConfig.copy(useAuthentication = false)

        // When
        streamRepository.updateConfiguration(noAuthConfig)

        // Then
        verify(exactly = 0) { mockSecureStorage.storeCredentials(any(), any()) }
    }

    // ========== Streaming State Management Tests ==========

    @Test
    fun `should transition to preparing state when starting stream`() = runTest {
        // Given
        streamRepository.updateConfiguration(validStreamConfig)

        // When
        streamRepository.startStreaming()

        // Then
        val currentState = streamRepository.streamState.first()
        assertTrue("Should be in preparing state", currentState.isPreparing)
    }

    @Test
    fun `should reject start streaming without configuration`() = runTest {
        // When
        val result = streamRepository.startStreaming()

        // Then
        assertTrue("Should reject starting without configuration", result.isFailure)
        val currentState = streamRepository.streamState.first()
        assertTrue("Should remain in idle state", currentState.isIdle)
    }

    @Test
    fun `should reject start streaming when already active`() = runTest {
        // Given
        streamRepository.updateConfiguration(validStreamConfig)
        streamRepository.startStreaming()

        // When
        val result = streamRepository.startStreaming()

        // Then
        assertTrue("Should reject starting when already active", result.isFailure)
    }

    @Test
    fun `should handle streaming preparation failure gracefully`() = runTest {
        // Given
        every { mockSecureStorage.retrieveCredentials() } returns Result.failure(Exception("Storage error"))
        streamRepository.updateConfiguration(validStreamConfig)

        // When
        streamRepository.startStreaming()

        // Then
        val currentState = streamRepository.streamState.first()
        assertTrue("Should be in error state", currentState.isError)
        if (currentState is StreamState.Error) {
            assertEquals("Should have configuration error code", StreamErrorCode.CONFIGURATION_ERROR, currentState.errorCode)
            assertTrue("Should allow retry", currentState.canRetry)
        }
    }

    @Test
    fun `should transition to stopped state when stopping stream`() = runTest {
        // Given
        streamRepository.updateConfiguration(validStreamConfig)
        streamRepository.startStreaming()

        // When
        streamRepository.stopStreaming()

        // Then
        val currentState = streamRepository.streamState.first()
        assertTrue("Should be in stopped state", currentState.isStopped)
    }

    @Test
    fun `should handle stop streaming when not active gracefully`() = runTest {
        // When
        val result = streamRepository.stopStreaming()

        // Then
        assertTrue("Should handle stop when not active", result.isSuccess)
        val currentState = streamRepository.streamState.first()
        assertTrue("Should remain in idle state", currentState.isIdle)
    }

    // ========== Security Tests ==========

    @Test
    fun `should validate URL before starting stream`() = runTest {
        // Given
        every { mockInputValidator.validateRTSPUrl(any()) } returns ValidationResult(false, "Malicious URL")
        val maliciousConfig = validStreamConfig.copy(serverUrl = "http://malicious.com/../admin")

        // When
        streamRepository.updateConfiguration(maliciousConfig)

        // Then
        verify { mockInputValidator.validateStreamingUrl("http://malicious.com/../admin") }
    }

    @Test
    fun `should prevent streaming with malicious stream path`() = runTest {
        // Given
        every { mockInputValidator.containsMaliciousContent("/stream") } returns false
        every { mockInputValidator.containsMaliciousContent("/stream/../admin") } returns true
        val maliciousConfig = validStreamConfig.copy(streamPath = "/stream/../admin")

        // When
        val result = streamRepository.updateConfiguration(maliciousConfig)

        // Then
        assertTrue("Should reject malicious stream path", result.isFailure)
    }

    @Test
    fun `should validate credentials before streaming`() = runTest {
        // Given
        streamRepository.updateConfiguration(validStreamConfig)

        // When
        streamRepository.startStreaming()

        // Then
        verify { mockSecureStorage.retrieveCredentials() }
    }

    @Test
    fun `should handle credential retrieval failure securely`() = runTest {
        // Given
        every { mockSecureStorage.retrieveCredentials() } returns Result.failure(Exception("Access denied"))
        streamRepository.updateConfiguration(validStreamConfig)

        // When
        streamRepository.startStreaming()

        // Then
        val currentState = streamRepository.streamState.first()
        assertTrue("Should be in error state", currentState.isError)
        if (currentState is StreamState.Error) {
            assertTrue("Error message should not expose sensitive details", 
                !currentState.errorMessage.contains("password", ignoreCase = true))
        }
    }

    // ========== Configuration Persistence Tests ==========

    @Test
    fun `should clear stored credentials when disabling authentication`() = runTest {
        // Given
        streamRepository.updateConfiguration(validStreamConfig)
        every { mockSecureStorage.deleteCredentials() } returns Result.success(Unit)

        // When
        val noAuthConfig = validStreamConfig.copy(useAuthentication = false)
        streamRepository.updateConfiguration(noAuthConfig)

        // Then
        verify { mockSecureStorage.deleteCredentials() }
    }

    @Test
    fun `should update credentials when changing authentication details`() = runTest {
        // Given
        streamRepository.updateConfiguration(validStreamConfig)
        clearMocks(mockSecureStorage)

        // When
        val updatedConfig = validStreamConfig.copy(
            username = "newuser",
            password = "NewPass456!"
        )
        streamRepository.updateConfiguration(updatedConfig)

        // Then
        verify { mockSecureStorage.storeCredentials("newuser", "NewPass456!") }
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    fun `should handle network configuration validation`() = runTest {
        // Given
        every { mockInputValidator.validatePort(9999) } returns ValidationResult(false, "Invalid port")
        val invalidPortConfig = validStreamConfig.copy(port = 9999)

        // When
        val result = streamRepository.updateConfiguration(invalidPortConfig)

        // Then
        assertTrue("Should reject invalid port", result.isFailure)
    }

    @Test
    fun `should validate streaming quality parameters`() = runTest {
        // Given
        val configWithInvalidBitrate = validStreamConfig.copy(maxBitrate = 50000) // Too low

        // When
        val result = streamRepository.updateConfiguration(configWithInvalidBitrate)

        // Then
        assertTrue("Should reject invalid bitrate", result.isFailure)
    }

    @Test
    fun `should handle concurrent state changes safely`() = runTest {
        // Given
        streamRepository.updateConfiguration(validStreamConfig)

        // When - simulate concurrent start/stop calls
        val startResult1 = streamRepository.startStreaming()
        val startResult2 = streamRepository.startStreaming()
        val stopResult = streamRepository.stopStreaming()

        // Then
        assertTrue("First start should succeed", startResult1.isSuccess)
        assertTrue("Second start should be rejected", startResult2.isFailure)
        assertTrue("Stop should succeed", stopResult.isSuccess)
    }

    @Test
    fun `should provide detailed error information for debugging`() = runTest {
        // Given
        every { mockInputValidator.validateStreamingUrl(any()) } returns ValidationResult(false, "Test validation error")
        val invalidConfig = validStreamConfig.copy(serverUrl = "invalid")

        // When
        streamRepository.updateConfiguration(invalidConfig)

        // Then
        val currentState = streamRepository.streamState.first()
        assertTrue("Should be in idle state after config failure", currentState.isIdle)
    }

    // ========== Streaming URL Generation Tests ==========

    @Test
    fun `should generate correct HTTP streaming URL with authentication`() = runTest {
        // Given
        streamRepository.updateConfiguration(validStreamConfig)

        // When
        val generatedUrl = validStreamConfig.generateStreamingUrl()

        // Then
        assertTrue("Should contain username and password", 
            generatedUrl.contains("testuser:TestPass123!"))
        assertTrue("Should contain server and port", 
            generatedUrl.contains("192.168.1.100:8080"))
        assertTrue("Should contain stream path", 
            generatedUrl.contains("/stream"))
    }

    @Test
    fun `should generate correct HTTP streaming URL without authentication`() = runTest {
        // Given
        val noAuthConfig = validStreamConfig.copy(useAuthentication = false)
        streamRepository.updateConfiguration(noAuthConfig)

        // When
        val generatedUrl = noAuthConfig.generateStreamingUrl()

        // Then
        assertFalse("Should not contain credentials", generatedUrl.contains("testuser"))
        assertFalse("Should not contain credentials", generatedUrl.contains("TestPass123!"))
        assertTrue("Should contain server and port", 
            generatedUrl.contains("192.168.1.100:8080"))
    }

    @Test
    fun `should generate safe display URL without credentials`() = runTest {
        // Given
        streamRepository.updateConfiguration(validStreamConfig)

        // When
        val displayUrl = validStreamConfig.generateDisplayUrl()

        // Then
        assertFalse("Should not expose credentials", displayUrl.contains("testuser"))
        assertFalse("Should not expose credentials", displayUrl.contains("TestPass123!"))
        assertTrue("Should contain server info", displayUrl.contains("192.168.1.100:8080"))
    }
}