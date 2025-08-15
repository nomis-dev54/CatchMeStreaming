package com.example.catchmestreaming.performance

import android.content.Context
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.catchmestreaming.data.RecordingConfig
import com.example.catchmestreaming.data.StreamConfig
import com.example.catchmestreaming.data.StreamQuality
import com.example.catchmestreaming.data.VideoQuality
import com.example.catchmestreaming.repository.CameraRepository
import com.example.catchmestreaming.repository.RecordingRepository
import com.example.catchmestreaming.repository.StreamRepository
import com.example.catchmestreaming.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Performance tests for streaming, recording, and system optimization
 * Tests startup time, memory usage, battery efficiency, and performance under load
 */
@RunWith(AndroidJUnit4::class)
class PerformanceTest {

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
    fun appStartupTime_shouldMeetTargetLatency() = runTest {
        // Target: <3 seconds for app startup on minimum spec device
        val startupTime = measureTimeMillis {
            // Simulate app startup sequence
            cameraRepository.initializeCamera()
            delay(100) // Allow initialization to complete
            
            // Initialize repositories
            val streamConfig = StreamConfig.createDefault()
            streamRepository.updateConfiguration(streamConfig)
            
            val recordingConfig = RecordingConfig.createDefault(VideoQuality.HD_720P)
            recordingRepository.updateConfiguration(recordingConfig)
        }

        assertTrue("App startup should complete within 3 seconds (actual: ${startupTime}ms)",
            startupTime < 3000)
    }

    @Test
    fun streamingStartupTime_shouldMeetTargetLatency() = runTest {
        // Target: <5 seconds for streaming startup on local network
        cameraRepository.initializeCamera()
        delay(500)

        val streamConfig = StreamConfig.createDefault().copy(port = 8500)
        streamRepository.updateConfiguration(streamConfig)

        val streamingStartupTime = measureTimeMillis {
            val result = streamRepository.startStreaming()
            assertTrue("Streaming should start successfully", result.isSuccess)
        }

        assertTrue("Streaming startup should complete within 5 seconds (actual: ${streamingStartupTime}ms)",
            streamingStartupTime < 5000)

        streamRepository.stopStreaming()
    }

    @Test
    fun memoryUsageStability_shouldMaintainReasonableLimits() = runTest {
        // Get initial memory usage
        System.gc() // Force garbage collection
        val initialMemory = Debug.getNativeHeapAllocatedSize()

        // Initialize all components
        cameraRepository.initializeCamera()
        delay(500)

        val streamConfig = StreamConfig.createDefault().copy(port = 8501)
        streamRepository.updateConfiguration(streamConfig)
        
        val recordingConfig = RecordingConfig.createDefault(VideoQuality.HD_720P)
        recordingRepository.updateConfiguration(recordingConfig)

        // Start operations
        val streamResult = streamRepository.startStreaming()
        if (streamResult.isSuccess) {
            delay(2000) // Run for 2 seconds
            streamRepository.stopStreaming()
        }

        val recordResult = recordingRepository.startRecording(VideoQuality.HD_720P)
        if (recordResult.isSuccess) {
            delay(2000) // Record for 2 seconds
            recordingRepository.stopRecording()
        }

        // Stop all operations and cleanup
        streamRepository.cleanup()
        recordingRepository.cleanup()
        cameraRepository.release()

        System.gc() // Force garbage collection
        delay(1000) // Allow cleanup to complete
        
        val finalMemory = Debug.getNativeHeapAllocatedSize()
        val memoryIncrease = finalMemory - initialMemory

        // Memory increase should be reasonable (less than 100MB)
        assertTrue("Memory increase should be reasonable (${memoryIncrease / 1024 / 1024}MB)",
            memoryIncrease < 100 * 1024 * 1024)
    }

    @Test
    fun memoryLeakDetection_shouldNotLeakMemory() = runTest {
        val memoryMeasurements = mutableListOf<Long>()

        // Perform multiple start/stop cycles and measure memory
        repeat(5) { cycle ->
            System.gc()
            val beforeMemory = Debug.getNativeHeapAllocatedSize()

            // Initialize components
            cameraRepository.initializeCamera()
            
            val streamConfig = StreamConfig.createDefault().copy(port = 8502 + cycle)
            streamRepository.updateConfiguration(streamConfig)
            
            // Start and stop streaming
            val streamResult = streamRepository.startStreaming()
            if (streamResult.isSuccess) {
                delay(1000)
                streamRepository.stopStreaming()
            }

            // Start and stop recording
            val recordingConfig = RecordingConfig.createDefault(VideoQuality.HD_720P)
            recordingRepository.updateConfiguration(recordingConfig)
            val recordResult = recordingRepository.startRecording(VideoQuality.HD_720P)
            if (recordResult.isSuccess) {
                delay(1000)
                recordingRepository.stopRecording()
            }

            // Cleanup
            streamRepository.cleanup()
            recordingRepository.cleanup()
            cameraRepository.release()

            System.gc()
            delay(500)
            
            val afterMemory = Debug.getNativeHeapAllocatedSize()
            memoryMeasurements.add(afterMemory - beforeMemory)
        }

        // Memory usage should not continuously increase (no major leaks)
        val averageIncrease = memoryMeasurements.average()
        val maxIncrease = memoryMeasurements.maxOrNull() ?: 0L

        assertTrue("Average memory increase per cycle should be reasonable (${averageIncrease / 1024 / 1024}MB)",
            averageIncrease < 50 * 1024 * 1024) // 50MB average

        assertTrue("Max memory increase should be bounded (${maxIncrease / 1024 / 1024}MB)",
            maxIncrease < 100 * 1024 * 1024) // 100MB max
    }

