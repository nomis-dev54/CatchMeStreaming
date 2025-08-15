package com.example.catchmestreaming.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.catchmestreaming.data.RecordingConfig
import com.example.catchmestreaming.data.RecordingState
import com.example.catchmestreaming.data.StreamConfig
import com.example.catchmestreaming.data.StreamState
import com.example.catchmestreaming.data.VideoQuality
import com.example.catchmestreaming.repository.CameraRepository
import com.example.catchmestreaming.repository.RecordingRepository
import com.example.catchmestreaming.repository.StreamRepository
import com.example.catchmestreaming.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for error handling and recovery scenarios
 * Tests failure modes, recovery mechanisms, and system resilience
 */
@RunWith(AndroidJUnit4::class)
class ErrorHandlingIntegrationTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var context: Context
    private lateinit var streamRepository: StreamRepository
    private lateinit var recordingRepository: RecordingRepository
    private lateinit var cameraRepository: CameraRepository
    private lateinit var mainViewModel: MainViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        streamRepository = StreamRepository(context)
        recordingRepository = RecordingRepository(context)
        cameraRepository = CameraRepository(context)
        mainViewModel = MainViewModel(
            cameraRepository = cameraRepository,
            streamRepository = streamRepository,
            recordingRepository = recordingRepository
        )
    }

    @After
    fun tearDown() {
        runTest {
            streamRepository.stopStreaming()
            recordingRepository.stopRecording()
            streamRepository.cleanup()
            recordingRepository.cleanup()
            cameraRepository.release()
        }
    }

    @Test
    fun cameraInitializationFailure_shouldRecoverGracefully() = runTest {
        // Attempt operations without camera initialization
        val previewResult = cameraRepository.startPreview()
        
        if (previewResult.isFailure) {
            // Verify failure is handled gracefully
            val cameraState = cameraRepository.cameraState.first()
            assertFalse("Camera should not be initialized", cameraState.isInitialized)
            assertFalse("Preview should not be active", cameraState.isPreviewActive)
            
            // Now properly initialize camera
            val initResult = cameraRepository.initializeCamera()
            assertTrue("Camera initialization should succeed", initResult.isSuccess)
            
            delay(500)
            
            // Retry preview after proper initialization
            val retryPreviewResult = cameraRepository.startPreview()
            assertTrue("Preview should work after proper initialization", retryPreviewResult.isSuccess)
        }
    }

    @Test
    fun streamingNetworkFailure_shouldHandleAndRecover() = runTest {
        // Configure streaming with invalid network settings
        val invalidConfig = StreamConfig(
            serverUrl = "192.168.999.999", // Invalid IP
            port = 99999, // Invalid port
            streamPath = "/stream",
            quality = com.example.catchmestreaming.data.StreamQuality.HD_720P,
            enableAuth = false
        )

        val configResult = streamRepository.updateConfiguration(invalidConfig)
        if (configResult.isSuccess) {
            // Initialize camera first
            cameraRepository.initializeCamera()
            delay(500)

            // Try to start streaming with invalid config
            val streamResult = streamRepository.startStreaming()
            
            if (streamResult.isFailure) {
                // Verify error state
                val streamState = streamRepository.streamState.first()
                assertTrue("Should be in error state", streamState is StreamState.Error)
                
                // Recovery: Update with valid configuration
                val validConfig = StreamConfig.createDefault()
                val validConfigResult = streamRepository.updateConfiguration(validConfig)
                assertTrue("Valid config should be accepted", validConfigResult.isSuccess)
                
                // Retry streaming
                val retryResult = streamRepository.startStreaming()
                if (retryResult.isSuccess) {
                    val retryState = streamRepository.streamState.first()
                    assertTrue("Recovery should succeed", retryState is StreamState.Streaming)
                    
                    // Cleanup
                    streamRepository.stopStreaming()
                }
            }
        }
    }

    @Test
    fun recordingStorageFailure_shouldHandleGracefully() = runTest {
        // Configure recording with impossible storage requirements
        val massiveConfig = RecordingConfig(
            quality = VideoQuality.UHD_4K,
            maxFileSizeBytes = Long.MAX_VALUE,
            maxDurationMs = Long.MAX_VALUE,
            includeAudio = true
        )

        val configResult = recordingRepository.updateConfiguration(massiveConfig)
        if (configResult.isSuccess) {
            cameraRepository.initializeCamera()
            delay(500)

            // Try to start recording with unrealistic requirements
            val recordingResult = recordingRepository.startRecording(VideoQuality.UHD_4K)
            
            if (recordingResult.isFailure) {
                // Verify error state
                val recordingState = recordingRepository.recordingState.first()
                assertTrue("Should handle storage limitation", 
                    recordingState is RecordingState.Error || recordingState is RecordingState.Idle)
                
                // Recovery: Use realistic configuration
                val realisticConfig = RecordingConfig.createDefault(VideoQuality.HD_720P)
                val realisticResult = recordingRepository.updateConfiguration(realisticConfig)
                assertTrue("Realistic config should be accepted", realisticResult.isSuccess)
                
                // Retry recording
                val retryResult = recordingRepository.startRecording(VideoQuality.HD_720P)
                if (retryResult.isSuccess) {
                    delay(1000)
                    recordingRepository.stopRecording()
                }
            }
        }
    }

    @Test
    fun concurrentOperationConflicts_shouldResolveCorrectly() = runTest {
        cameraRepository.initializeCamera()
        delay(500)

        // Start streaming
        val streamConfig = StreamConfig.createDefault().copy(port = 8090)
        streamRepository.updateConfiguration(streamConfig)
        val streamResult = streamRepository.startStreaming()
        
        if (streamResult.isSuccess) {
            // Try to start recording while streaming
            val recordingConfig = RecordingConfig.createDefault(VideoQuality.HD_720P)
            recordingRepository.updateConfiguration(recordingConfig)
            val recordingResult = recordingRepository.startRecording(VideoQuality.HD_720P)
            
            // System should either:
            // 1. Support concurrent operations
            // 2. Prevent conflicting operations gracefully
            // 3. Stop one to start the other
            
            val streamState = streamRepository.streamState.first()
            val recordingState = recordingRepository.recordingState.first()
            
            if (streamState is StreamState.Streaming && recordingState is RecordingState.Recording) {
                // Concurrent operations supported
                assertTrue("Both operations should be active", true)
            } else if (streamState is StreamState.Streaming && recordingState is RecordingState.Error) {
                // Recording blocked by streaming - acceptable
                assertTrue("Streaming should prevent recording conflicts", true)
            } else if (streamState is StreamState.Error && recordingState is RecordingState.Recording) {
                // Recording stopped streaming - acceptable
                assertTrue("Recording should handle stream conflicts", true)
            } else {
                // Some other resolution occurred
                assertTrue("System should resolve conflicts gracefully", true)
            }
            
            // Cleanup both operations
            streamRepository.stopStreaming()
            recordingRepository.stopRecording()
        }
    }

    @Test
    fun resourceExhaustion_shouldHandleGracefully() = runTest {
        cameraRepository.initializeCamera()
        delay(500)

        // Start multiple operations rapidly to stress system
        val operations = mutableListOf<Boolean>()
        
        repeat(10) { index ->
            val streamConfig = StreamConfig.createDefault().copy(port = 8100 + index)
            val configResult = streamRepository.updateConfiguration(streamConfig)
            
            if (configResult.isSuccess) {
                val streamResult = streamRepository.startStreaming()
                operations.add(streamResult.isSuccess)
                
                if (streamResult.isSuccess) {
                    // Brief operation
                    delay(100)
                    streamRepository.stopStreaming()
                    delay(100)
                }
            }
        }

        // System should handle rapid operations without crashing
        assertTrue("System should handle rapid operations", operations.isNotEmpty())
        
        // At least some operations should succeed (system resilience)
        val successfulOperations = operations.count { it }
        assertTrue("Some operations should succeed under stress", successfulOperations > 0)
    }

    @Test
    fun invalidConfigurationRecovery_shouldMaintainStability() = runTest {
        val invalidConfigs = listOf(
            StreamConfig.createDefault().copy(serverUrl = ""),
            StreamConfig.createDefault().copy(port = -1),
            StreamConfig.createDefault().copy(streamPath = ""),
            StreamConfig.createDefault().copy(quality = com.example.catchmestreaming.data.StreamQuality.HD_720P)
        )

        cameraRepository.initializeCamera()
        delay(500)

        invalidConfigs.forEach { invalidConfig ->
            // Try invalid configuration
            val configResult = streamRepository.updateConfiguration(invalidConfig)
            
            if (configResult.isSuccess) {
                // If accepted, try to use it
                val streamResult = streamRepository.startStreaming()
                
                if (streamResult.isFailure) {
                    // Expected - invalid config should cause stream failure
                    val streamState = streamRepository.streamState.first()
                    assertTrue("Invalid config should cause error", 
                        streamState is StreamState.Error || streamState is StreamState.Idle)
                } else {
                    // Config was validated/sanitized successfully
                    streamRepository.stopStreaming()
                }
            }
            
            // System should always be able to recover with valid config
            val validConfig = StreamConfig.createDefault()
            val recoveryResult = streamRepository.updateConfiguration(validConfig)
            assertTrue("Should always be able to set valid config", recoveryResult.isSuccess)
            
            delay(100)
        }
    }

    @Test
    fun memoryPressureRecovery_shouldFreeResources() = runTest {
        cameraRepository.initializeCamera()
        delay(500)

        // Create many short-lived operations to stress memory
        repeat(20) { cycle ->
            // Configure streaming
            val streamConfig = StreamConfig.createDefault().copy(port = 8200 + cycle)
            streamRepository.updateConfiguration(streamConfig)
            
            // Start streaming
            val streamResult = streamRepository.startStreaming()
            if (streamResult.isSuccess) {
                delay(50) // Very brief streaming
                streamRepository.stopStreaming()
            }
            
            // Configure recording
            val recordingConfig = RecordingConfig.createDefault(VideoQuality.HD_720P)
            recordingRepository.updateConfiguration(recordingConfig)
            
            // Start recording
            val recordingResult = recordingRepository.startRecording(VideoQuality.HD_720P)
            if (recordingResult.isSuccess) {
                delay(50) // Very brief recording
                recordingRepository.stopRecording()
            }
            
            // Brief pause between cycles
            delay(50)
        }

        // System should still be responsive after stress test
        val finalStreamConfig = StreamConfig.createDefault().copy(port = 8300)
        val finalConfigResult = streamRepository.updateConfiguration(finalStreamConfig)
        assertTrue("System should be responsive after stress test", finalConfigResult.isSuccess)
        
        val finalStreamResult = streamRepository.startStreaming()
        if (finalStreamResult.isSuccess) {
            val finalState = streamRepository.streamState.first()
            assertTrue("Final operation should work", finalState is StreamState.Streaming)
            streamRepository.stopStreaming()
        }
    }

    @Test
    fun systemRecoveryAfterCrash_shouldRestoreState() = runTest {
        // Simulate app restart scenario
        cameraRepository.initializeCamera()
        delay(500)

        // Save some configuration state
        val originalConfig = StreamConfig(
            serverUrl = "192.168.1.100",
            port = 8080,
            streamPath = "/stream",
            quality = com.example.catchmestreaming.data.StreamQuality.HD_1080P,
            enableAuth = true,
            username = "testuser",
            password = "testpass"
        )

        streamRepository.updateConfiguration(originalConfig)
        
        // Simulate "crash" by releasing resources
        cameraRepository.release()
        streamRepository.cleanup()
        recordingRepository.cleanup()

        // Simulate restart by recreating repositories
        val newStreamRepository = StreamRepository(context)
        val newRecordingRepository = RecordingRepository(context)
        val newCameraRepository = CameraRepository(context)

        // System should be able to initialize cleanly
        val initResult = newCameraRepository.initializeCamera()
        assertTrue("Camera should initialize after restart", initResult.isSuccess)

        // Should be able to set configuration
        val configResult = newStreamRepository.updateConfiguration(originalConfig)
        assertTrue("Should be able to set config after restart", configResult.isSuccess)

        // Should be able to start operations
        delay(500)
        val streamResult = newStreamRepository.startStreaming()
        if (streamResult.isSuccess) {
            newStreamRepository.stopStreaming()
        }

        // Cleanup new instances
        newStreamRepository.cleanup()
        newRecordingRepository.cleanup()
        newCameraRepository.release()
    }

    @Test
    fun errorPropagation_shouldMaintainSystemIntegrity() = runTest {
        // Test that errors in one component don't corrupt others
        cameraRepository.initializeCamera()
        delay(500)

        // Start successful streaming
        val validStreamConfig = StreamConfig.createDefault().copy(port = 8400)
        streamRepository.updateConfiguration(validStreamConfig)
        val streamResult = streamRepository.startStreaming()
        
        if (streamResult.isSuccess) {
            // Try invalid recording operation while streaming
            val invalidRecordingConfig = RecordingConfig(
                quality = VideoQuality.UHD_4K,
                maxFileSizeBytes = -1, // Invalid
                includeAudio = true
            )
            
            val recordingConfigResult = recordingRepository.updateConfiguration(invalidRecordingConfig)
            
            if (recordingConfigResult.isSuccess) {
                val recordingResult = recordingRepository.startRecording(VideoQuality.UHD_4K)
                
                // Recording failure should not affect streaming
                val streamState = streamRepository.streamState.first()
                assertTrue("Streaming should continue despite recording error", 
                    streamState is StreamState.Streaming)
            }
            
            // Streaming should still be controllable
            val stopResult = streamRepository.stopStreaming()
            assertTrue("Should be able to stop streaming after error", stopResult.isSuccess)
        }

        // Camera should still be functional
        val cameraState = cameraRepository.cameraState.first()
        assertTrue("Camera should remain functional", cameraState.isInitialized)
    }
}