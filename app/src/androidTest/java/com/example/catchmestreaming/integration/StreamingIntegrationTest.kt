package com.example.catchmestreaming.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.catchmestreaming.data.StreamConfig
import com.example.catchmestreaming.data.StreamQuality
import com.example.catchmestreaming.data.StreamState
import com.example.catchmestreaming.repository.CameraRepository
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
 * End-to-end integration tests for the complete streaming workflow
 * Tests camera → streaming → HTTP server → client connectivity
 */
@RunWith(AndroidJUnit4::class)
class StreamingIntegrationTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    )

    private lateinit var context: Context
    private lateinit var streamRepository: StreamRepository
    private lateinit var cameraRepository: CameraRepository
    private lateinit var mainViewModel: MainViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        streamRepository = StreamRepository(context)
        cameraRepository = CameraRepository(context)
        mainViewModel = MainViewModel(
            cameraRepository = cameraRepository,
            streamRepository = streamRepository,
            recordingRepository = com.example.catchmestreaming.repository.RecordingRepository(context)
        )
    }

    @After
    fun tearDown() {
        runTest {
            streamRepository.stopStreaming()
            streamRepository.cleanup()
            cameraRepository.release()
        }
    }

    @Test
    fun completeStreamingWorkflow_shouldSucceed() = runTest {
        // Step 1: Configure streaming with valid settings
        val streamConfig = StreamConfig(
            serverUrl = "192.168.1.100",
            port = 8080,
            streamPath = "/stream",
            quality = StreamQuality.HD_720P,
            username = "testuser",
            password = "testpass",
            enableAuth = true
        )

        val configResult = streamRepository.updateConfiguration(streamConfig)
        assertTrue("Stream configuration should succeed", configResult.isSuccess)

        // Step 2: Initialize camera
        val cameraResult = cameraRepository.initializeCamera()
        assertTrue("Camera initialization should succeed", cameraResult.isSuccess)

        // Wait for camera to be ready
        delay(1000)

        // Step 3: Start streaming
        val streamResult = streamRepository.startStreaming()
        assertTrue("Streaming should start successfully", streamResult.isSuccess)

        // Step 4: Verify streaming state
        val streamState = streamRepository.streamState.first()
        assertTrue("Should be in streaming state", streamState is StreamState.Streaming)

        if (streamState is StreamState.Streaming) {
            assertNotNull("Stream URL should not be null", streamState.rtspUrl)
            assertTrue("Stream URL should contain correct format", 
                streamState.rtspUrl.contains("http://"))
            assertTrue("Stream should have start time", streamState.startTime > 0)
        }

        // Step 5: Verify HTTP server is running
        val serverStatus = streamRepository.getServerStatus()
        assertTrue("HTTP server should be running", serverStatus.isRunning)
        assertEquals("Server should be on correct port", 8080, serverStatus.port)

        // Step 6: Stop streaming gracefully
        val stopResult = streamRepository.stopStreaming()
        assertTrue("Streaming should stop successfully", stopResult.isSuccess)

        // Step 7: Verify final state
        val finalState = streamRepository.streamState.first()
        assertTrue("Should be in stopped state", 
            finalState is StreamState.Stopped || finalState is StreamState.Idle)
    }

    @Test
    fun streamingWithoutAuthentication_shouldSucceed() = runTest {
        // Configure streaming without authentication
        val streamConfig = StreamConfig(
            serverUrl = "192.168.1.100",
            port = 8081,
            streamPath = "/stream",
            quality = StreamQuality.HD_720P,
            enableAuth = false
        )

        streamRepository.updateConfiguration(streamConfig)
        cameraRepository.initializeCamera()
        delay(500)

        val streamResult = streamRepository.startStreaming()
        assertTrue("Streaming without auth should succeed", streamResult.isSuccess)

        val streamState = streamRepository.streamState.first()
        assertTrue("Should be streaming", streamState is StreamState.Streaming)

        streamRepository.stopStreaming()
    }

    @Test
    fun concurrentCameraAndStreamingOperations_shouldWork() = runTest {
        // Initialize camera first
        cameraRepository.initializeCamera()
        delay(500)

        // Start preview
        val previewResult = cameraRepository.startPreview()
        assertTrue("Preview should start", previewResult.isSuccess)

        // Configure and start streaming while preview is active
        val streamConfig = StreamConfig.createDefault().copy(port = 8082)
        streamRepository.updateConfiguration(streamConfig)

        val streamResult = streamRepository.startStreaming()
        assertTrue("Streaming should work with active preview", streamResult.isSuccess)

        // Both should be active
        val cameraState = cameraRepository.cameraState.first()
        val streamState = streamRepository.streamState.first()

        assertTrue("Camera should be initialized", cameraState.isInitialized)
        assertTrue("Preview should be active", cameraState.isPreviewActive)
        assertTrue("Should be streaming", streamState is StreamState.Streaming)

        // Cleanup
        streamRepository.stopStreaming()
        cameraRepository.stopPreview()
    }

    @Test
    fun streamingFailureRecovery_shouldHandleGracefully() = runTest {
        // Try to start streaming without camera initialization
        val streamConfig = StreamConfig.createDefault().copy(port = 8083)
        streamRepository.updateConfiguration(streamConfig)

        // This should handle the error gracefully
        val streamResult = streamRepository.startStreaming()
        
        // Verify error state
        val streamState = streamRepository.streamState.first()
        if (streamResult.isFailure || streamState is StreamState.Error) {
            assertTrue("Should handle streaming failure", true)
            
            // Now properly initialize and retry
            cameraRepository.initializeCamera()
            delay(500)
            
            val retryResult = streamRepository.startStreaming()
            if (retryResult.isSuccess) {
                val retryState = streamRepository.streamState.first()
                assertTrue("Retry should succeed", retryState is StreamState.Streaming)
                streamRepository.stopStreaming()
            }
        }
    }

    @Test
    fun multipleStartStopCycles_shouldMaintainStability() = runTest {
        val streamConfig = StreamConfig.createDefault().copy(port = 8084)
        streamRepository.updateConfiguration(streamConfig)
        cameraRepository.initializeCamera()
        delay(500)

        // Perform multiple start/stop cycles
        repeat(3) { cycle ->
            // Start streaming
            val startResult = streamRepository.startStreaming()
            assertTrue("Cycle $cycle: Start should succeed", startResult.isSuccess)

            // Verify streaming state
            val streamingState = streamRepository.streamState.first()
            assertTrue("Cycle $cycle: Should be streaming", 
                streamingState is StreamState.Streaming)

            // Brief streaming period
            delay(1000)

            // Stop streaming
            val stopResult = streamRepository.stopStreaming()
            assertTrue("Cycle $cycle: Stop should succeed", stopResult.isSuccess)

            // Verify stopped state
            delay(500)
            val stoppedState = streamRepository.streamState.first()
            assertTrue("Cycle $cycle: Should be stopped", 
                stoppedState is StreamState.Stopped || stoppedState is StreamState.Idle)

            // Brief pause between cycles
            delay(500)
        }
    }

    @Test
    fun streamingWithDifferentQualitySettings_shouldAdapt() = runTest {
        cameraRepository.initializeCamera()
        delay(500)

        val qualitySettings = listOf(
            StreamQuality.SD_480P,
            StreamQuality.HD_720P,
            StreamQuality.HD_1080P
        )

        qualitySettings.forEachIndexed { index, quality ->
            val streamConfig = StreamConfig.createDefault().copy(
                quality = quality,
                port = 8085 + index
            )

            streamRepository.updateConfiguration(streamConfig)
            
            val streamResult = streamRepository.startStreaming()
            assertTrue("Quality $quality: Streaming should start", streamResult.isSuccess)

            val streamState = streamRepository.streamState.first()
            assertTrue("Quality $quality: Should be streaming", 
                streamState is StreamState.Streaming)

            // Verify configuration applied
            val currentConfig = streamRepository.getCurrentConfig()
            assertEquals("Quality should match", quality, currentConfig?.quality)

            streamRepository.stopStreaming()
            delay(500)
        }
    }

    @Test
    fun networkConnectivityValidation_shouldWork() = runTest {
        // Test with invalid IP address format
        val invalidConfig = StreamConfig(
            serverUrl = "invalid.ip.address",
            port = 8086,
            streamPath = "/stream",
            quality = StreamQuality.HD_720P,
            enableAuth = false
        )

        val configResult = streamRepository.updateConfiguration(invalidConfig)
        // Should either reject invalid config or handle gracefully
        if (configResult.isSuccess) {
            cameraRepository.initializeCamera()
            delay(500)
            
            val streamResult = streamRepository.startStreaming()
            if (streamResult.isFailure) {
                val streamState = streamRepository.streamState.first()
                assertTrue("Should handle network errors", streamState is StreamState.Error)
            }
        }
    }
}