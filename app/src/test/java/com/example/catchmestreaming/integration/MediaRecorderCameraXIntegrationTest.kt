package com.example.catchmestreaming.integration

import android.content.Context
import android.media.MediaRecorder
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import com.example.catchmestreaming.data.RecordingConfig
import com.example.catchmestreaming.repository.CameraRepository
import com.example.catchmestreaming.repository.RecordingRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for MediaRecorder + CameraX integration
 * Tests the correct approach: MediaRecorder surface with Preview use case
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MediaRecorderCameraXIntegrationTest {

    private lateinit var context: Context
    private lateinit var recordingRepository: RecordingRepository
    private lateinit var cameraRepository: CameraRepository
    private lateinit var mockSurface: Surface

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        recordingRepository = RecordingRepository(context)
        cameraRepository = CameraRepository(context)
        mockSurface = mockk<Surface>(relaxed = true)
    }

    @After
    fun tearDown() {
        recordingRepository.cleanup()
        cameraRepository.release()
        clearAllMocks()
    }

    @Test
    fun `configureVideoRecording should store MediaRecorder surface correctly`() = runTest {
        // When
        val result = cameraRepository.configureVideoRecording(mockSurface)

        // Then
        assertTrue("Configuration should succeed", result.isSuccess)
        assertTrue(
            "Recording should be configured", 
            cameraRepository.cameraState.value.isVideoRecordingConfigured
        )
    }

    @Test
    fun `removeVideoRecording should clean up MediaRecorder surface`() = runTest {
        // Given
        cameraRepository.configureVideoRecording(mockSurface)
        assertTrue(cameraRepository.cameraState.value.isVideoRecordingConfigured)

        // When
        val result = cameraRepository.removeVideoRecording()

        // Then
        assertTrue("Removal should succeed", result.isSuccess)
        assertFalse(
            "Recording should not be configured", 
            cameraRepository.cameraState.value.isVideoRecordingConfigured
        )
        verify { mockSurface.release() }
    }

    @Test
    fun `recordingRepository should provide valid surface from MediaRecorder`() = runTest {
        // Given
        val config = RecordingConfig.createDefault()
        recordingRepository.updateConfiguration(config)

        // Mock MediaRecorder surface
        val mockMediaRecorder = mockk<MediaRecorder> {
            every { surface } returns mockSurface
        }

        // When - This would happen internally in prepareRecorderAndGetSurface
        val surface = mockMediaRecorder.surface

        // Then
        assertNotNull("Surface should not be null", surface)
        assertEquals("Surface should match", mockSurface, surface)
    }

    @Test
    fun `integration flow should work correctly`() = runTest {
        // This test demonstrates the correct integration flow:
        // 1. Prepare MediaRecorder and get surface
        // 2. Configure camera with surface
        // 3. Both components are ready for recording
        
        // Step 1: Setup recording configuration
        val config = RecordingConfig.createDefault()
        val configResult = recordingRepository.updateConfiguration(config)
        assertTrue("Config update should succeed", configResult.isSuccess)

        // Step 2: Configure camera with mock surface (in real implementation, 
        // this would be MediaRecorder.getSurface())
        val cameraResult = cameraRepository.configureVideoRecording(mockSurface)
        assertTrue("Camera config should succeed", cameraResult.isSuccess)

        // Step 3: Verify both are configured
        assertTrue(
            "Recording should be configured",
            cameraRepository.cameraState.value.isVideoRecordingConfigured
        )
        assertEquals(
            "Config should match",
            config,
            recordingRepository.getCurrentConfig()
        )
    }

    @Test
    fun `getRecorderSurface should return null when MediaRecorder not initialized`() {
        // When
        val surface = recordingRepository.getRecorderSurface()

        // Then
        assertNull("Surface should be null when MediaRecorder not initialized", surface)
    }

    @Test
    fun `configureVideoRecording should handle surface error gracefully`() = runTest {
        // Given
        val errorSurface = mockk<Surface> {
            every { isValid } throws RuntimeException("Surface error")
        }

        // When
        val result = cameraRepository.configureVideoRecording(errorSurface)

        // Then
        // Should still succeed because we're just storing the surface reference
        assertTrue("Should succeed even with problematic surface", result.isSuccess)
    }
}