    @Test
    fun concurrentOperationsPerformance_shouldHandleEfficiently() = runTest {
        cameraRepository.initializeCamera()
        delay(500)

        val concurrentOperationTime = measureTimeMillis {
            // Start streaming
            val streamConfig = StreamConfig.createDefault().copy(port = 8510)
            streamRepository.updateConfiguration(streamConfig)
            val streamResult = streamRepository.startStreaming()

            // Start recording while streaming (if supported)
            val recordingConfig = RecordingConfig.createDefault(VideoQuality.HD_720P)
            recordingRepository.updateConfiguration(recordingConfig)
            val recordResult = recordingRepository.startRecording(VideoQuality.HD_720P)

            // Let both run concurrently
            delay(3000)

            // Stop both operations
            if (streamResult.isSuccess) {
                streamRepository.stopStreaming()
            }
            if (recordResult.isSuccess) {
                recordingRepository.stopRecording()
            }
        }

        // Concurrent operations should complete within reasonable time
        assertTrue("Concurrent operations should complete efficiently (${concurrentOperationTime}ms)",
            concurrentOperationTime < 10000) // 10 seconds total
    }

    @Test
    fun streamingLatency_shouldMeetRealTimeRequirements() = runTest {
        cameraRepository.initializeCamera()
        delay(500)

        // Test different quality settings for latency
        val qualities = listOf(
            StreamQuality.SD_480P,
            StreamQuality.HD_720P,
            StreamQuality.HD_1080P
        )

        qualities.forEachIndexed { index, quality ->
            val streamConfig = StreamConfig.createDefault().copy(
                quality = quality,
                port = 8520 + index
            )
            streamRepository.updateConfiguration(streamConfig)

            val latency = measureTimeMillis {
                val result = streamRepository.startStreaming()
                assertTrue("Streaming should start for quality $quality", result.isSuccess)
            }

            // Higher quality should still meet reasonable latency requirements
            val maxLatencyForQuality = when (quality) {
                StreamQuality.SD_480P -> 3000L
                StreamQuality.HD_720P -> 4000L
                StreamQuality.HD_1080P -> 5000L
            }

            assertTrue("$quality streaming latency should be acceptable (${latency}ms)",
                latency < maxLatencyForQuality)

            streamRepository.stopStreaming()
            delay(500)
        }
    }

    @Test
    fun recordingPerformance_shouldHandleLongSessions() = runTest {
        cameraRepository.initializeCamera()
        delay(500)

        val recordingConfig = RecordingConfig(
            quality = VideoQuality.HD_720P,
            maxDurationMs = 30000, // 30 seconds
            includeAudio = true
        )
        recordingRepository.updateConfiguration(recordingConfig)

        val recordingTime = measureTimeMillis {
            val startResult = recordingRepository.startRecording(VideoQuality.HD_720P)
            assertTrue("Recording should start", startResult.isSuccess)

            // Let it record for specified duration
            delay(5000) // Record for 5 seconds (shorter for test)

            val stopResult = recordingRepository.stopRecording()
            assertTrue("Recording should stop", stopResult.isSuccess)
        }

        // Recording operations should be efficient
        assertTrue("Recording session should complete efficiently (${recordingTime}ms)",
            recordingTime < 10000) // Should complete within 10 seconds
    }

    @Test
    fun qualityAdaptationPerformance_shouldSwitchEfficiently() = runTest {
        cameraRepository.initializeCamera()
        delay(500)

        val qualityLevels = listOf(
            VideoQuality.HD_720P,
            VideoQuality.HD_1080P,
            VideoQuality.HD_720P,
            VideoQuality.HD_1080P
        )

        val totalAdaptationTime = measureTimeMillis {
            qualityLevels.forEach { quality ->
                val config = RecordingConfig.createDefault(quality)
                val configResult = recordingRepository.updateConfiguration(config)
                assertTrue("Quality configuration should succeed", configResult.isSuccess)

                val recordResult = recordingRepository.startRecording(quality)
                if (recordResult.isSuccess) {
                    delay(1000) // Brief recording
                    recordingRepository.stopRecording()
                    delay(200) // Brief pause
                }
            }
        }

        // Quality adaptation should be fast
        assertTrue("Quality adaptation should be efficient (${totalAdaptationTime}ms)",
            totalAdaptationTime < 15000) // 15 seconds for 4 adaptations
    }

