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

    private val validRTSPConfig = RTSPConfig(
        serverUrl = "rtsp://192.168.1.100",
        username = "testuser",
        password = "TestPass123!",
        port = 554,
        streamPath = "/live",
        quality = StreamQuality.MEDIUM,
        enableAudio = true,
        useAuthentication = true
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        context = RuntimeEnvironment.getApplication()
        
        // Setup default mock behaviors
        every { mockInputValidator.validateRTSPUrl(any()) } returns ValidationResult(true)
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
    fun `should accept valid RTSP configuration`() = runTest {
        // When
        val result = streamRepository.updateConfiguration(validRTSPConfig)

        // Then
        assertTrue("Should accept valid configuration", result.isSuccess)
        assertEquals("Should store configuration", validRTSPConfig, streamRepository.getCurrentConfig())
    }

    @Test
    fun `should reject configuration with invalid URL`() = runTest {
        // Given
        every { mockInputValidator.validateRTSPUrl(any()) } returns ValidationResult(false, "Invalid URL")
        val invalidConfig = validRTSPConfig.copy(serverUrl = "invalid-url")

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
        val weakPasswordConfig = validRTSPConfig.copy(password = "weak")

        // When
        val result = streamRepository.updateConfiguration(weakPasswordConfig)

        // Then
        assertTrue("Should reject weak password", result.isFailure)
        assertNull("Should not store configuration with weak password", streamRepository.getCurrentConfig())
    }

    @Test
    fun `should accept configuration without authentication`() = runTest {
        // Given
        val noAuthConfig = validRTSPConfig.copy(
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
        streamRepository.updateConfiguration(validRTSPConfig)

        // Then
        verify { mockSecureStorage.storeCredentials("testuser", "TestPass123!") }
    }

    @Test
    fun `should not store credentials when authentication disabled`() = runTest {
        // Given
        val noAuthConfig = validRTSPConfig.copy(useAuthentication = false)

        // When
        streamRepository.updateConfiguration(noAuthConfig)

        // Then
        verify(exactly = 0) { mockSecureStorage.storeCredentials(any(), any()) }
    }

    // ========== Streaming State Management Tests ==========

    @Test
    fun `should transition to preparing state when starting stream`() = runTest {
        // Given
        streamRepository.updateConfiguration(validRTSPConfig)

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
        streamRepository.updateConfiguration(validRTSPConfig)
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
        streamRepository.updateConfiguration(validRTSPConfig)

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
        streamRepository.updateConfiguration(validRTSPConfig)
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
        val maliciousConfig = validRTSPConfig.copy(serverUrl = "rtsp://malicious.com/../admin")

        // When
        streamRepository.updateConfiguration(maliciousConfig)

        // Then
        verify { mockInputValidator.validateRTSPUrl("rtsp://malicious.com/../admin") }
    }

    @Test
    fun `should prevent streaming with malicious stream path`() = runTest {
        // Given
        every { mockInputValidator.containsMaliciousContent("/live") } returns false
        every { mockInputValidator.containsMaliciousContent("/live/../admin") } returns true
        val maliciousConfig = validRTSPConfig.copy(streamPath = "/live/../admin")

        // When
        val result = streamRepository.updateConfiguration(maliciousConfig)

        // Then
        assertTrue("Should reject malicious stream path", result.isFailure)
    }

    @Test
    fun `should validate credentials before streaming`() = runTest {
        // Given
        streamRepository.updateConfiguration(validRTSPConfig)

        // When
        streamRepository.startStreaming()

        // Then
        verify { mockSecureStorage.retrieveCredentials() }
    }

    @Test
    fun `should handle credential retrieval failure securely`() = runTest {
        // Given
        every { mockSecureStorage.retrieveCredentials() } returns Result.failure(Exception("Access denied"))
        streamRepository.updateConfiguration(validRTSPConfig)

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
        streamRepository.updateConfiguration(validRTSPConfig)
        every { mockSecureStorage.deleteCredentials() } returns Result.success(Unit)

        // When
        val noAuthConfig = validRTSPConfig.copy(useAuthentication = false)
        streamRepository.updateConfiguration(noAuthConfig)

        // Then
        verify { mockSecureStorage.deleteCredentials() }
    }

    @Test
    fun `should update credentials when changing authentication details`() = runTest {
        // Given
        streamRepository.updateConfiguration(validRTSPConfig)
        clearMocks(mockSecureStorage)

        // When
        val updatedConfig = validRTSPConfig.copy(
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
        val invalidPortConfig = validRTSPConfig.copy(port = 9999)

        // When
        val result = streamRepository.updateConfiguration(invalidPortConfig)

        // Then
        assertTrue("Should reject invalid port", result.isFailure)
    }

    @Test
    fun `should validate streaming quality parameters`() = runTest {
        // Given
        val configWithInvalidBitrate = validRTSPConfig.copy(maxBitrate = 50000) // Too low

        // When
        val result = streamRepository.updateConfiguration(configWithInvalidBitrate)

        // Then
        assertTrue("Should reject invalid bitrate", result.isFailure)
    }

    @Test
    fun `should handle concurrent state changes safely`() = runTest {
        // Given
        streamRepository.updateConfiguration(validRTSPConfig)

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
        every { mockInputValidator.validateRTSPUrl(any()) } returns ValidationResult(false, "Test validation error")
        val invalidConfig = validRTSPConfig.copy(serverUrl = "invalid")

        // When
        streamRepository.updateConfiguration(invalidConfig)

        // Then
        val currentState = streamRepository.streamState.first()
        assertTrue("Should be in idle state after config failure", currentState.isIdle)
    }

    // ========== Streaming URL Generation Tests ==========

    @Test
    fun `should generate correct RTSP URL with authentication`() = runTest {
        // Given
        streamRepository.updateConfiguration(validRTSPConfig)

        // When
        val generatedUrl = validRTSPConfig.generateRTSPUrl()

        // Then
        assertTrue("Should contain username and password", 
            generatedUrl.contains("testuser:TestPass123!"))
        assertTrue("Should contain server and port", 
            generatedUrl.contains("192.168.1.100:554"))
        assertTrue("Should contain stream path", 
            generatedUrl.contains("/live"))
    }

    @Test
    fun `should generate correct RTSP URL without authentication`() = runTest {
        // Given
        val noAuthConfig = validRTSPConfig.copy(useAuthentication = false)
        streamRepository.updateConfiguration(noAuthConfig)

        // When
        val generatedUrl = noAuthConfig.generateRTSPUrl()

        // Then
        assertFalse("Should not contain credentials", generatedUrl.contains("testuser"))
        assertFalse("Should not contain credentials", generatedUrl.contains("TestPass123!"))
        assertTrue("Should contain server and port", 
            generatedUrl.contains("192.168.1.100:554"))
    }

    @Test
    fun `should generate safe display URL without credentials`() = runTest {
        // Given
        streamRepository.updateConfiguration(validRTSPConfig)

        // When
        val displayUrl = validRTSPConfig.generateDisplayUrl()

        // Then
        assertFalse("Should not expose credentials", displayUrl.contains("testuser"))
        assertFalse("Should not expose credentials", displayUrl.contains("TestPass123!"))
        assertTrue("Should contain server info", displayUrl.contains("192.168.1.100:554"))
    }
}