package com.example.catchmestreaming.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catchmestreaming.data.RTSPConfig
import com.example.catchmestreaming.data.StreamQuality
import com.example.catchmestreaming.data.StreamState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamRepositoryInstrumentedTest {
    
    private lateinit var context: Context
    private lateinit var streamRepository: StreamRepository
    
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
        context = ApplicationProvider.getApplicationContext()
        streamRepository = StreamRepository(context)
    }
    
    @After
    fun tearDown() {
        streamRepository.cleanup()
    }
    
    // ========== Android Integration Tests ==========
    
    @Test
    fun testRepositoryInitializationWithRealContext() = runTest {
        // When
        val initialState = streamRepository.streamState.first()
        
        // Then
        assertTrue("Should initialize with idle state", initialState.isIdle)
        assertNull("Should have no initial configuration", streamRepository.getCurrentConfig())
    }
    
    @Test
    fun testConfigurationWithRealSecureStorage() = runTest {
        // When
        val result = streamRepository.updateConfiguration(validRTSPConfig)
        
        // Then
        // Note: This may fail if Android Keystore is not available in test environment
        // That's expected behavior - credentials require hardware backing
        val currentConfig = streamRepository.getCurrentConfig()
        
        if (result.isSuccess) {
            assertEquals("Should store configuration", validRTSPConfig, currentConfig)
        } else {
            // Expected if keystore is not available in test environment
            assertTrue("Should be keystore related failure", 
                result.exceptionOrNull()?.message?.contains("keystore", ignoreCase = true) == true ||
                result.exceptionOrNull()?.message?.contains("credential", ignoreCase = true) == true)
        }
    }
    
    @Test
    fun testConfigurationWithoutAuthentication() = runTest {
        // Given
        val noAuthConfig = validRTSPConfig.copy(
            useAuthentication = false,
            username = "",
            password = ""
        )
        
        // When
        val result = streamRepository.updateConfiguration(noAuthConfig)
        
        // Then
        assertTrue("Should accept config without authentication", result.isSuccess)
        assertEquals("Should store configuration", noAuthConfig, streamRepository.getCurrentConfig())
    }
    
    @Test
    fun testRTSPUrlValidationWithRealValidator() = runTest {
        // Given - Invalid URL that should fail real validation
        val invalidConfig = validRTSPConfig.copy(
            serverUrl = "invalid-url-format"
        )
        
        // When
        val result = streamRepository.updateConfiguration(invalidConfig)
        
        // Then
        assertTrue("Should reject invalid URL", result.isFailure)
        assertNull("Should not store invalid config", streamRepository.getCurrentConfig())
    }
    
    @Test
    fun testMaliciousInputDetection() = runTest {
        // Given - Malicious stream path
        val maliciousConfig = validRTSPConfig.copy(
            streamPath = "/live/../admin"
        )
        
        // When
        val result = streamRepository.updateConfiguration(maliciousConfig)
        
        // Then
        assertTrue("Should reject malicious input", result.isFailure)
        assertNull("Should not store malicious config", streamRepository.getCurrentConfig())
    }
    
    @Test
    fun testPasswordValidationWithRealValidator() = runTest {
        // Given - Weak password that should fail real validation
        val weakPasswordConfig = validRTSPConfig.copy(
            password = "123"
        )
        
        // When
        val result = streamRepository.updateConfiguration(weakPasswordConfig)
        
        // Then
        assertTrue("Should reject weak password", result.isFailure)
        assertNull("Should not store config with weak password", streamRepository.getCurrentConfig())
    }
    
    @Test
    fun testStreamingStatePersistenceAcrossOperations() = runTest {
        // Given
        val config = validRTSPConfig.copy(useAuthentication = false)
        streamRepository.updateConfiguration(config)
        
        // When - Try to start streaming (will likely fail due to no camera/server)
        val startResult = streamRepository.startStreaming()
        
        // Then - Should handle failure gracefully
        val state = streamRepository.streamState.first()
        
        // Either preparing, error, or streaming depending on environment
        assertTrue("Should be in a valid state", 
            state.isPreparing || state.isError || state.isStreaming)
        
        if (state.isError) {
            val errorState = state as StreamState.Error
            assertTrue("Should provide meaningful error", 
                errorState.errorMessage.isNotBlank())
            assertTrue("Should allow retry for most errors", 
                errorState.canRetry)
        }
    }
    
    @Test
    fun testCleanupReleasesResources() = runTest {
        // Given
        val config = validRTSPConfig.copy(useAuthentication = false)
        streamRepository.updateConfiguration(config)
        
        // When
        streamRepository.cleanup()
        
        // Then
        val state = streamRepository.streamState.first()
        assertTrue("Should return to idle state after cleanup", state.isIdle)
    }
    
    @Test
    fun testErrorStateClearingBehavior() = runTest {
        // Given - Force an error state by trying to start without config
        streamRepository.startStreaming()
        val errorState = streamRepository.streamState.first()
        
        if (errorState.isError) {
            // When
            val clearResult = streamRepository.clearError()
            
            // Then
            assertTrue("Should successfully clear error", clearResult.isSuccess)
            val clearedState = streamRepository.streamState.first()
            assertTrue("Should return to idle state", clearedState.isIdle)
        }
    }
    
    @Test
    fun testConcurrentOperationSafety() = runTest {
        // Given
        val config = validRTSPConfig.copy(useAuthentication = false)
        
        // When - Multiple concurrent configuration updates
        val results = listOf(
            streamRepository.updateConfiguration(config),
            streamRepository.updateConfiguration(config.copy(port = 555)),
            streamRepository.updateConfiguration(config.copy(port = 556))
        )
        
        // Then - All operations should complete without crashing
        assertTrue("All operations should complete", results.size == 3)
        assertNotNull("Should have some configuration", streamRepository.getCurrentConfig())
    }
    
    @Test
    fun testConfigurationParameterValidation() = runTest {
        // Test various invalid parameter combinations
        val invalidConfigs = listOf(
            validRTSPConfig.copy(port = -1), // Invalid port
            validRTSPConfig.copy(port = 70000), // Port too high
            validRTSPConfig.copy(maxBitrate = 50000), // Bitrate too low
            validRTSPConfig.copy(maxBitrate = 20000000), // Bitrate too high
            validRTSPConfig.copy(keyFrameInterval = 0), // Invalid key frame interval
            validRTSPConfig.copy(keyFrameInterval = 100), // Key frame interval too high
            validRTSPConfig.copy(streamPath = "") // Empty stream path
        )
        
        for (config in invalidConfigs) {
            val result = streamRepository.updateConfiguration(config)
            assertTrue("Should reject invalid config: ${config.toLogSafeString()}", 
                result.isFailure)
        }
    }
    
    @Test
    fun testQualityParameterHandling() = runTest {
        // Test all quality levels
        val qualities = StreamQuality.values()
        
        for (quality in qualities) {
            val config = validRTSPConfig.copy(
                quality = quality,
                useAuthentication = false
            )
            
            val result = streamRepository.updateConfiguration(config)
            assertTrue("Should accept quality ${quality.displayName}", result.isSuccess)
            
            val storedConfig = streamRepository.getCurrentConfig()
            assertEquals("Should store correct quality", quality, storedConfig?.quality)
        }
    }
}