    @Test
    fun networkBandwidthOptimization_shouldAdaptToConnection() = runTest {
        cameraRepository.initializeCamera()
        delay(500)

        // Test streaming with different quality settings (simulating bandwidth adaptation)
        val networkConfigurations = listOf(
            StreamConfig.createDefault().copy(
                quality = StreamQuality.SD_480P,
                port = 8530
            ),
            StreamConfig.createDefault().copy(
                quality = StreamQuality.HD_720P,
                port = 8531
            ),
            StreamConfig.createDefault().copy(
                quality = StreamQuality.HD_1080P,
                port = 8532
            )
        )

        networkConfigurations.forEach { config ->
            val adaptationTime = measureTimeMillis {
                val configResult = streamRepository.updateConfiguration(config)
                assertTrue("Network configuration should succeed", configResult.isSuccess)

                val streamResult = streamRepository.startStreaming()
                if (streamResult.isSuccess) {
                    delay(2000) // Stream briefly
                    streamRepository.stopStreaming()
                }
            }

            // Each quality adaptation should be fast
            assertTrue("Network adaptation for ${config.quality} should be fast (${adaptationTime}ms)",
                adaptationTime < 5000)

            delay(500) // Brief pause between adaptations
        }
    }

    @Test
    fun resourceCleanupPerformance_shouldReleaseQuickly() = runTest {
        // Initialize all components
        cameraRepository.initializeCamera()
        delay(500)

        val streamConfig = StreamConfig.createDefault().copy(port = 8540)
        streamRepository.updateConfiguration(streamConfig)
        streamRepository.startStreaming()

        val recordingConfig = RecordingConfig.createDefault(VideoQuality.HD_720P)
        recordingRepository.updateConfiguration(recordingConfig)
        recordingRepository.startRecording(VideoQuality.HD_720P)

        delay(2000) // Let operations run

        // Measure cleanup time
        val cleanupTime = measureTimeMillis {
            streamRepository.stopStreaming()
            recordingRepository.stopRecording()
            streamRepository.cleanup()
            recordingRepository.cleanup()
            cameraRepository.release()
        }

        // Cleanup should be fast
        assertTrue("Resource cleanup should be fast (${cleanupTime}ms)",
            cleanupTime < 3000) // 3 seconds
    }

    @Test
    fun stressTestPerformance_shouldHandleHighLoad() = runTest {
        // Stress test with rapid operations
        val stressTestTime = measureTimeMillis {
            repeat(20) { cycle ->
                // Rapid initialization/cleanup cycles
                cameraRepository.initializeCamera()
                
                val streamConfig = StreamConfig.createDefault().copy(port = 8550 + cycle)
                streamRepository.updateConfiguration(streamConfig)
                
                val streamResult = streamRepository.startStreaming()
                if (streamResult.isSuccess) {
                    delay(100) // Very brief streaming
                    streamRepository.stopStreaming()
                }

                streamRepository.cleanup()
                cameraRepository.release()
                
                delay(50) // Brief pause
            }
        }

        // Stress test should complete within reasonable time
        assertTrue("Stress test should complete efficiently (${stressTestTime}ms)",
            stressTestTime < 30000) // 30 seconds for 20 cycles
    }

    @Test
    fun uiResponsiveness_shouldMaintainFrameRate() = runTest {
        // Simulate UI operations during background processing
        cameraRepository.initializeCamera()
        delay(500)

        val streamConfig = StreamConfig.createDefault().copy(port = 8560)
        streamRepository.updateConfiguration(streamConfig)
        
        // Start background operations
        val streamResult = streamRepository.startStreaming()
        
        if (streamResult.isSuccess) {
            // Measure time for configuration updates (simulating UI interactions)
            val uiResponseTime = measureTimeMillis {
                repeat(10) {
                    // Simulate UI configuration changes
                    val newConfig = streamConfig.copy(
                        quality = if (it % 2 == 0) StreamQuality.HD_720P else StreamQuality.HD_1080P
                    )
                    streamRepository.updateConfiguration(newConfig)
                    delay(50) // Simulate UI update delay
                }
            }

            // UI should remain responsive during streaming
            assertTrue("UI should remain responsive during streaming (${uiResponseTime}ms)",
                uiResponseTime < 3000) // 3 seconds for 10 operations

            streamRepository.stopStreaming()
        }
    }
}